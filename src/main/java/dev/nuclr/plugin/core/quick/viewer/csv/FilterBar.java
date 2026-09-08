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

import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import dev.nuclr.plugin.core.quick.viewer.csv.ViewerUi.Palette;

/**
 * The row above the grid: the filter that applies to every column, the two
 * switches that say how a query is read, the per-column filter toggle, and the
 * export button.
 */
final class FilterBar extends JPanel {

	private static final long serialVersionUID = 1L;

	/** What the bar reports back to the viewer. */
	interface Listener {

		/** The general query changed; the viewer debounces before filtering. */
		void filterChanged(String query);

		/** Regex or case sensitivity was switched, which re-reads every query. */
		void modeChanged(boolean regex, boolean caseSensitive);

		/** The per-column filter row was shown or hidden. */
		void columnFiltersToggled(boolean visible);

		/** The export menu was asked for; {@code anchor} is what it should drop from. */
		void exportRequested(Component anchor);
	}

	private final JTextField query = ViewerUi.compact(new JTextField());
	private final JToggleButton caseSensitive = toggle("Aa", "Match case");
	private final JToggleButton regex = toggle(".*", "Regular expression");
	private final JToggleButton columnFilters = toggle("Cols", "Filter each column separately");
	private final JButton export = button("Export ▾", "Export or copy rows");

	private Palette palette = ViewerUi.palette(null);

	FilterBar(Listener listener) {

		super(new GridBagLayout());
		setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));

		query.setToolTipText("Show only rows where any column matches");
		query.putClientProperty("JTextField.placeholderText", "Filter rows…");
		query.putClientProperty("JTextField.showClearButton", Boolean.TRUE);
		query.getDocument().addDocumentListener(new DocumentListener() {

			@Override
			public void insertUpdate(DocumentEvent e) {
				listener.filterChanged(query.getText());
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				listener.filterChanged(query.getText());
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				listener.filterChanged(query.getText());
			}
		});

		caseSensitive.addActionListener(e -> listener.modeChanged(regex.isSelected(), caseSensitive.isSelected()));
		regex.addActionListener(e -> listener.modeChanged(regex.isSelected(), caseSensitive.isSelected()));
		columnFilters.addActionListener(e -> listener.columnFiltersToggled(columnFilters.isSelected()));
		export.addActionListener(e -> listener.exportRequested(export));

		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridy = 0;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.insets = new Insets(0, 0, 0, 3);
		constraints.weightx = 1;
		add(query, constraints);

		constraints.weightx = 0;
		add(caseSensitive, constraints);
		add(regex, constraints);
		add(columnFilters, constraints);
		constraints.insets = new Insets(0, 0, 0, 0);
		add(export, constraints);
	}

	String query() {
		return query.getText();
	}

	boolean isRegex() {
		return regex.isSelected();
	}

	boolean isCaseSensitive() {
		return caseSensitive.isSelected();
	}

	boolean isColumnFiltersVisible() {
		return columnFilters.isSelected();
	}

	void setColumnFiltersVisible(boolean visible) {
		columnFilters.setSelected(visible);
	}

	/** Clears the query without reporting it; the caller is resetting the view anyway. */
	void reset() {
		query.setText("");
	}

	/**
	 * Marks the query as one the viewer could not use.
	 *
	 * @param message why it could not, or {@code null} when the query is fine
	 */
	void setInvalid(String message) {

		query.setBorder(message == null
				? null
				: BorderFactory.createLineBorder(ViewerUi.blend(palette.accent(), Color.RED, 0.5f)));
		query.setToolTipText(message == null
				? "Show only rows where any column matches"
				: "Not a valid regular expression: " + message);
	}

	void applyTheme(Palette palette) {

		this.palette = palette;
		setBackground(palette.header());

		query.setBackground(palette.background());
		query.setForeground(palette.foreground());
		query.setCaretColor(palette.foreground());

		for (Component component : getComponents()) {
			component.setFont(ViewerUi.smallFont());
		}
	}

	private static JToggleButton toggle(String text, String tooltip) {

		JToggleButton button = ViewerUi.compact(new JToggleButton(text));
		button.setToolTipText(tooltip);
		button.setFocusable(false);
		button.setMargin(new Insets(1, 4, 1, 4));

		return button;
	}

	private static JButton button(String text, String tooltip) {

		JButton button = ViewerUi.compact(new JButton(text));
		button.setToolTipText(tooltip);
		button.setFocusable(false);
		button.setMargin(new Insets(1, 6, 1, 6));

		return button;
	}
}
