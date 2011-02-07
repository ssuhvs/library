/* Copyright c 2005-2012.
 * Licensed under GNU  LESSER General Public License, Version 3.
 * http://www.gnu.org/licenses
 */
package org.beangle.struts2.action;

import java.util.Locale;

import org.beangle.commons.text.AbstractTextResource;
import org.beangle.commons.text.TextResource;

public class ActionTextResource extends AbstractTextResource implements TextResource {

	ActionSupport action;

	public ActionTextResource(ActionSupport action) {
		super();
		this.action = action;
	}

	public Locale getLocale() {
		return action.getLocale();
	}

	public String getText(String key, Object[] args) {
		String[] params = new String[args.length];
		for (int i = 0; i < args.length; i++) {
			params[i] = String.valueOf(args[i]);
		}
		return action.getText(key, params);
	}

	public String getText(String key) {
		return action.getText(key);
	}

	public void setLocale(Locale locale) {

	}

}
