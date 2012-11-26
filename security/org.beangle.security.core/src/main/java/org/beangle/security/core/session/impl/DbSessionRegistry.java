/*
 * Beangle, Agile Java/Scala Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2012, Beangle Software.
 *
 * Beangle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Beangle is distributed in the hope that it will be useful.
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Beangle.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.security.core.session.impl;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.beangle.commons.bean.Initializing;
import org.beangle.commons.collection.CollectUtils;
import org.beangle.commons.dao.EntityDao;
import org.beangle.commons.dao.impl.BaseServiceImpl;
import org.beangle.commons.dao.query.builder.OqlBuilder;
import org.beangle.commons.lang.Assert;
import org.beangle.commons.lang.Dates;
import org.beangle.commons.lang.Objects;
import org.beangle.commons.lang.time.Stopwatch;
import org.beangle.security.core.Authentication;
import org.beangle.security.core.session.SessionController;
import org.beangle.security.core.session.SessionException;
import org.beangle.security.core.session.SessionRegistry;
import org.beangle.security.core.session.SessionStatus;
import org.beangle.security.core.session.Sessioninfo;
import org.beangle.security.core.session.SessioninfoBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author chaostone
 * @since 2.4
 */
public class DbSessionRegistry extends BaseServiceImpl implements SessionRegistry, Initializing {

  protected static final Logger logger = LoggerFactory.getLogger(DbSessionRegistry.class);

  private SessionController controller;

  private SessioninfoBuilder sessioninfoBuilder = new SimpleSessioninfoBuilder();

  protected final Map<String, AccessEntry> entries = CollectUtils.newConcurrentHashMap();

  long updatedAt = System.currentTimeMillis();

  /**
   * Default interval(5 minutes) for update access log to db and process expired session infos.
   */
  private int updateInterval = 5 * 60 * 1000;

  public void init() throws Exception {
    Assert.notNull(controller, "controller must set");
    Assert.notNull(sessioninfoBuilder, "sessioninfoBuilder must set");
    SessionCleanerTask sessionCleanerTask = new SessionCleanerTask(this);
    sessionCleanerTask.setEntityDao(entityDao);
    // 下一次间隔开始清理，不要浪费启动时间
    new Timer("beangle-session-cleaner", true).schedule(sessionCleanerTask,
        new Date(System.currentTimeMillis() + updateInterval), updateInterval);
  }

  @SuppressWarnings({ "rawtypes", "unchecked" })
  public boolean isRegisted(String principal) {
    OqlBuilder builder = OqlBuilder.from(sessioninfoBuilder.getSessioninfoType(), "info");
    builder.where("info.username=:username and info.expiredAt is null", principal).select("info.id")
        .cacheable();
    return !entityDao.search(builder).isEmpty();
  }

  public List<Sessioninfo> getSessioninfos(String principal, boolean includeExpiredSessions) {
    OqlBuilder<Sessioninfo> builder = OqlBuilder.from(sessioninfoBuilder.getSessioninfoType().getName(),
        "info");
    builder.where("info.username=:username", principal);
    if (!includeExpiredSessions) builder.where("info.expiredAt is null");
    return entityDao.search(builder);
  }

  public Sessioninfo getSessioninfo(String sessionId) {
    OqlBuilder<? extends Sessioninfo> builder = OqlBuilder.from(sessioninfoBuilder.getSessioninfoType(),
        "info");
    builder.where("info.id=:sessionid", sessionId);
    List<? extends Sessioninfo> infos = entityDao.search(builder);
    if (infos.isEmpty()) return null;
    else return infos.get(0);
  }

  public SessionStatus getSessionStatus(String sessionid) {
    OqlBuilder<SessionStatus> builder = OqlBuilder.from(sessioninfoBuilder.getSessioninfoType().getName(),
        "info");
    builder.where("info.id=:sessionid", sessionid)
        .select("new org.beangle.security.core.session.SessionStatus(info.username,info.expiredAt)")
        .cacheable();
    List<SessionStatus> infos = entityDao.search(builder);
    if (infos.isEmpty()) return null;
    else return infos.get(0);
  }

  public void register(Authentication auth, String sessionId) throws SessionException {
    SessionStatus existed = getSessionStatus(sessionId);
    String principal = auth.getName();
    // 是否为重复注册
    if (null != existed && Objects.equals(existed.getUsername(), principal)) return;
    // 争取名额
    boolean success = controller.onRegister(auth, sessionId, this);
    if (!success) throw new SessionException("security.OvermaxSession");
    // 注销同会话的其它账户
    if (null != existed) remove(sessionId, " expired with replacement.");
    // 新生
    entityDao.save(sessioninfoBuilder.build(auth, sessionId));
  }

  public Sessioninfo remove(String sessionId) {
    return remove(sessionId, null);
  }

