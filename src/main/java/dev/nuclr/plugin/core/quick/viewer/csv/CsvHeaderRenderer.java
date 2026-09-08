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

import java.awt.Component;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

import dev.nuclr.plugin.core.quick.viewer.csv.ViewerUi.Palette;

/**
 * The column titles, with an arrow on the column the grid is sorted by - the
 * feedback that makes a header click feel like a control rather than a guess.
 */
final class CsvHeaderRenderer extends DefaultTableCellRenderer {

	private static final long serialVersionUID = 1L;

	private static final String ASCENDING = "  ▲";
	private static final String DESCENDING = "  ▼";

	private Palette palette = ViewerUi.palette(null);
	private int sortColumn = -1;
	private boolean descending;

	CsvHeaderRenderer() {
		setOpaque(true);
		setHorizontalAlignment(LEFT);
	}

	void applyTheme(Palette palette) {
		this.palette = palette;
	}

	/**
	 * @param dataColumn the file column the grid is sorted by, or {@code -1} for file order
	 * @param descending whether that sort is descending
	 */
	void setSort(int dataColumn, boolean descending) {
		this.sortColumn = dataColumn;
		this.descending = descending;
	}

	@Override
	public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
			boolean hasFocus, int row, int column) {

		super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

		int dataColumn = CsvTableModel.dataColumn(table.convertColumnIndexToModel(column));
		boolean sorted = dataColumn == sortColumn;

		String title = value == null ? "" : value.toString();
		setText(sorted ? title + (descending ? DESCENDING : ASCENDING) : title);
		setToolTipText(title.isEmpty() ? null : title);

		Font font = ViewerUi.defaultFont();
		setFont(sorted ? font.deriveFont(Font.BOLD) : font);
		setBackground(palette.header());
		setForeground(sorted ? palette.foreground() : palette.headerForeground());
		setHorizontalAlignment(dataColumn < 0 ? JLabel.RIGHT : JLabel.LEFT);
		setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 0, 1, 1, palette.grid()),
				BorderFactory.createEmptyBorder(2, 6, 2, 6)));

		return this;
	}
}
