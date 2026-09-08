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

import javax.swing.table.AbstractTableModel;

/**
 * The rows of one page.
 *
 * <p>The model is a window onto {@link CsvData}: it holds the view (the row
 * indices the filter and sort produced) and the offset of the current page into
 * it, so turning a page copies nothing.
 *
 * <p>Column 0 is the row-number gutter. It carries the row's position in the
 * file, which is the only thing that still identifies a row once it has been
 * filtered and sorted, so every other column is offset by one - hence
 * {@link #dataColumn(int)} and {@link #tableColumn(int)}.
 */
final class CsvTableModel extends AbstractTableModel {

	private static final long serialVersionUID = 1L;

	/** Columns shown before the file's own: just the row-number gutter. */
	static final int GUTTER_COLUMNS = 1;

	private CsvData data;
	private int[] view = new int[0];
	private int pageStart;
	private int pageRows;

	/** Replaces the file, clearing the page: the caller sets a view straight after. */
	void setData(CsvData data) {

		this.data = data;
		this.view = new int[0];
		this.pageStart = 0;
		this.pageRows = 0;
		fireTableStructureChanged();
	}

	/**
	 * Shows a slice of a view.
	 *
	 * @param view      row indices in display order
	 * @param pageStart index into {@code view} of the page's first row
	 * @param pageRows  how many rows the page holds
	 */
	void setPage(int[] view, int pageStart, int pageRows) {

		this.view = view != null ? view : new int[0];
		this.pageStart = Math.max(0, pageStart);
		this.pageRows = Math.max(0, Math.min(pageRows, this.view.length - this.pageStart));
		fireTableDataChanged();
	}

	CsvData data() {
		return data;
	}

	/** Index into the view of a row on screen - the coordinate search hits use. */
	int viewRow(int tableRow) {
		return pageStart + tableRow;
	}

	/** The row's index in the file, or {@code -1} when the table row is not on this page. */
	int sourceRow(int tableRow) {

		int index = pageStart + tableRow;
		return index >= 0 && index < view.length ? view[index] : -1;
	}

	/** The file column a table column shows, or {@code -1} for the gutter. */
	static int dataColumn(int tableColumn) {
		return tableColumn - GUTTER_COLUMNS;
	}

	/** Where a file column sits in the table. */
	static int tableColumn(int dataColumn) {
		return dataColumn + GUTTER_COLUMNS;
	}

	@Override
	public int getRowCount() {
		return pageRows;
	}

	@Override
	public int getColumnCount() {
		return data == null ? 0 : data.columnCount() + GUTTER_COLUMNS;
	}

	@Override
	public String getColumnName(int column) {

		if (data == null) {
			return "";
		}

		return column == 0 ? "#" : data.columnName(dataColumn(column));
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {

		int row = sourceRow(rowIndex);
		if (data == null || row < 0) {
			return "";
		}

		if (columnIndex == 0) {
			// The row's own number in the file, 1-based, so it survives sorting.
			return ViewerUi.count(row + 1L);
		}

		return data.value(row, dataColumn(columnIndex));
	}

	@Override
	public boolean isCellEditable(int rowIndex, int columnIndex) {
		return false;
	}

	@Override
	public Class<?> getColumnClass(int columnIndex) {
		return String.class;
	}
}
