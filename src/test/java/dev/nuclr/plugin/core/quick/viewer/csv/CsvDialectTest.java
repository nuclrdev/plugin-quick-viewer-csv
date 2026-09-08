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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class CsvDialectTest {

	@Test
	void sniffsCommas() {
		assertEquals(',', CsvDialect.sniff("name,age,city\nada,36,london\nalan,41,wilmslow\n", "people.csv").delimiter());
	}

	@Test
	void sniffsSemicolonsEvenWhenTheValuesContainCommas() {
		assertEquals(';', CsvDialect.sniff("name;price;note\nbolt;1,50;short, thick\nnut;0,80;small, round\n",
				"parts.csv").delimiter());
	}

	@Test
	void sniffsTabsAndPipes() {

		assertEquals('\t', CsvDialect.sniff("a\tb\tc\n1\t2\t3\n4\t5\t6\n", "data.csv").delimiter());
		assertEquals('|', CsvDialect.sniff("a|b|c\n1|2|3\n4|5|6\n", "data.csv").delimiter());
	}

	@Test
	void trustsTheTsvExtensionOverTheContent() {
		assertEquals('\t', CsvDialect.sniff("one, two and three\tfour\n", "notes.tsv").delimiter());
	}

	@Test
	void fallsBackToCommaForContentWithNoSeparatorAtAll() {

		assertEquals(',', CsvDialect.sniff("just one long line of prose\nand another\n", "notes.csv").delimiter());
		assertEquals(',', CsvDialect.sniff("", "empty.csv").delimiter());
		assertEquals(',', CsvDialect.sniff(null, null).delimiter());
	}

	@Test
	void namesItsSeparator() {

		assertEquals("Comma", CsvDialect.of(',').delimiterName());
		assertEquals("Semicolon", CsvDialect.of(';').delimiterName());
		assertEquals("Tab", CsvDialect.of('\t').delimiterName());
		assertEquals("Pipe", CsvDialect.of('|').delimiterName());
	}

	@Test
	void readsARowOfDistinctWordsAsAHeader() {
		assertTrue(CsvDialect.looksLikeHeader(List.of("name", "age", "city")));
	}

	@Test
	void rejectsARowThatHoldsNumbers() {
		assertFalse(CsvDialect.looksLikeHeader(List.of("ada", "36", "london")));
	}

	@Test
	void rejectsARowWithAnEmptyOrRepeatedTitle() {

		assertFalse(CsvDialect.looksLikeHeader(List.of("name", "", "city")));
		assertFalse(CsvDialect.looksLikeHeader(List.of("name", "city", "name")));
		assertFalse(CsvDialect.looksLikeHeader(List.of()));
		assertFalse(CsvDialect.looksLikeHeader(null));
	}
}
