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

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.nuclr.platform.plugin.NuclrResource;

/**
 * Turns a resource into a {@link CsvData} inside a fixed budget.
 *
 * <p>A quick view opens whatever the cursor lands on, so the cost of opening
 * has to be bounded rather than proportional to the file: reading stops at
 * {@link #MAX_ROWS} rows or {@link #MAX_CHARS} characters, whichever comes
 * first, and the viewer says so. Both caps are generous enough that ordinary
 * exports load whole and small enough that a runaway log file cannot exhaust
 * the heap.
 */
final class CsvLoader {

	/** Rows kept in memory. At ~10 columns this is a few hundred MB of nothing much, so it also has a character cap. */
	static final int MAX_ROWS = 200_000;

	/** Characters of cell text kept in memory - the real memory bound, roughly 48 MB of strings. */
	static final long MAX_CHARS = 24_000_000L;

	/** Columns kept per row; a file wider than this is a pivot table, not a grid to preview. */
	static final int MAX_COLUMNS = 512;

	/** How much of the head is decoded to sniff the separator and the encoding. */
	static final int SNIFF_BYTES = 64 * 1024;

	/** Rows sampled to decide whether a column holds numbers. */
	private static final int NUMERIC_SAMPLE_ROWS = 500;

	/** Share of a column's sampled values that must parse as numbers for it to count as numeric. */
	private static final double NUMERIC_SHARE = 0.8;

	/** How often the cancellation flag is read while parsing. */
	private static final int CANCEL_CHECK_INTERVAL = 512;

	private CsvLoader() {
	}

	/** Raised when the bytes are not delimited text at all, so another viewer should have the file. */
	static final class NotTabularException extends Exception {

		private static final long serialVersionUID = 1L;

		NotTabularException(String message) {
			super(message);
		}
	}

	/**
	 * Reads {@code resource} into a grid.
	 *
	 * @param resource      the file to read
	 * @param forcedDialect the separator the user picked, or {@code null} to sniff one
	 * @param forcedHeader  {@code TRUE}/{@code FALSE} when the user decided whether the
	 *                      first record is a header, {@code null} to decide by content
	 * @param cancelled     checked while parsing; when it is set, reading stops
	 * @return the parsed data, or {@code null} if the load was cancelled
	 * @throws NotTabularException if the resource is binary
	 * @throws Exception           if the resource cannot be opened or read
	 */
	static CsvData load(NuclrResource resource, CsvDialect forcedDialect, Boolean forcedHeader,
			AtomicBoolean cancelled) throws Exception {

		try (InputStream raw = resource.openInputStream()) {

			BufferedInputStream in = new BufferedInputStream(raw, SNIFF_BYTES + 8192);
			in.mark(SNIFF_BYTES + 8);
			byte[] head = in.readNBytes(SNIFF_BYTES);

			Charset charset = charsetOf(head);
			int bom = bomLength(head);

			if (bom == 0 && containsNullByte(head)) {
				throw new NotTabularException("binary content");
			}

			String sample = decode(head, bom, charset);
			CsvDialect dialect = forcedDialect != null
					? forcedDialect
					: CsvDialect.sniff(sample, CsvFileSupport.name(resource));

			in.reset();
			in.skipNBytes(bom);

			try (Reader reader = reader(in, charset)) {
				return parse(reader, dialect, forcedHeader, resource.getLength(), cancelled);
			}
		}
	}

	// -------------------------------------------------------------------------

	private static CsvData parse(Reader reader, CsvDialect dialect, Boolean forcedHeader, long byteLength,
			AtomicBoolean cancelled) throws Exception {

		List<String> header = null;
		List<String[]> rows = new ArrayList<>();
		int widest = 0;
		boolean truncated = false;

		try (CsvParser parser = new CsvParser(reader, dialect.delimiter(), dialect.quote())) {

			List<String> record;
			while ((record = parser.next()) != null) {

				if (rows.size() % CANCEL_CHECK_INTERVAL == 0 && isCancelled(cancelled)) {
					return null;
				}

				// A blank line is spacing, not a row of one empty cell.
				if (record.size() == 1 && record.get(0).isBlank()) {
					continue;
				}

				if (header == null && rows.isEmpty()) {
					boolean isHeader = forcedHeader != null ? forcedHeader : CsvDialect.looksLikeHeader(record);
					if (isHeader) {
						header = truncate(record);
						widest = Math.max(widest, header.size());
						continue;
					}
				}

				if (rows.size() >= MAX_ROWS || parser.charsConsumed() > MAX_CHARS) {
					truncated = true;
					break;
				}

				List<String> cells = truncate(record);
				widest = Math.max(widest, cells.size());
				rows.add(cells.toArray(new String[0]));
			}
		}

		if (isCancelled(cancelled)) {
			return null;
		}

		List<String> columns = columnNames(header, widest);
		boolean[] numeric = numericColumns(rows, columns.size());

		return new CsvData(columns, rows, numeric, dialect, header != null, truncated, byteLength);
	}

