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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What the user has typed into the filter fields.
 *
 * <p>The general query matches a row when <em>any</em> of its cells match; a
 * column query constrains that one column. Everything is combined with AND, so
 * narrowing by typing into a second field always shows fewer rows, never more.
 *
 * @param query         the general query applied across all columns
 * @param columnQueries per-column queries, keyed by column index
 * @param regex         whether the queries are regular expressions
 * @param caseSensitive whether case matters
 */
record FilterSpec(String query, Map<Integer, String> columnQueries, boolean regex, boolean caseSensitive) {

	static final FilterSpec NONE = new FilterSpec("", Map.of(), false, false);

	FilterSpec {
		query = query == null ? "" : query;
		columnQueries = columnQueries == null ? Map.of() : Map.copyOf(columnQueries);
	}

	/** Whether nothing is filtered out, in which case the view is the file itself. */
	boolean isEmpty() {

		if (!query.isBlank()) {
			return false;
		}

		for (String columnQuery : columnQueries.values()) {
			if (columnQuery != null && !columnQuery.isBlank()) {
				return false;
			}
		}

		return true;
	}

	FilterSpec withQuery(String newQuery) {
		return new FilterSpec(newQuery, columnQueries, regex, caseSensitive);
	}

	/** Sets (or, with a blank query, clears) the filter on one column. */
	FilterSpec withColumnQuery(int column, String columnQuery) {

		Map<Integer, String> updated = new LinkedHashMap<>(columnQueries);
		if (columnQuery == null || columnQuery.isBlank()) {
			updated.remove(column);
		} else {
			updated.put(column, columnQuery);
		}

		return new FilterSpec(query, updated, regex, caseSensitive);
	}

	FilterSpec withRegex(boolean newRegex) {
		return new FilterSpec(query, columnQueries, newRegex, caseSensitive);
	}

	FilterSpec withCaseSensitive(boolean newCaseSensitive) {
		return new FilterSpec(query, columnQueries, regex, newCaseSensitive);
	}

	/** Drops the column queries, which is what a newly opened file needs. */
	FilterSpec withoutColumnQueries() {
		return new FilterSpec(query, Map.of(), regex, caseSensitive);
	}
}
