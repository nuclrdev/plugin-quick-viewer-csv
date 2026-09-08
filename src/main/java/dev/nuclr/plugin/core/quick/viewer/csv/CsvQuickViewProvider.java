/*

	Copyright 2026 Sergio, Nuclr (https://nuclr.dev)

	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at

	http://www.apache.org/licenses/LICENSE-2.0

	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.

*/
package dev.nuclr.plugin.core.quick.viewer.csv;

import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JComponent;

import dev.nuclr.platform.NuclrThemeScheme;
import dev.nuclr.platform.plugin.NuclrPluginContext;
import dev.nuclr.platform.plugin.NuclrResource;
import dev.nuclr.platform.plugin.QuickViewNuclrPlugin;
import lombok.extern.slf4j.Slf4j;

/**
 * Quick-view provider for CSV, TSV and other delimited text: shows the file as
 * a sortable, filterable grid one page at a time, and exports what the user
 * selects.
 *
 * <p>Claims files by extension only. Priority 0 puts it ahead of the text quick
 * viewer, which would otherwise show a .csv as the raw lines it is made of.
 */
@Slf4j
public class CsvQuickViewProvider implements QuickViewNuclrPlugin {

	private static final String ID = "dev.nuclr.plugin.core.quickviewer.csv";

	private NuclrPluginContext context;
	private CsvQuickViewPanel panel;
	private NuclrThemeScheme theme;
	private NuclrResource currentResource;
	private volatile AtomicBoolean currentCancelled;

	@Override
	public JComponent panel() {

		if (panel == null) {
			panel = new CsvQuickViewPanel();
			panel.applyTheme(theme);
			if (context != null) {
				panel.setSettings(context.getSettings());
			}
		}

		return panel;
	}

	@Override
	public void preinit(NuclrPluginContext context) {

		this.context = context;
		this.theme = context != null ? context.getTheme() : null;

		if (panel != null) {
			panel.applyTheme(theme);
			panel.setSettings(context != null ? context.getSettings() : null);
		}
	}

	@Override
	public void init() {
		// Nothing to start: the panel is built on first use and the file is read
		// on the thread the host opens it on.
	}

	@Override
	public NuclrPluginContext getContext() {
		return context;
	}

	@Override
	public boolean supports(NuclrResource resource) {
		return CsvFileSupport.supports(resource);
	}

	@Override
	public boolean openResource(NuclrResource resource, AtomicBoolean cancelled) {

		// Abandon whatever the previous selection started before taking this one on:
		// the host moves the cursor faster than a large file parses.
		if (currentCancelled != null) {
			currentCancelled.set(true);
		}

		currentResource = resource;
		currentCancelled = cancelled;
		panel();

		return panel.load(resource, cancelled);
	}

	@Override
	public void closeResource() {

		if (currentCancelled != null) {
			currentCancelled.set(true);
			currentCancelled = null;
		}

		currentResource = null;

		if (panel != null) {
			panel.clear();
		}
	}

	@Override
	public void unload() {

		closeResource();
		panel = null;
		context = null;
		theme = null;
	}

	@Override
	public void updateTheme(NuclrThemeScheme themeScheme) {

		this.theme = themeScheme;

		if (panel != null) {
			panel.applyTheme(themeScheme);
		}
	}

	@Override
	public NuclrResource getCurrentResource() {
		return currentResource;
	}

	@Override
	public boolean onFocusGained() {
		// Unlike most quick views this one is worked in - sorted, filtered, rows
		// picked for export - so it takes focus and reports it honestly.
		return panel != null && panel.focusTable();
	}

	@Override
	public void onFocusLost() {
		// Nothing to dim: the grid's own selection colours already track focus.
	}

	@Override
	public boolean isFocused() {
		return panel != null && panel.isFocusOwnerWithin();
	}

	@Override
	public String uuid() {
		return ID;
	}

	@Override
	public String getWindowTitle() {
		return "Quick View: " + (currentResource != null ? currentResource.getName() : "");
	}
}
