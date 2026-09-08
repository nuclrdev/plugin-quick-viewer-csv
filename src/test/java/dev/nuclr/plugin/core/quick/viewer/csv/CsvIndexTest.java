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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

class CsvIndexTest {

	private static final String PEOPLE = """
			name,age,city
			ada,36,London
			alan,41,Wilmslow
			grace,45,New York
			edsger,72,Austin
			""";

	@Test
	void keepsEveryRowWhenThereIsNoFilter() {

		CsvData data = TestTables.of(PEOPLE);

		assertArrayEquals(new int[] { 0, 1, 2, 3 }, CsvIndex.filter(data, null, new AtomicBoolean()));
	}

	@Test
	void keepsTheRowsAnyColumnMatches() {

		CsvData data = TestTables.of(PEOPLE);
		RowMatcher matcher = RowMatcher.compile(FilterSpec.NONE.withQuery("an"));

		// "alan" by name, "New York" has none - Wilmslow does not match, Austin does not.
		assertArrayEquals(new int[] { 1 }, CsvIndex.filter(data, matcher, new AtomicBoolean()));
	}

	@Test
	void combinesColumnFiltersWithAnd() {

		CsvData data = TestTables.of(PEOPLE);
		FilterSpec spec = FilterSpec.NONE.withColumnQuery(2, "o").withQuery("grace");

		int[] rows = CsvIndex.filter(data, RowMatcher.compile(spec), new AtomicBoolean());

		// Three cities hold an "o"; only one of those rows also holds "grace".
		assertArrayEquals(new int[] { 2 }, rows);
	}

	@Test
	void stopsWhenCancelled() {

		CsvData data = TestTables.of(PEOPLE);
		RowMatcher matcher = RowMatcher.compile(FilterSpec.NONE.withQuery("a"));

		assertNull(CsvIndex.filter(data, matcher, new AtomicBoolean(true)));
	}

	@Test
	void sortsANumericColumnAsNumbers() {

		CsvData data = TestTables.of("""
				item,qty
				a,9
				b,10
				c,2
				""");

		int[] view = CsvIndex.all(data);
		CsvIndex.sort(view, data, 1, false);

		assertEquals(List.of("2", "9", "10"), values(data, view, 1));
	}

	@Test
	void sortsTextCaseInsensitivelyAndReversesOnDescending() {

		CsvData data = TestTables.of("""
				name
				delta
				Alpha
				charlie
				""");

		int[] ascending = CsvIndex.all(data);
		CsvIndex.sort(ascending, data, 0, false);
		assertEquals(List.of("Alpha", "charlie", "delta"), values(data, ascending, 0));

		int[] descending = CsvIndex.all(data);
		CsvIndex.sort(descending, data, 0, true);
		assertEquals(List.of("delta", "charlie", "Alpha"), values(data, descending, 0));
	}

	@Test
	void keepsBlankCellsLastInBothDirections() {

		CsvData data = TestTables.of("""
				name,note
				a,
				b,zeta
				c,alpha
				""");

		int[] ascending = CsvIndex.all(data);
		CsvIndex.sort(ascending, data, 1, false);
		assertEquals("", data.value(ascending[2], 1));

		int[] descending = CsvIndex.all(data);
		CsvIndex.sort(descending, data, 1, true);
		assertEquals("", data.value(descending[2], 1));
	}

	@Test
	void restoresFileOrderWhenNoColumnIsGiven() {

		CsvData data = TestTables.of(PEOPLE);

		int[] view = CsvIndex.all(data);
		CsvIndex.sort(view, data, 0, false);
		CsvIndex.sort(view, data, -1, false);

		assertArrayEquals(new int[] { 0, 1, 2, 3 }, view);
	}

	@Test
	void sortsEqualValuesStablySoAPagedGridDoesNotShuffle() {

		CsvData data = TestTables.of("""
				group,name
				x,first
				x,second
				x,third
				""");

		int[] view = CsvIndex.all(data);
		CsvIndex.sort(view, data, 0, false);

		assertEquals(List.of("first", "second", "third"), values(data, view, 1));
	}

	private static List<String> values(CsvData data, int[] view, int column) {

		List<String> values = new ArrayList<>(view.length);
		for (int row : view) {
			values.add(data.value(row, column));
		}

		return values;
	}
}
