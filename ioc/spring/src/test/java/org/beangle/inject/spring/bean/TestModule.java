/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2016, Beangle Software.
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
package org.beangle.inject.spring.bean;

import org.beangle.commons.inject.Scope;
import org.beangle.commons.inject.bind.AbstractBindModule;

public class TestModule extends AbstractBindModule {

  @Override
  protected void doBinding() {
    bind(SomeAction.class).in(Scope.PROTOTYPE);
    bind(UserLdapProvider.class, UserDaoProvider.class).shortName();
    bind(TestService.class).shortName();

    bind(ProviderManager.class).property("providers", listref(UserDaoProvider.class));

    bind(ResourcesConsumer.class).property("resources",
        ";classpath*:META-INF/beangle/table.properties;classpath:beangle/table.properties");
  }

}
