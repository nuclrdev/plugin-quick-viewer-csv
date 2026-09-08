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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

class CsvSearchTest {

	private static final String TABLE = """
			name,city
			ada,London
			alan,Wilmslow
			grace,London
			""";

	@Test
	void findsEveryMatchingCellInViewOrder() {

		CsvData data = TestTables.of(TABLE);
		CsvSearch.Hits hits = find(data, CsvIndex.all(data), "london");

		assertEquals(2, hits.size());
		assertEquals(0, hits.viewRow(0));
		assertEquals(1, hits.column(0));
		assertEquals(2, hits.viewRow(1));
	}

	@Test
	void findsSeveralMatchesWithinOneRow() {

		CsvData data = TestTables.of("""
				a,b
				same,same
				""");

		CsvSearch.Hits hits = find(data, CsvIndex.all(data), "same");

		assertEquals(2, hits.size());
		assertEquals(0, hits.column(0));
		assertEquals(1, hits.column(1));
	}

	@Test
	void walksForwardsAndBackwardsAndWrapsAround() {

		CsvData data = TestTables.of(TABLE);
		CsvSearch.Hits hits = find(data, CsvIndex.all(data), "london");

		int first = hits.next(-1, Integer.MAX_VALUE);
		assertEquals(0, first);

		int second = hits.next(hits.viewRow(first), hits.column(first));
		assertEquals(1, second);

		// Past the last hit it comes back to the first, and back past the first to the last.
		assertEquals(0, hits.next(hits.viewRow(second), hits.column(second)));
		assertEquals(1, hits.previous(hits.viewRow(first), hits.column(first)));
	}

	@Test
	void reportsWhichCellsToHighlight() {

		CsvData data = TestTables.of(TABLE);
		CsvSearch.Hits hits = find(data, CsvIndex.all(data), "london");

		assertTrue(hits.contains(0, 1));
		assertFalse(hits.contains(0, 0));
		assertFalse(hits.contains(1, 1));
	}

	@Test
	void searchesTheFilteredViewOnly() {

		CsvData data = TestTables.of(TABLE);
		int[] view = CsvIndex.filter(data, RowMatcher.compile(FilterSpec.NONE.withQuery("grace")), new AtomicBoolean());

		CsvSearch.Hits hits = find(data, view, "london");

		assertEquals(1, hits.size());
		// The one row in view is view row 0, whatever its number in the file.
		assertEquals(0, hits.viewRow(0));
	}

	@Test
	void hasNoHitsWithoutAQuery() {

		CsvData data = TestTables.of(TABLE);

		assertTrue(CsvSearch.find(data, CsvIndex.all(data), null, new AtomicBoolean()).isEmpty());
		assertEquals(-1, CsvSearch.Hits.NONE.next(0, 0));
		assertEquals(-1, CsvSearch.Hits.NONE.previous(0, 0));
	}

	@Test
	void stopsWhenCancelled() {

		CsvData data = TestTables.of(TABLE);

		assertNull(CsvSearch.find(data, CsvIndex.all(data), TextMatcher.compile("a", false, false),
				new AtomicBoolean(true)));
	}

	private static CsvSearch.Hits find(CsvData data, int[] view, String query) {
		return CsvSearch.find(data, view, TextMatcher.compile(query, false, false), new AtomicBoolean());
	}
}