  private Sessioninfo remove(String sessionId, String reason) {
    Sessioninfo info = getSessioninfo(sessionId);
    if (null == info) {
      return null;
    } else {
      // FIXME not in a transaction
      if (null != reason) info.addRemark(reason);
      entityDao.remove(info);
      controller.onLogout(info);
      entries.remove(info.getId());
      Object sessioninfoLog = sessioninfoBuilder.buildLog(info);
      if (null != sessioninfoLog) entityDao.save(sessioninfoLog);
      logger.debug("Remove session {} for {}", sessionId, info.getUsername());
      return info;
    }
  }

  public void expire(String sessionId) {
    Sessioninfo info = getSessioninfo(sessionId);
    if (null != info) {
      controller.onLogout(info);
      info.expireNow();
      entityDao.saveOrUpdate(info);
    }
  }

  @SuppressWarnings({ "rawtypes", "unchecked" })
  public int count() {
    OqlBuilder builder = OqlBuilder.from(Sessioninfo.class, "info");
    builder.select("count(id)");
    List<Number> numbers = entityDao.search(builder);
    if (numbers.isEmpty()) return 0;
    else return (numbers.get(0)).intValue();
  }

  public void setController(SessionController controller) {
    this.controller = controller;
  }

  public SessionController getController() {
    return controller;
  }

  public void setSessioninfoBuilder(SessioninfoBuilder sessioninfoBuilder) {
    this.sessioninfoBuilder = sessioninfoBuilder;
  }

  public SessioninfoBuilder getSessioninfoBuilder() {
    return sessioninfoBuilder;
  }

  public void access(String sessionid, String resource, long accessAt) {
    if (accessAt - updatedAt > updateInterval) {
      new Thread(new AccessUpdaterTask(this)).start();
    }
    AccessEntry entry = entries.get(sessionid);
    if (null == entry) entries.put(sessionid, new AccessEntry(resource, accessAt));
    else entry.access(resource, accessAt);
  }

  public String getResource(String sessionid) {
    AccessEntry entry = entries.get(sessionid);
    if (null == entry) return null;
    else return entry.resource;
  }

  public int getUpdateInterval() {
    return updateInterval;
  }

  public void setUpdateInterval(int updateInterval) {
    this.updateInterval = updateInterval;
  }

}

/**
 * Last AccessAt updater Task
 * 
 * @author chaostone
 */
class AccessUpdaterTask implements Runnable {

  DbSessionRegistry registry;

  public AccessUpdaterTask(DbSessionRegistry registry) {
    super();
    this.registry = registry;
  }

  public void run() {
    EntityDao entityDao = registry.getEntityDao();
    long updatedAt = registry.updatedAt;
    List<Object[]> arguments = CollectUtils.newArrayList();
    for (Map.Entry<String, AccessEntry> entry : registry.entries.entrySet()) {
      AccessEntry accessEntry = entry.getValue();
      if (accessEntry.accessAt > updatedAt) {
        Date accessAt = new Date(entry.getValue().accessAt);
        arguments.add(new Object[] { accessAt, entry.getKey(), accessAt });
      }
    }
    if (!arguments.isEmpty()) {
      entityDao.executeUpdateHqlRepeatly("update "
          + registry.getSessioninfoBuilder().getSessioninfoType().getName()
          + " info set info.lastAccessAt=? where info.id=? and info.lastAccessAt < ? ", arguments);
    }
    registry.updatedAt = System.currentTimeMillis();
  }

}

class AccessEntry {
  String resource;
  long accessAt;

  public AccessEntry(String resource, long accessMillis) {
    super();
    this.resource = resource;
    this.accessAt = accessMillis;
  }

  public void access(String resource, long accessMillis) {
    this.resource = resource;
    this.accessAt = accessMillis;
  }
}

/**
 * DB session info cleaner
 * 
 * @author chaostone
 */
class SessionCleanerTask extends TimerTask {

  private final Logger logger = LoggerFactory.getLogger(DbSessionRegistry.class);

  private final SessionRegistry registry;

  private EntityDao entityDao;

  /** 默认 过期时间 30分钟 */
  private int expiredTime = 30;

  public SessionCleanerTask(SessionRegistry registry) {
    super();
    this.registry = registry;
  }

  public SessionCleanerTask(SessionRegistry registry, int expiredTime) {
    this.registry = registry;
    this.expiredTime = expiredTime;
  }

  @Override
  public void run() {
    Stopwatch watch = new Stopwatch().start();
    logger.debug("clean up expired or over maxOnlineTime session start ...");
    Calendar calendar = Calendar.getInstance();
    OqlBuilder<? extends Sessioninfo> builder = OqlBuilder.from(registry.getSessioninfoBuilder()
        .getSessioninfoType(), "info");
    builder.where("info.lastAccessAt is null or info.lastAccessAt<:givenTime",
        Dates.rollMinutes(calendar.getTime(), -expiredTime));
    List<? extends Sessioninfo> infos = entityDao.search(builder);
    int removed = 0;
    for (Sessioninfo info : infos) {
      registry.remove(info.getId());
      removed++;
    }
    if (removed > 0 || watch.elapsedMillis() > 50) {
      logger.info("removed {} expired sessions in {}", removed, watch);
    }
    registry.getController().stat();
  }

  public void setEntityDao(EntityDao entityDao) {
    this.entityDao = entityDao;
  }

}
