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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Ctrl+F over the rows currently in view.
 *
 * <p>Every hit is found up front rather than one at a time, because the useful
 * part of a find bar in a paged grid is "3 of 27" and jumping straight to the
 * page a match is on - both of which need the whole list. A hit is a cell, not
 * a row, so pressing Enter walks across a row that matches twice.
 */
final class CsvSearch {

	/** Hits kept. Past it the query is not a search, and the list itself would cost more than the file. */
	static final int MAX_HITS = 200_000;

	private CsvSearch() {
	}

	/**
	 * Where the matches are.
	 *
	 * <p>Positions are packed two-per-{@code long} - view row in the high half,
	 * column in the low half - so a file-sized result is one array and no
	 * objects.
	 *
	 * @param positions the packed hits, in view order
	 * @param capped    whether searching stopped at {@link #MAX_HITS}
	 */
	record Hits(long[] positions, boolean capped) {

		static final Hits NONE = new Hits(new long[0], false);

		int size() {
			return positions.length;
		}

		boolean isEmpty() {
			return positions.length == 0;
		}

		/** The row, as an index into the view (not into the file). */
		int viewRow(int hit) {
			return (int) (positions[hit] >>> 32);
		}

		int column(int hit) {
			return (int) positions[hit];
		}

		/**
		 * The first hit at or after a position, for Enter; wraps to the start.
		 *
		 * @param viewRow the row to search from
		 * @param column  the column to search from within that row
		 * @return the hit index, or {@code -1} when there are no hits at all
		 */
		int next(int viewRow, int column) {

			if (isEmpty()) {
				return -1;
			}

			long from = pack(viewRow, column);
			for (int i = 0; i < positions.length; i++) {
				if (positions[i] > from) {
					return i;
				}
			}

			return 0;
		}

		/**
		 * The last hit before a position, for Shift+Enter; wraps to the end.
		 *
		 * @param viewRow the row to search back from
		 * @param column  the column to search back from within that row
		 * @return the hit index, or {@code -1} when there are no hits at all
		 */
		int previous(int viewRow, int column) {

			if (isEmpty()) {
				return -1;
			}

			long from = pack(viewRow, column);
			for (int i = positions.length - 1; i >= 0; i--) {
				if (positions[i] < from) {
					return i;
				}
			}

			return positions.length - 1;
		}

		/** Whether a cell is one of the hits - what the renderer asks to paint the highlight. */
		boolean contains(int viewRow, int column) {
			return !isEmpty() && Arrays.binarySearch(positions, pack(viewRow, column)) >= 0;
		}

		private static long pack(int viewRow, int column) {
			return ((long) viewRow << 32) | (column & 0xFFFFFFFFL);
		}
	}

	/**
	 * Finds every cell that matches, over the rows in view and in view order.
	 *
	 * @param data      the file
	 * @param view      the row indices currently shown, in display order
	 * @param matcher   the compiled query, or {@code null} for no query
	 * @param cancelled checked as it goes; when set, the scan stops
	 * @return the hits, or {@code null} if cancelled
	 */
	static Hits find(CsvData data, int[] view, TextMatcher matcher, AtomicBoolean cancelled) {

		if (matcher == null || view == null || view.length == 0) {
			return Hits.NONE;
		}

		int columnCount = data.columnCount();
		long[] positions = new long[Math.min(1024, Math.max(16, view.length))];
		int found = 0;
		boolean capped = false;

		for (int viewRow = 0; viewRow < view.length && !capped; viewRow++) {

			if ((viewRow & 0x3FF) == 0 && cancelled != null && cancelled.get()) {
				return null;
			}

			int row = view[viewRow];
			for (int column = 0; column < columnCount; column++) {
				if (!matcher.matches(data.value(row, column))) {
					continue;
				}
				if (found == positions.length) {
					positions = Arrays.copyOf(positions, Math.min(MAX_HITS, positions.length * 2));
				}
				positions[found++] = Hits.pack(viewRow, column);
				if (found >= MAX_HITS) {
					capped = true;
					break;
				}
			}
		}

		return new Hits(Arrays.copyOf(positions, found), capped);
	}
}
