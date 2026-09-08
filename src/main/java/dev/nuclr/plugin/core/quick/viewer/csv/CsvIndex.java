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

import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The view onto a file: which rows the filter kept, in which order the sort put
 * them. Both are expressed as an array of row indices into {@link CsvData}, so
 * the file itself is never copied or reordered, and a page is a slice of that
 * array.
 *
 * <p>Filtering and sorting run over every row, so they belong on a background
 * thread; nothing here touches Swing.
 */
final class CsvIndex {

	private CsvIndex() {
	}

	/** Every row, in file order - the view when nothing is filtered. */
	static int[] all(CsvData data) {

		int[] rows = new int[data.rowCount()];
		for (int i = 0; i < rows.length; i++) {
			rows[i] = i;
		}

		return rows;
	}

	/**
	 * The rows that match, in file order.
	 *
	 * @param data      the file
	 * @param matcher   the compiled filter, or {@code null} to keep every row
	 * @param cancelled checked as it goes; when set, the scan stops
	 * @return the matching row indices, or {@code null} if cancelled
	 */
	static int[] filter(CsvData data, RowMatcher matcher, AtomicBoolean cancelled) {

		if (matcher == null) {
			return all(data);
		}

		int rowCount = data.rowCount();
		int[] matches = new int[rowCount];
		int found = 0;

		for (int row = 0; row < rowCount; row++) {
			if ((row & 0x3FF) == 0 && cancelled != null && cancelled.get()) {
				return null;
			}
			if (matcher.matches(data, row)) {
				matches[found++] = row;
			}
		}

		return Arrays.copyOf(matches, found);
	}

	/**
	 * Orders a view by one column, in place.
	 *
	 * <p>Numbers sort as numbers when the column holds them - the whole point of
	 * clicking a header on a price column - and text sorts case-insensitively.
	 * Empty cells stay at the bottom in both directions, the way a spreadsheet
	 * keeps blanks out of the way.
	 *
	 * @param view       the row indices to order, modified in place
	 * @param data       the file the indices point into
	 * @param column     the column to order by, or {@code -1} to restore file order
	 * @param descending whether to reverse the order
	 */
	static void sort(int[] view, CsvData data, int column, boolean descending) {

		if (view == null || view.length < 2) {
			return;
		}

		if (column < 0) {
			Arrays.sort(view);
			if (descending) {
				reverse(view);
			}
			return;
		}

		Comparator<Integer> comparator = comparator(data, column, descending);

		// Boxing to reach a stable, comparator-driven sort. It costs one Integer per
		// row for the length of the sort, which is far less than the row data itself
		// and buys the tie-break behaviour a hand-rolled primitive sort would have to
		// re-implement.
		Integer[] boxed = new Integer[view.length];
		for (int i = 0; i < view.length; i++) {
			boxed[i] = view[i];
		}

		Arrays.sort(boxed, comparator);

		for (int i = 0; i < view.length; i++) {
			view[i] = boxed[i];
		}
	}

	private static Comparator<Integer> comparator(CsvData data, int column, boolean descending) {

		boolean numeric = data.isNumericColumn(column);

		return (left, right) -> {

			String a = data.value(left, column);
			String b = data.value(right, column);

			boolean emptyA = a.isBlank();
			boolean emptyB = b.isBlank();
			if (emptyA || emptyB) {
				// Outside the direction flip on purpose: blanks are absent values, not
				// small ones, so they belong last however the column is sorted.
				return emptyA && emptyB ? 0 : (emptyA ? 1 : -1);
			}

			int result;
			if (numeric) {
				double x = Numbers.parse(a);
				double y = Numbers.parse(b);
				if (Double.isNaN(x) || Double.isNaN(y)) {
					// A stray label in a number column: keep it next to the numbers rather
					// than pretending it is one.
					result = Double.isNaN(x) && Double.isNaN(y) ? compareText(a, b) : (Double.isNaN(x) ? 1 : -1);
				} else {
					result = Double.compare(x, y);
				}
			} else {
				result = compareText(a, b);
			}

			return descending ? -result : result;
		};
	}

	private static int compareText(String a, String b) {

		int result = String.CASE_INSENSITIVE_ORDER.compare(a, b);
		// Same letters, different case: order them anyway so the sort is reproducible.
		return result != 0 ? result : a.compareTo(b);
	}

	private static void reverse(int[] view) {

		for (int i = 0, j = view.length - 1; i < j; i++, j--) {
			int swap = view[i];
			view[i] = view[j];
			view[j] = swap;
		}
	}
}
