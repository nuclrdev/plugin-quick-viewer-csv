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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvExporterTest {

	private static final String TABLE = """
			name,city
			ada,London
			alan,Wilmslow
			grace,New York
			""";

	@Test
	void writesTheHeaderAndTheChosenRowsInTheOrderGiven() {

		CsvData data = TestTables.of(TABLE);

		String csv = CsvExporter.toText(data, new int[] { 2, 0 }, true, ',');

		assertEquals("name,city\r\ngrace,New York\r\nada,London\r\n", csv);
	}

	@Test
	void leavesTheHeaderOutWhenAsked() {

		CsvData data = TestTables.of(TABLE);

		assertEquals("ada,London\r\n", CsvExporter.toText(data, new int[] { 0 }, false, ','));
	}

	@Test
	void writesWithTheSeparatorItIsGiven() {

		CsvData data = TestTables.of(TABLE);

		assertEquals("ada;London\r\n", CsvExporter.toText(data, new int[] { 0 }, false, ';'));
	}

	@Test
	void padsShortRowsSoEveryLineHasEveryColumn() {

		CsvData data = TestTables.of("""
				a,b,c
				1,2,3
				4
				""");

		assertEquals("4,,\r\n", CsvExporter.toText(data, new int[] { 1 }, false, ','));
	}

	@Test
	void quotesOnlyWhatNeedsIt() {

		assertEquals("plain", CsvExporter.escape("plain", ','));
		assertEquals("\"a,b\"", CsvExporter.escape("a,b", ','));
		assertEquals("\"say \"\"hi\"\"\"", CsvExporter.escape("say \"hi\"", ','));
		assertEquals("\"two\nlines\"", CsvExporter.escape("two\nlines", ','));
		assertEquals("a,b", CsvExporter.escape("a,b", ';'));
		assertEquals("", CsvExporter.escape(null, ','));
	}

	@Test
	void roundTripsThroughTheParser() {

		// Not a text block: the quoting this is about would close one.
		CsvData data = TestTables.of("name,note\n"
				+ "ada,\"a note, with a comma\"\n"
				+ "alan,\"he said \"\"hello\"\"\"\n");

		String exported = CsvExporter.toText(data, CsvIndex.all(data), true, ',');
		CsvData reloaded = TestTables.of(exported);

		assertEquals(data.rowCount(), reloaded.rowCount());
		assertEquals("a note, with a comma", reloaded.value(0, 1));
		assertEquals("he said \"hello\"", reloaded.value(1, 1));
	}

	@Test
	void writesAFileAsUtf8(@TempDir Path directory) throws Exception {

		CsvData data = TestTables.of("name,city\nadaß,Köln\n");
		Path file = directory.resolve("export.csv");

		CsvExporter.writeFile(file, data, CsvIndex.all(data), true, ',');

		String written = Files.readString(file, StandardCharsets.UTF_8);
		assertTrue(written.contains("adaß,Köln"), written);
		assertTrue(written.startsWith("name,city\r\n"), written);
	}
}
