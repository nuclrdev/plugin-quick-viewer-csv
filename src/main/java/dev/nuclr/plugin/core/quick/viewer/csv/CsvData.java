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

import java.util.List;

/**
 * One parsed file: the column names and every data row that fitted inside the
 * loader's budget.
 *
 * <p>Immutable once built, because it is produced on a background thread and
 * read from the event-dispatch thread from then on. Rows are ragged - a real
 * file has short lines - so cells are read through {@link #value}, which
 * answers for a column the row never had.
 */
final class CsvData {

	private final List<String> columns;
	private final List<String[]> rows;
	private final boolean[] numericColumns;
	private final CsvDialect dialect;
	private final boolean headerRow;
	private final boolean truncated;
	private final long byteLength;

	CsvData(List<String> columns, List<String[]> rows, boolean[] numericColumns, CsvDialect dialect,
			boolean headerRow, boolean truncated, long byteLength) {
		this.columns = List.copyOf(columns);
		this.rows = rows;
		this.numericColumns = numericColumns;
		this.dialect = dialect;
		this.headerRow = headerRow;
		this.truncated = truncated;
		this.byteLength = byteLength;
	}

	int rowCount() {
		return rows.size();
	}

	int columnCount() {
		return columns.size();
	}

	String columnName(int column) {
		return column >= 0 && column < columns.size() ? columns.get(column) : "";
	}

	List<String> columns() {
		return columns;
	}

	/**
	 * The cell at {@code row}/{@code column}, or an empty string when the row
	 * stopped short of that column.
	 *
	 * @param row    row index, 0-based, excluding the header
	 * @param column column index, 0-based
	 * @return the cell text, never {@code null}
	 */
	String value(int row, int column) {

		if (row < 0 || row >= rows.size() || column < 0) {
			return "";
		}

		String[] cells = rows.get(row);
		return column < cells.length && cells[column] != null ? cells[column] : "";
	}

	/** The raw row, for export and filtering; the array is not copied, so treat it as read-only. */
	String[] row(int row) {
		return rows.get(row);
	}

	/** Whether the column holds numbers, which decides its alignment and how it sorts. */
	boolean isNumericColumn(int column) {
		return column >= 0 && column < numericColumns.length && numericColumns[column];
	}

	CsvDialect dialect() {
		return dialect;
	}

	/** Whether the first record of the file was taken as the column names. */
	boolean hasHeaderRow() {
		return headerRow;
	}

	/** Whether the file was longer than the loader's budget and rows were left unread. */
	boolean isTruncated() {
		return truncated;
	}

	/** Size of the source file in bytes, as the host reported it; {@code 0} when unknown. */
	long byteLength() {
		return byteLength;
	}

	boolean isEmpty() {
		return rows.isEmpty();
	}
}
