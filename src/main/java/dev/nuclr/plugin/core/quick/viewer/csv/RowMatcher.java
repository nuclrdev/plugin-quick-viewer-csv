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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A {@link FilterSpec} compiled against a file: the general query plus one
 * matcher per filtered column, ready to be run over every row.
 */
final class RowMatcher {

	private final TextMatcher general;
	private final int[] columns;
	private final TextMatcher[] columnMatchers;

	private RowMatcher(TextMatcher general, int[] columns, TextMatcher[] columnMatchers) {
		this.general = general;
		this.columns = columns;
		this.columnMatchers = columnMatchers;
	}

	/**
	 * Compiles a filter.
	 *
	 * @param spec what the user typed
	 * @return the matcher, or {@code null} when the filter constrains nothing
	 * @throws java.util.regex.PatternSyntaxException if regex mode is on and an
	 *                                                expression is not valid
	 */
	static RowMatcher compile(FilterSpec spec) {

		if (spec == null || spec.isEmpty()) {
			return null;
		}

		TextMatcher general = TextMatcher.compile(spec.query().isBlank() ? null : spec.query(),
				spec.regex(), spec.caseSensitive());

		List<Integer> columns = new ArrayList<>();
		List<TextMatcher> matchers = new ArrayList<>();

		for (Map.Entry<Integer, String> entry : spec.columnQueries().entrySet()) {
			TextMatcher matcher = TextMatcher.compile(entry.getValue(), spec.regex(), spec.caseSensitive());
			if (matcher != null) {
				columns.add(entry.getKey());
				matchers.add(matcher);
			}
		}

		if (general == null && columns.isEmpty()) {
			return null;
		}

		int[] columnIndices = new int[columns.size()];
		for (int i = 0; i < columnIndices.length; i++) {
			columnIndices[i] = columns.get(i);
		}

		return new RowMatcher(general, columnIndices, matchers.toArray(new TextMatcher[0]));
	}

	/**
	 * Whether a row survives the filter.
	 *
	 * @param data the file
	 * @param row  the row index within it
	 * @return {@code true} when every column query and the general query match
	 */
	boolean matches(CsvData data, int row) {

		for (int i = 0; i < columns.length; i++) {
			if (!columnMatchers[i].matches(data.value(row, columns[i]))) {
				return false;
			}
		}

		if (general == null) {
			return true;
		}

		int columnCount = data.columnCount();
		for (int column = 0; column < columnCount; column++) {
			if (general.matches(data.value(row, column))) {
				return true;
			}
		}

		return false;
	}
}
