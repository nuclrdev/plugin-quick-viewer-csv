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

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes rows back out as CSV - to a file the user picks, or to the clipboard.
 *
 * <p>Always RFC 4180 with CRLF line endings and UTF-8, whatever the source file
 * used, because the export is going to a spreadsheet or another tool rather
 * than back to where it came from. The separator is the caller's choice so that
 * a semicolon-separated original can be re-exported as it was.
 */
final class CsvExporter {

	/**
	 * Rows past which the clipboard is the wrong destination. The system
	 * clipboard holds one string, so a selection this size would build tens of
	 * megabytes of characters just to hand them over; the viewer offers a file
	 * instead.
	 */
	static final int CLIPBOARD_ROW_LIMIT = 100_000;

	private static final String LINE_END = "\r\n";

	private CsvExporter() {
	}

	/**
	 * Writes the given rows.
	 *
	 * @param out           where to write; not closed here
	 * @param data          the file the rows come from
	 * @param rows          row indices, in the order they should be written
	 * @param includeHeader whether to write the column names first
	 * @param delimiter     the separator to write with
	 * @throws IOException if writing fails
	 */
	static void write(Writer out, CsvData data, int[] rows, boolean includeHeader, char delimiter)
			throws IOException {

		int columnCount = data.columnCount();

		if (includeHeader) {
			for (int column = 0; column < columnCount; column++) {
				if (column > 0) {
					out.write(delimiter);
				}
				out.write(escape(data.columnName(column), delimiter));
			}
			out.write(LINE_END);
		}

		for (int row : rows) {
			for (int column = 0; column < columnCount; column++) {
				if (column > 0) {
					out.write(delimiter);
				}
				out.write(escape(data.value(row, column), delimiter));
			}
			out.write(LINE_END);
		}
	}

	/**
	 * Renders the given rows as one string, for the clipboard.
	 *
	 * @param data          the file the rows come from
	 * @param rows          row indices, in the order they should be written
	 * @param includeHeader whether to include the column names
	 * @param delimiter     the separator to write with
	 * @return the CSV text
	 */
	static String toText(CsvData data, int[] rows, boolean includeHeader, char delimiter) {

		StringWriter writer = new StringWriter();
		try {
			write(writer, data, rows, includeHeader, delimiter);
		} catch (IOException e) {
			// A StringWriter does not fail; this only satisfies the signature.
			throw new IllegalStateException(e);
		}

		return writer.toString();
	}

	/**
	 * Writes the given rows to a file, replacing what is there.
	 *
	 * @param file          the destination
	 * @param data          the file the rows come from
	 * @param rows          row indices, in the order they should be written
	 * @param includeHeader whether to write the column names first
	 * @param delimiter     the separator to write with
	 * @throws IOException if the file cannot be written
	 */
	static void writeFile(Path file, CsvData data, int[] rows, boolean includeHeader, char delimiter)
			throws IOException {

		try (OutputStream out = Files.newOutputStream(file);
				Writer writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8), 64 * 1024)) {
			write(writer, data, rows, includeHeader, delimiter);
		}
	}

	/**
	 * Quotes a value if it needs it.
	 *
	 * @param value     the cell text; {@code null} is written as empty
	 * @param delimiter the separator in use, which is what most values need quoting for
	 * @return the value as it should appear in the file
	 */
	static String escape(String value, char delimiter) {

		if (value == null || value.isEmpty()) {
			return "";
		}

		boolean quote = value.indexOf(delimiter) >= 0
				|| value.indexOf('"') >= 0
				|| value.indexOf('\n') >= 0
				|| value.indexOf('\r') >= 0;

		if (!quote) {
			return value;
		}

		return '"' + value.replace("\"", "\"\"") + '"';
	}

	/** A {@link Writer} over a {@link StringBuilder}; {@code java.io.StringWriter} synchronises on every call. */
	private static final class StringWriter extends Writer {

		private final StringBuilder text = new StringBuilder(4096);

		@Override
		public void write(char[] buffer, int offset, int length) {
			text.append(buffer, offset, length);
		}

		@Override
		public void write(String value) {
			text.append(value);
		}

		@Override
		public void write(int ch) {
			text.append((char) ch);
		}

		@Override
		public void flush() {
			// Nothing buffered elsewhere.
		}

		@Override
		public void close() {
			// Nothing to release.
		}

		@Override
		public String toString() {
			return text.toString();
		}
	}
}
