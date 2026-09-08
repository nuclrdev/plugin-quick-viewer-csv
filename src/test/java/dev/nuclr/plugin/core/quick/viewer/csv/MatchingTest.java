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

import java.util.regex.PatternSyntaxException;

import org.junit.jupiter.api.Test;

class MatchingTest {

	@Test
	void plainTextMatchesAnywhereInTheCellAndIgnoresCaseByDefault() {

		TextMatcher matcher = TextMatcher.compile("lon", false, false);

		assertTrue(matcher.matches("London"));
		assertTrue(matcher.matches("BABYLON"));
		assertFalse(matcher.matches("Paris"));
	}

	@Test
	void honoursCaseWhenAskedTo() {

		TextMatcher matcher = TextMatcher.compile("Lon", false, true);

		assertTrue(matcher.matches("London"));
		assertFalse(matcher.matches("london"));
	}

	@Test
	void treatsARegularExpressionAsASubstringSearchUnlessItIsAnchored() {

		assertTrue(TextMatcher.compile("l.n", true, false).matches("London"));
		assertTrue(TextMatcher.compile("^Lon", true, false).matches("London"));
		assertFalse(TextMatcher.compile("^ndo", true, false).matches("London"));
	}

	@Test
	void reportsWhereAMatchStartsAndHowLongItIs() {

		TextMatcher matcher = TextMatcher.compile("don", false, false);

		assertEquals(3, matcher.indexIn("London", 0));
		assertEquals(-1, matcher.indexIn("London", 4));
		assertEquals(3, matcher.matchLength("London", 3));

		TextMatcher regex = TextMatcher.compile("o[a-z]+", true, false);
		assertEquals(1, regex.indexIn("London", 0));
		assertEquals(5, regex.matchLength("London", 1));
	}

	@Test
	void treatsABlankQueryAsNoConstraint() {

		assertNull(TextMatcher.compile("", false, false));
		assertNull(TextMatcher.compile(null, true, true));
		assertNull(RowMatcher.compile(FilterSpec.NONE));
		assertNull(RowMatcher.compile(FilterSpec.NONE.withQuery("   ").withColumnQuery(0, "  ")));
	}

	@Test
	void reportsAnUnfinishedRegularExpression() {
		assertThrows(PatternSyntaxException.class, () -> TextMatcher.compile("(unclosed", true, false));
	}

	@Test
	void aColumnQueryConstrainsOnlyItsColumn() {

		CsvData data = TestTables.of("""
				name,city
				ada,London
				london,Paris
				""");

		RowMatcher matcher = RowMatcher.compile(FilterSpec.NONE.withColumnQuery(1, "london"));

		assertTrue(matcher.matches(data, 0));
		assertFalse(matcher.matches(data, 1));
	}

	@Test
	void aGeneralQueryLooksAtEveryColumn() {

		CsvData data = TestTables.of("""
				name,city
				ada,London
				alan,Paris
				""");

		RowMatcher matcher = RowMatcher.compile(FilterSpec.NONE.withQuery("paris"));

		assertFalse(matcher.matches(data, 0));
		assertTrue(matcher.matches(data, 1));
	}

	@Test
	void clearingAColumnQueryRemovesIt() {

		FilterSpec spec = FilterSpec.NONE.withColumnQuery(1, "x");
		assertFalse(spec.isEmpty());

		assertTrue(spec.withColumnQuery(1, "").isEmpty());
		assertTrue(spec.withoutColumnQueries().isEmpty());
	}
}
