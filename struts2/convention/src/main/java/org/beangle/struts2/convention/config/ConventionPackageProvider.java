/*
 * Beangle, Agile Java/Scala Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2013, Beangle Software.
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
package org.beangle.struts2.convention.config;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletContext;

import org.apache.struts2.ServletActionContext;
import org.apache.struts2.views.freemarker.FreemarkerManager;
import org.beangle.commons.collection.CollectUtils;
import org.beangle.commons.lang.Objects;
import org.beangle.commons.lang.Strings;
import org.beangle.commons.lang.time.Stopwatch;
import org.beangle.commons.text.i18n.TextBundleRegistry;
import org.beangle.struts2.annotation.Result;
import org.beangle.struts2.annotation.Results;
import org.beangle.struts2.convention.config.ActionFinder.ActionTest;
import org.beangle.struts2.convention.route.Action;
import org.beangle.struts2.convention.route.ActionBuilder;
import org.beangle.struts2.convention.route.Profile;
import org.beangle.struts2.convention.route.ProfileService;
import org.beangle.struts2.convention.route.ViewMapper;
import org.beangle.struts2.freemarker.TemplateFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opensymphony.xwork2.ActionContext;
import com.opensymphony.xwork2.ObjectFactory;
import com.opensymphony.xwork2.config.Configuration;
import com.opensymphony.xwork2.config.ConfigurationException;
import com.opensymphony.xwork2.config.PackageProvider;
import com.opensymphony.xwork2.config.entities.ActionConfig;
import com.opensymphony.xwork2.config.entities.PackageConfig;
import com.opensymphony.xwork2.config.entities.ResultConfig;
import com.opensymphony.xwork2.config.entities.ResultTypeConfig;
import com.opensymphony.xwork2.inject.Inject;
import com.opensymphony.xwork2.util.classloader.ReloadingClassLoader;
import com.opensymphony.xwork2.util.finder.ClassLoaderInterface;
import com.opensymphony.xwork2.util.finder.ClassLoaderInterfaceDelegate;

import freemarker.cache.TemplateLoader;

/**
 * <p>
 * This class is a configuration provider for the XWork configuration system. This is really the
 * only way to truly handle loading of the packages, actions and results correctly.
 * </p>
 */
public class ConventionPackageProvider implements PackageProvider {
  private static final Logger logger = LoggerFactory.getLogger(ConventionPackageProvider.class);

  private boolean devMode = false;

  private final Configuration configuration;

  private final FreemarkerManager freemarkerManager;

  private final ViewMapper viewMapper;

  private final ProfileService profileService;

  private final ActionBuilder actionBuilder;

  private final TextBundleRegistry registry;

  private ReloadingClassLoader reloadingClassLoader;

  private ActionFinder actionFinder;

  private List<String> actionPackages = CollectUtils.newArrayList();

  @Inject("beangle.convention.default.parent.package")
  private String defaultParentPackage;

  @Inject("beangle.convention.action.suffix")
  private String actionSuffix;

  @Inject("beangle.i18n.resources")
  private String defaultBundleNames;

  @Inject("beangle.i18n.reload")
  private String reloadBundles = "false";

  // Temperary use
  private TemplateFinder templateFinder;

  @Inject
  public ConventionPackageProvider(Configuration configuration, ObjectFactory objectFactory,
      FreemarkerManager freemarkerManager, ProfileService profileService, ActionBuilder actionBuilder,
      TextBundleRegistry registry, ViewMapper viewMapper) throws Exception {
    this.configuration = configuration;
    actionFinder = (ActionFinder) objectFactory.buildBean(ContainerActionFinder.class,
        new HashMap<String, Object>(0));
    this.freemarkerManager = freemarkerManager;
    this.profileService = profileService;
    this.actionBuilder = actionBuilder;
    this.registry = registry;
    this.viewMapper = viewMapper;
  }

  public void init(Configuration configuration) throws ConfigurationException {
    registry.addDefaults(Strings.split(defaultBundleNames));
    registry.setReloadBundles(Boolean.valueOf(reloadBundles));
    templateFinder = buildTemplateFinder();
  }

