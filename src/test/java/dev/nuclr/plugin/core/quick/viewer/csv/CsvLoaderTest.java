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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

class CsvLoaderTest {

	@Test
	void usesTheFirstRecordAsTheHeader() throws Exception {

		CsvData data = load("people.csv", "name,age\nada,36\nalan,41\n");

		assertTrue(data.hasHeaderRow());
		assertEquals(List.of("name", "age"), data.columns());
		assertEquals(2, data.rowCount());
		assertEquals("ada", data.value(0, 0));
		assertEquals("41", data.value(1, 1));
	}

	@Test
	void generatesColumnNamesWhenThereIsNoHeader() throws Exception {

		CsvData data = load("readings.csv", "1,2\n3,4\n");

		assertFalse(data.hasHeaderRow());
		assertEquals(List.of("Column 1", "Column 2"), data.columns());
		assertEquals(2, data.rowCount());
	}

	@Test
	void answersForColumnsAShortRowNeverHad() throws Exception {

		CsvData data = load("ragged.csv", "a,b,c\nx,y\np,q,r\n");

		assertEquals(3, data.columnCount());
		assertEquals("", data.value(0, 2));
		assertEquals("r", data.value(1, 2));
	}

	@Test
	void widensTheColumnsWhenARowIsLongerThanTheHeader() throws Exception {

		CsvData data = load("wide.csv", "a,b\nx,y,z\n");

		assertEquals(3, data.columnCount());
		assertEquals("Column 3", data.columnName(2));
		assertEquals("z", data.value(0, 2));
	}

	@Test
	void skipsBlankLines() throws Exception {

		CsvData data = load("gappy.csv", "name\nada\n\n\nalan\n");

		assertEquals(2, data.rowCount());
		assertEquals("alan", data.value(1, 0));
	}

	@Test
	void marksNumericColumns() throws Exception {

		CsvData data = load("prices.csv", "item,price,note\nbolt,1.50,short\nnut,0.80,round\n");

		assertFalse(data.isNumericColumn(0));
		assertTrue(data.isNumericColumn(1));
		assertFalse(data.isNumericColumn(2));
	}

	@Test
	void sniffsTheSeparatorAndAcceptsAnOverride() throws Exception {

		String content = "name;city\nada;london\nalan;wilmslow\n";

		assertEquals(';', load("people.csv", content).dialect().delimiter());

		CsvData asCommas = CsvLoader.load(new StringResource("people.csv", content), CsvDialect.of(','), null,
				new AtomicBoolean());
		assertEquals(1, asCommas.columnCount());
	}

	@Test
	void honoursAForcedHeaderDecision() throws Exception {

		String content = "name,age\nada,36\n";

		CsvData withoutHeader = CsvLoader.load(new StringResource("people.csv", content), null, Boolean.FALSE,
				new AtomicBoolean());

		assertFalse(withoutHeader.hasHeaderRow());
		assertEquals(2, withoutHeader.rowCount());
		assertEquals("name", withoutHeader.value(0, 0));
	}

	@Test
	void stripsAByteOrderMark() throws Exception {

		byte[] withBom = concat(new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF },
				"name,age\nada,36\n".getBytes(StandardCharsets.UTF_8));

		CsvData data = CsvLoader.load(new StringResource("people.csv", withBom), null, null, new AtomicBoolean());

		assertEquals("name", data.columnName(0));
	}

	@Test
	void readsUtf16WithItsByteOrderMark() throws Exception {

		byte[] utf16 = concat(new byte[] { (byte) 0xFF, (byte) 0xFE },
				"name,city\nada,london\n".getBytes(StandardCharsets.UTF_16LE));

		CsvData data = CsvLoader.load(new StringResource("people.csv", utf16), null, null, new AtomicBoolean());

		assertEquals(List.of("name", "city"), data.columns());
		assertEquals("london", data.value(0, 1));
	}

	@Test
	void refusesBinaryContentSoAnotherViewerCanHaveIt() {

		byte[] binary = { 'M', 'Z', 0, 0, 1, 2, 3, 0 };

		assertThrows(CsvLoader.NotTabularException.class,
				() -> CsvLoader.load(new StringResource("odd.csv", binary), null, null, new AtomicBoolean()));
	}

	@Test
	void returnsNullWhenTheLoadIsCancelled() throws Exception {

		AtomicBoolean cancelled = new AtomicBoolean(true);

		assertNull(CsvLoader.load(new StringResource("people.csv", "name\nada\n"), null, null, cancelled));
	}

	@Test
	void stopsAtTheRowCapAndSaysSo() throws Exception {

		StringBuilder content = new StringBuilder("name,value\n");
		for (int i = 0; i < CsvLoader.MAX_ROWS + 100; i++) {
			content.append("row").append(i).append(',').append(i).append('\n');
		}

		CsvData data = load("big.csv", content.toString());

		assertTrue(data.isTruncated());
		assertEquals(CsvLoader.MAX_ROWS, data.rowCount());
	}

	@Test
	void keepsAtMostTheColumnCap() throws Exception {

		String header = String.join(",", java.util.Collections.nCopies(CsvLoader.MAX_COLUMNS + 20, "c"));

		CsvData data = load("wide.csv", header + "\n1\n");

		assertEquals(CsvLoader.MAX_COLUMNS, data.columnCount());
	}

	@Test
	void readsAnEmptyFileAsAnEmptyTable() throws Exception {

		CsvData data = load("empty.csv", "");

		assertTrue(data.isEmpty());
		assertEquals(0, data.columnCount());
	}

	private static CsvData load(String name, String content) throws Exception {
		return CsvLoader.load(new StringResource(name, content), null, null, new AtomicBoolean());
	}

	private static byte[] concat(byte[] first, byte[] second) {

		byte[] joined = new byte[first.length + second.length];
		System.arraycopy(first, 0, joined, 0, first.length);
		System.arraycopy(second, 0, joined, first.length, second.length);

		return joined;
	}
}
