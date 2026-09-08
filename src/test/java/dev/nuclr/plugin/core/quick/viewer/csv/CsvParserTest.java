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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class CsvParserTest {

	@Test
	void readsPlainRecords() throws IOException {

		List<List<String>> records = parse("a,b,c\n1,2,3\n", ',');

		assertEquals(2, records.size());
		assertEquals(List.of("a", "b", "c"), records.get(0));
		assertEquals(List.of("1", "2", "3"), records.get(1));
	}

	@Test
	void keepsDelimitersAndNewlinesInsideQuotes() throws IOException {

		List<List<String>> records = parse("\"a,b\",\"line1\nline2\",c\n", ',');

		assertEquals(1, records.size());
		assertEquals(List.of("a,b", "line1\nline2", "c"), records.get(0));
	}

	@Test
	void unescapesDoubledQuotes() throws IOException {
		assertEquals(List.of("say \"hi\"", "x"), parse("\"say \"\"hi\"\"\",x\n", ',').get(0));
	}

	@Test
	void treatsAQuoteInsideAnUnquotedFieldAsText() throws IOException {
		assertEquals(List.of("6\" pipe", "x"), parse("6\" pipe,x\n", ',').get(0));
	}

	@Test
	void closesAnUnterminatedQuoteAtEndOfInput() throws IOException {
		assertEquals(List.of("a", "runs off"), parse("a,\"runs off", ',').get(0));
	}

	@Test
	void handlesCarriageReturnLineEndings() throws IOException {

		assertEquals(2, parse("a,b\r\n1,2\r\n", ',').size());
		assertEquals(2, parse("a,b\r1,2\r", ',').size());
	}

	@Test
	void doesNotInventARecordForTheTrailingNewline() throws IOException {
		assertEquals(1, parse("a,b\n", ',').size());
	}

	@Test
	void keepsEmptyFields() throws IOException {
		assertEquals(List.of("", "", ""), parse(",,\n", ',').get(0));
	}

	@Test
	void readsSemicolonAndTabSeparatedRecords() throws IOException {

		assertEquals(List.of("a", "b"), parse("a;b\n", ';').get(0));
		assertEquals(List.of("a", "b"), parse("a\tb\n", '\t').get(0));
	}

	@Test
	void returnsNullOnAnEmptyInput() throws IOException {

		try (CsvParser parser = new CsvParser(new StringReader(""), ',', '"')) {
			assertNull(parser.next());
		}
	}

	@Test
	void cutsACellThatWouldNotFitInMemory() throws IOException {

		String huge = "x".repeat(CsvParser.MAX_CELL_CHARS + 500);

		try (CsvParser parser = new CsvParser(new StringReader(huge + ",b\n"), ',', '"')) {
			List<String> record = parser.next();
			assertEquals(CsvParser.MAX_CELL_CHARS, record.get(0).length());
			assertEquals("b", record.get(1));
			assertTrue(parser.cellTruncated());
		}
	}

	@Test
	void countsTheCharactersItConsumed() throws IOException {

		String content = "a,b\n1,2\n";

		try (CsvParser parser = new CsvParser(new StringReader(content), ',', '"')) {
			while (parser.next() != null) {
				// Drain it.
			}
			assertEquals(content.length(), parser.charsConsumed());
		}
	}

	private static List<List<String>> parse(String content, char delimiter) throws IOException {

		List<List<String>> records = new ArrayList<>();

		try (CsvParser parser = new CsvParser(new StringReader(content), delimiter, '"')) {
			List<String> record;
			while ((record = parser.next()) != null) {
				records.add(record);
			}
		}

		return records;
	}
}
