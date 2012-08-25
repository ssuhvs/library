/* Copyright c 2005-2012.
 * Licensed under GNU  LESSER General Public License, Version 3.
 * http://www.gnu.org/licenses
 */
package org.beangle.security.blueprint.data.model;

import java.security.Principal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.persistence.Cacheable;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.ManyToOne;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import org.beangle.commons.entity.pojo.TemporalActiveEntity;
import org.beangle.commons.entity.pojo.LongIdObject;
import org.beangle.security.blueprint.Role;
import org.beangle.security.blueprint.data.DataPermission;
import org.beangle.security.blueprint.data.DataResource;
import org.beangle.security.blueprint.function.FuncResource;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.google.gson.GsonBuilder;

/**
 * 数据授权实体
 * 
 * @author chaostone
 * @since 3.0.0
 */
@Entity(name = "org.beangle.security.blueprint.data.DataPermission")
@Cacheable
@Cache(region = "beangle.security", usage = CacheConcurrencyStrategy.READ_WRITE)
public class DataPermissionBean extends LongIdObject implements TemporalActiveEntity, DataPermission {

  private static final long serialVersionUID = -8956079356245507990L;

  /** 角色 */
  @ManyToOne(fetch = FetchType.LAZY)
  protected Role role;

  /** 数据资源 */
  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  protected DataResource resource;

  /** 功能资源 */
  @ManyToOne(fetch = FetchType.LAZY)
  protected FuncResource funcResource;

  /** 授权的操作 */
  @Size(max = 100)
  protected String actions;

  /** 资源过滤器 */
  @Size(max = 500)
  protected String filters;

  /** 访问满足的检查(入口\人员等) */
  @Size(max = 500)
  protected String restrictions;

  /** 能够访问哪些属性 */
  @Size(max = 300)
  protected String attrs;

  /** 生效时间 */
  protected Date effectiveAt;

  /** 失效时间 */
  protected Date invalidAt;

  /** 备注 */
  @Size(max = 100)
  protected String remark;

  public DataPermissionBean() {
    super();
  }

  public DataPermissionBean(Long id) {
    super(id);
  }

  public DataPermissionBean(Role role, DataResource resource, String actions) {
    super();
    this.role = role;
    this.resource = resource;
    this.actions = actions;
  }

  public Role getRole() {
    return role;
  }

  public void setRole(Role role) {
    this.role = role;
  }

  public Object clone() {
    return new DataPermissionBean(role, resource, actions);
  }

  public String getActions() {
    return actions;
  }

  public void setActions(String actions) {
    this.actions = actions;
  }

  public Date getEffectiveAt() {
    return effectiveAt;
  }

  public void setEffectiveAt(Date effectiveAt) {
    this.effectiveAt = effectiveAt;
  }

  public Date getInvalidAt() {
    return invalidAt;
  }

  public void setInvalidAt(Date invalidAt) {
    this.invalidAt = invalidAt;
  }

  public DataResource getResource() {
    return resource;
  }

  public void setResource(DataResource resource) {
    this.resource = resource;
  }

  public String getFilters() {
    return filters;
  }

  public void setFilters(String filters) {
    this.filters = filters;
  }

  public boolean validate(Map<String, String> conditions) {
    @SuppressWarnings("unchecked")
    Map<String, String> guardMap = new GsonBuilder().create().fromJson(getRestrictions(), HashMap.class);
    for (String key : guardMap.keySet()) {
      if (!guardMap.get(key).equals(conditions.get(key))) return false;
    }
    return true;
  }

  public String getRestrictions() {
    return restrictions;
  }

  public void setRestrictions(String restrictions) {
    this.restrictions = restrictions;
  }

  public String getAttrs() {
    return attrs;
  }

  public void setAttrs(String attrs) {
    this.attrs = attrs;
  }

  public Principal getPrincipal() {
    return role;
  }

  public FuncResource getFuncResource() {
    return funcResource;
  }

  public void setFuncResource(FuncResource funcResource) {
    this.funcResource = funcResource;
  }

  public String getRemark() {
    return remark;
  }

  public void setRemark(String remark) {
    this.remark = remark;
  }

}