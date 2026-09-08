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

import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * Streaming RFC 4180 record reader: one record at a time, so a file is never
 * held in memory in two forms at once.
 *
 * <p>Deliberately lenient, because a quick viewer previews whatever the user
 * happens to select and showing a best-effort grid always beats showing a parse
 * error: a stray quote in the middle of an unquoted field is data, an
 * unterminated quote at end of input closes itself, and rows are allowed to
 * disagree about how many fields they have.
 */
final class CsvParser implements Closeable {

	/** A cell longer than this is cut: no readable grid cell approaches it, and it bounds a pathological file. */
	static final int MAX_CELL_CHARS = 16_384;

	private static final int BUFFER_SIZE = 16 * 1024;

	private final Reader reader;
	private final char delimiter;
	private final char quote;
	private final char[] buffer = new char[BUFFER_SIZE];

	private int limit;
	private int position;
	private boolean endOfStream;
	private long charsConsumed;
	private boolean cellTruncated;

	CsvParser(Reader reader, char delimiter, char quote) {
		this.reader = reader;
		this.delimiter = delimiter;
		this.quote = quote;
	}

	/**
	 * Reads the next record.
	 *
	 * @return its fields, or {@code null} at end of input
	 * @throws IOException if the underlying reader fails
	 */
	List<String> next() throws IOException {

		List<String> fields = new ArrayList<>(8);
		StringBuilder field = new StringBuilder(32);
		boolean quoted = false;
		boolean inQuotes = false;
		boolean anything = false;

		while (true) {

			int c = read();

			if (c < 0) {
				// End of input. Nothing consumed means the previous record ended on the
				// file's last line terminator - that is the end, not a trailing empty row.
				if (!anything) {
					return null;
				}
				fields.add(field.toString());
				return fields;
			}

			anything = true;
			char ch = (char) c;

			if (inQuotes) {
				if (ch == quote) {
					if (peek() == quote) {
						read();
						append(field, quote);
					} else {
						inQuotes = false;
					}
				} else {
					append(field, ch);
				}
				continue;
			}

			if (ch == quote && !quoted && field.isEmpty()) {
				quoted = true;
				inQuotes = true;
				continue;
			}

			if (ch == delimiter) {
				fields.add(field.toString());
				field = new StringBuilder(32);
				quoted = false;
				continue;
			}

			if (ch == '\n') {
				fields.add(field.toString());
				return fields;
			}

			if (ch == '\r') {
				if (peek() == '\n') {
					read();
				}
				fields.add(field.toString());
				return fields;
			}

			append(field, ch);
		}
	}

	/** Characters read from the underlying reader so far - the loader's budget is spent in these. */
	long charsConsumed() {
		return charsConsumed;
	}

	/** Whether any cell hit {@link #MAX_CELL_CHARS} and lost its tail. */
	boolean cellTruncated() {
		return cellTruncated;
	}

	@Override
	public void close() throws IOException {
		reader.close();
	}

	// -------------------------------------------------------------------------

	private void append(StringBuilder field, char ch) {
		if (field.length() >= MAX_CELL_CHARS) {
			cellTruncated = true;
			return;
		}
		field.append(ch);
	}

	private int read() throws IOException {
		if (position >= limit && !fill()) {
			return -1;
		}
		charsConsumed++;
		return buffer[position++];
	}

	private int peek() throws IOException {
		if (position >= limit && !fill()) {
			return -1;
		}
		return buffer[position];
	}

	private boolean fill() throws IOException {
		if (endOfStream) {
			return false;
		}
		int read = reader.read(buffer, 0, buffer.length);
		if (read < 0) {
			endOfStream = true;
			return false;
		}
		position = 0;
		limit = read;
		// A reader is allowed to return 0; loop until it gives us something or ends.
		return read > 0 || fill();
	}
}
