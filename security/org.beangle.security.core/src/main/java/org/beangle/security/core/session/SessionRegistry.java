/* Copyright c 2005-2012.
 * Licensed under GNU  LESSER General Public License, Version 3.
 * http://www.gnu.org/licenses
 */
package org.beangle.security.core.session;

import java.util.List;

import org.beangle.security.core.Authentication;

/**
 * 记录session信息的注册表
 * 
 * @author chaostone
 */
public interface SessionRegistry {

  /**
   * 注册
   * 
   * @param authentication
   * @param sessionid
   */
  void register(Authentication authentication, String sessionid) throws SessionException;

  /**
   * 注销指定sessionid
   * 
   * @param sessionid
   */
  Sessioninfo remove(String sessionid);

  /**
   * 过期指定会话
   * 
   * @param sessionid
   */
  void expire(String sessionid);

  /**
   * 查询某帐号的在线信息
   * 
   * @param principal
   * @param includeExpiredSessions
   */
  List<Sessioninfo> getSessioninfos(String principal, boolean includeExpiredSessions);

  /**
   * 查询对应sessionid的信息
   * 
   * @param sessionid
   */
  Sessioninfo getSessioninfo(String sessionid);

  /**
   * 查询会话状态
   * 
   * @param sessionid
   */
  SessionStatus getSessionStatus(String sessionid);

  /**
   * 查询帐号是否还有没有过期的在线记录
   * 
   * @param principal
   */
  boolean isRegisted(String principal);

  /**
   * session count
   */
  int count();

  /**
   * 更新对应sessionId的最后访问时间
   * 
   * @param sessionid
   */
  void access(String sessionid, String resource, long accessAt);

  /**
   * 查询控制器
   */
  SessionController getController();

  /**
   * 获取sessinoinfobuilder
   */
  SessioninfoBuilder getSessioninfoBuilder();

  /**
   * 获取当前session访问的资源
   * 
   * @param sessionid
   */
  String getResource(String sessionid);

}