  public void loadPackages() throws ConfigurationException {
    Stopwatch watch = new Stopwatch(true);
    for (Profile profile : actionBuilder.getProfileService().getProfiles()) {
      if (profile.isActionScan()) actionPackages.add(profile.getActionPattern());
    }
    if (actionPackages.isEmpty()) { return; }

    initReloadClassLoader();
    Map<String, PackageConfig.Builder> packageConfigs = new HashMap<String, PackageConfig.Builder>();
    int newActions = 0;
    int overrideActions = 0;
    Map<Class<?>, String> actionNames = actionFinder.getActions(new ActionTest(actionSuffix, actionPackages));
    Map<String, Class<?>> name2Actions = CollectUtils.newHashMap();
    Map<String, PackageConfig.Builder> name2Packages = CollectUtils.newHashMap();
    for (Map.Entry<Class<?>, String> entry : actionNames.entrySet()) {
      Class<?> actionClass = entry.getKey();
      String beanName = entry.getValue();
      Profile profile = actionBuilder.getProfileService().getProfile(actionClass.getName());
      Action action = actionBuilder.build(actionClass);
      String key = action.getNamespace() + "/" + action.getName();
      Class<?> existAction = name2Actions.get(key);

      PackageConfig.Builder pcb = null;
      if (null == existAction) {
        pcb = getPackageConfig(profile, packageConfigs, action, actionClass);
      } else {
        pcb = name2Packages.get(key);
      }

      if (null == existAction) {
        if (createActionConfig(pcb, action, actionClass, beanName)) {
          newActions++;
          name2Actions.put(key, actionClass);
          name2Packages.put(key, pcb);
        }
      } else {
        if (!actionClass.isAssignableFrom(existAction)) {
          String actionName = action.getName();
          ActionConfig.Builder actionConfig = new ActionConfig.Builder(pcb.getName(), actionName, beanName);
          actionConfig.methodName(action.getMethod());
          actionConfig.addResultConfigs(buildResultConfigs(actionClass, pcb));
          pcb.addActionConfig(actionName, actionConfig.build());
          name2Actions.put(key, actionClass);
          name2Packages.put(key, pcb);
          overrideActions++;
        }
      }
    }
    newActions += buildIndexActions(packageConfigs);
    // Add the new actions to the configuration
    Set<String> packageNames = packageConfigs.keySet();
    for (String packageName : packageNames) {
      configuration.removePackageConfig(packageName);
      configuration.addPackageConfig(packageName, packageConfigs.get(packageName).build());
    }
    templateFinder = null;
    logger.info("Action scan completed,create {} new action(override {}) in {}.", new Object[] { newActions,
        overrideActions, watch });
  }

  protected void initReloadClassLoader() {
    if (isReloadEnabled() && reloadingClassLoader == null) reloadingClassLoader = new ReloadingClassLoader(
        getClassLoader());
  }

  protected ClassLoader getClassLoader() {
    return Thread.currentThread().getContextClassLoader();
  }

  protected ClassLoaderInterface getClassLoaderInterface() {
    if (isReloadEnabled()) return new ClassLoaderInterfaceDelegate(reloadingClassLoader);
    else {
      ClassLoaderInterface classLoaderInterface = null;
      ActionContext ctx = ActionContext.getContext();
      if (ctx != null) classLoaderInterface = (ClassLoaderInterface) ctx
          .get(ClassLoaderInterface.CLASS_LOADER_INTERFACE);
      return Objects.defaultIfNull(classLoaderInterface, new ClassLoaderInterfaceDelegate(getClassLoader()));
    }
  }

  protected boolean isReloadEnabled() {
    return devMode;
  }

  protected boolean createActionConfig(PackageConfig.Builder pkgCfg, Action action, Class<?> actionClass,
      String beanName) {
    ActionConfig.Builder actionConfig = new ActionConfig.Builder(pkgCfg.getName(), action.getName(), beanName);
    actionConfig.methodName(action.getMethod());
    String actionName = action.getName();
    // check action exists on that package (from XML config probably)
    PackageConfig existedPkg = configuration.getPackageConfig(pkgCfg.getName());
    boolean create = true;
    if (existedPkg != null) {
      ActionConfig existed = existedPkg.getActionConfigs().get(actionName);
      create = (null == existed);
    }
    if (create) {
      actionConfig.addResultConfigs(buildResultConfigs(actionClass, pkgCfg));
      pkgCfg.addActionConfig(actionName, actionConfig.build());
      logger.debug("Add {}/{} for {} in {}",
          new Object[] { pkgCfg.getNamespace(), actionName, actionClass.getName(), pkgCfg.getName() });
    }
    return create;
  }

  protected boolean shouldGenerateResult(Method m) {
    if (String.class.equals(m.getReturnType()) && m.getParameterTypes().length == 0
        && Modifier.isPublic(m.getModifiers()) && !Modifier.isStatic(m.getModifiers())) {
      String name = m.getName().toLowerCase();
      if (Strings.contains(name, "save") || Strings.contains(name, "remove")
          || Strings.contains(name, "export") || Strings.contains(name, "import")
          || Strings.contains(name, "execute") || Strings.contains(name, "toString")) { return false; }
      return true;
    }
    return false;
  }