	private static boolean isCancelled(AtomicBoolean cancelled) {
		return (cancelled != null && cancelled.get()) || Thread.currentThread().isInterrupted();
	}

	private static List<String> truncate(List<String> record) {
		return record.size() <= MAX_COLUMNS ? record : record.subList(0, MAX_COLUMNS);
	}

	/**
	 * Names the columns: the header where there is one, and a generated name for
	 * every column the header did not cover - which happens both in a headerless
	 * file and in one whose data rows are wider than its header.
	 */
	private static List<String> columnNames(List<String> header, int widest) {

		List<String> columns = new ArrayList<>(widest);

		for (int i = 0; i < widest; i++) {
			String name = header != null && i < header.size() ? header.get(i) : null;
			columns.add(name == null || name.isBlank() ? "Column " + (i + 1) : name.trim());
		}

		return columns;
	}

	private static boolean[] numericColumns(List<String[]> rows, int columnCount) {

		boolean[] numeric = new boolean[columnCount];
		int sampled = Math.min(rows.size(), NUMERIC_SAMPLE_ROWS);

		for (int column = 0; column < columnCount; column++) {
			int values = 0;
			int numbers = 0;
			for (int row = 0; row < sampled; row++) {
				String[] cells = rows.get(row);
				String value = column < cells.length ? cells[column] : null;
				if (value == null || value.isBlank()) {
					continue;
				}
				values++;
				if (Numbers.isNumeric(value)) {
					numbers++;
				}
			}
			numeric[column] = values > 0 && numbers >= values * NUMERIC_SHARE;
		}

		return numeric;
	}

	private static Reader reader(InputStream in, Charset charset) {
		// REPLACE, not REPORT: one bad byte in a mostly-readable export should cost
		// the user one glyph, not the whole preview.
		return new InputStreamReader(in, charset.newDecoder()
				.onMalformedInput(CodingErrorAction.REPLACE)
				.onUnmappableCharacter(CodingErrorAction.REPLACE));
	}

	private static String decode(byte[] head, int bom, Charset charset) {
		return new String(head, bom, head.length - bom, charset);
	}

	/**
	 * The encoding named by a byte-order mark, or UTF-8. Guessing beyond the BOM
	 * is not worth it: UTF-8 covers what tools emit today, and the decoder is
	 * lenient about the rest.
	 */
	private static Charset charsetOf(byte[] head) {

		if (startsWith(head, 0xFF, 0xFE)) {
			return StandardCharsets.UTF_16LE;
		}
		if (startsWith(head, 0xFE, 0xFF)) {
			return StandardCharsets.UTF_16BE;
		}
		return StandardCharsets.UTF_8;
	}

	private static int bomLength(byte[] head) {

		if (startsWith(head, 0xEF, 0xBB, 0xBF)) {
			return 3;
		}
		if (startsWith(head, 0xFF, 0xFE) || startsWith(head, 0xFE, 0xFF)) {
			return 2;
		}
		return 0;
	}

	private static boolean startsWith(byte[] bytes, int... prefix) {

		if (bytes.length < prefix.length) {
			return false;
		}

		for (int i = 0; i < prefix.length; i++) {
			if ((bytes[i] & 0xFF) != prefix[i]) {
				return false;
			}
		}

		return true;
	}

	/** Null bytes do not occur in any single-byte or UTF-8 text, so they mean binary. */
	private static boolean containsNullByte(byte[] head) {

		for (byte b : head) {
			if (b == 0) {
				return true;
			}
		}

		return false;
	}
}
