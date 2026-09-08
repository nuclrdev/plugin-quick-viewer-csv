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

/**
 * How many rows go on a page, and which rows a given page holds.
 *
 * <p>The default page is the one that fits: a quick view is a side panel of
 * unpredictable height, and a page that ends where the visible area ends is the
 * one that never asks the user to scroll and page at the same time. The user
 * can still pick a fixed size, which is what makes "page 4" mean the same thing
 * from one session to the next.
 */
final class Paging {

	/** Fixed sizes offered next to the fitted one. */
	static final int[] CHOICES = { 25, 50, 100, 250, 500, 1000 };

	/** Value standing for "as many as fit"; not a row count. */
	static final int FIT = 0;

	/** Below this a page is mostly chrome, so a very short panel still gets this many. */
	static final int MIN_FITTED = 5;

	/** Above this the grid gets slow to lay out for no gain: nobody scans 500 rows by eye. */
	static final int MAX_FITTED = 500;

	private Paging() {
	}

	/**
	 * How many rows fit in the space the grid actually has.
	 *
	 * @param viewportHeight height available for rows, in pixels
	 * @param rowHeight      height of one row, in pixels
	 * @return the row count, clamped to something usable
	 */
	static int fit(int viewportHeight, int rowHeight) {

		if (rowHeight <= 0) {
			return MIN_FITTED;
		}

		int rows = viewportHeight / rowHeight;
		return Math.max(MIN_FITTED, Math.min(MAX_FITTED, rows));
	}

	/**
	 * The effective page size.
	 *
	 * @param choice         {@link #FIT} or a fixed size
	 * @param viewportHeight height available for rows, in pixels
	 * @param rowHeight      height of one row, in pixels
	 * @return rows per page, always at least one
	 */
	static int pageSize(int choice, int viewportHeight, int rowHeight) {
		return choice == FIT ? fit(viewportHeight, rowHeight) : Math.max(1, choice);
	}

	/** How many pages {@code rowCount} rows make; always at least one, so "1 of 1" shows for an empty file. */
	static int pageCount(int rowCount, int pageSize) {

		if (pageSize <= 0) {
			return 1;
		}

		return Math.max(1, (rowCount + pageSize - 1) / pageSize);
	}

	/** Keeps a page number inside the file after the filter shortened it. */
	static int clampPage(int page, int pageCount) {
		return Math.max(0, Math.min(page, Math.max(0, pageCount - 1)));
	}

	/** Index of the first row on a page. */
	static int firstRow(int page, int pageSize) {
		return Math.max(0, page) * pageSize;
	}

	/** Index just past the last row on a page, bounded by the row count. */
	static int endRow(int page, int pageSize, int rowCount) {
		return Math.min(rowCount, firstRow(page, pageSize) + pageSize);
	}

	/** The page a given row falls on, which is how the find bar jumps to a match. */
	static int pageOf(int row, int pageSize) {
		return pageSize <= 0 ? 0 : Math.max(0, row) / pageSize;
	}
}