  /**
   * generator default results by method name
   * 
   * @param clazz
   * @return
   */
  protected List<ResultConfig> buildResultConfigs(Class<?> clazz, PackageConfig.Builder pcb) {
    List<ResultConfig> configs = CollectUtils.newArrayList();
    // load annotation results
    Result[] results = new Result[0];
    Results rs = clazz.getAnnotation(Results.class);
    if (null == rs) {
      org.beangle.struts2.annotation.Action an = clazz
          .getAnnotation(org.beangle.struts2.annotation.Action.class);
      if (null != an) results = an.results();
    } else {
      results = rs.value();
    }
    Set<String> annotationResults = CollectUtils.newHashSet();
    if (null != results) {
      for (Result result : results) {
        String resultType = result.type();
        if (Strings.isEmpty(resultType)) resultType = "dispatcher";
        ResultTypeConfig rtc = pcb.getResultType(resultType);
        configs.add(new ResultConfig.Builder(result.name(), rtc.getClassName()).addParam(
            rtc.getDefaultResultParam(), result.location()).build());
        annotationResults.add(result.name());
      }
    }
    // load ftl convension results
    if (null == profileService) return configs;
    String extention = profileService.getProfile(clazz.getName()).getViewExtension();
    if (!extention.equals("ftl")) return configs;
    for (Method m : clazz.getMethods()) {
      String methodName = m.getName();
      if (!annotationResults.contains(methodName) && shouldGenerateResult(m)) {
        String path = templateFinder.find(clazz, methodName, methodName, extention);
        if (null != path) {
          ResultTypeConfig rtc = pcb.getResultType("freemarker");
          configs.add(new ResultConfig.Builder(m.getName(), rtc.getClassName()).addParam(
              rtc.getDefaultResultParam(), path).build());
        }
      }
    }
    return configs;
  }

  protected PackageConfig.Builder getPackageConfig(Profile profile,
      final Map<String, PackageConfig.Builder> packageConfigs, Action action, final Class<?> actionClass) {
    // 循环查找父包
    String actionPkg = actionClass.getPackage().getName();
    PackageConfig parentPkg = null;
    while (Strings.contains(actionPkg, '.')) {
      parentPkg = configuration.getPackageConfig(actionPkg);
      if (null != parentPkg) {
        break;
      } else {
        actionPkg = Strings.substringBeforeLast(actionPkg, ".");
      }
    }
    if (null == parentPkg) {
      actionPkg = defaultParentPackage;
      parentPkg = configuration.getPackageConfig(actionPkg);
    }
    if (parentPkg == null) { throw new ConfigurationException("Unable to locate parent package ["
        + actionClass.getPackage().getName() + "]"); }

    String actionPackage = actionClass.getPackage().getName();
    PackageConfig.Builder pkgConfig = packageConfigs.get(actionPackage);
    if (pkgConfig == null) {
      PackageConfig myPkg = configuration.getPackageConfig(actionPackage);
      if (null != myPkg) {
        pkgConfig = new PackageConfig.Builder(myPkg);
      } else {
        pkgConfig = new PackageConfig.Builder(actionPackage).namespace(action.getNamespace()).addParent(
            parentPkg);
        logger.debug("Created package config named {} with a namespace {}", actionPackage,
            action.getNamespace());
      }
      packageConfigs.put(actionPackage, pkgConfig);
    }
    return pkgConfig;
  }

  /**
   * Determine all the index handling actions and results based on this logic:
   * <p>
   * 1. Loop over all the namespaces such as /foo and see if it has an action named index<br>
   * 2. If an action doesn't exists in the parent namespace of the same name, create an action in
   * the parent namespace of the same name as the namespace that points to the index action in the
   * namespace. e.g. /foo -> /foo/index<br>
   * 3. Create the action in the namespace for empty string if it doesn't exist. e.g. /foo/ the
   * action is "" and the namespace is /foo
   * </p>
   * 
   * @param packageConfigs
   *          Used to store the actions.
   */
  protected int buildIndexActions(Map<String, PackageConfig.Builder> packageConfigs) {
    int createCount = 0;
    Map<String, PackageConfig.Builder> byNamespace = new HashMap<String, PackageConfig.Builder>();
    Collection<PackageConfig.Builder> values = packageConfigs.values();
    for (PackageConfig.Builder packageConfig : values) {
      byNamespace.put(packageConfig.getNamespace(), packageConfig);
    }
    Set<String> namespaces = byNamespace.keySet();
    for (String namespace : namespaces) {
      // First see if the namespace has an index action
      PackageConfig.Builder pkgConfig = byNamespace.get(namespace);
      ActionConfig indexActionConfig = pkgConfig.build().getAllActionConfigs().get("index");
      if (indexActionConfig == null) {
        continue;
      }
      if (pkgConfig.build().getAllActionConfigs().get("") == null) {
        logger.debug("Creating index ActionConfig with an action name of [] for the action class {}",
            indexActionConfig.getClassName());
        pkgConfig.addActionConfig("", indexActionConfig);
        createCount++;
      }
    }
    return createCount;
  }

  public boolean needsReload() {
    return devMode;
  }

  private TemplateFinder buildTemplateFinder() {
    ServletContext sc = ServletActionContext.getServletContext();
    TemplateLoader loader = freemarkerManager.getConfiguration(sc).getTemplateLoader();
    return new TemplateFinder(loader, viewMapper);
  }

}