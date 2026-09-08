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

import org.junit.jupiter.api.Test;

class PagingTest {

	@Test
	void fitsAsManyRowsAsTheHeightAllows() {
		assertEquals(30, Paging.fit(600, 20));
	}

	@Test
	void keepsTheFittedPageUsableInAVeryShortOrVeryTallPane() {

		assertEquals(Paging.MIN_FITTED, Paging.fit(10, 20));
		assertEquals(Paging.MIN_FITTED, Paging.fit(600, 0));
		assertEquals(Paging.MAX_FITTED, Paging.fit(100_000, 20));
	}

	@Test
	void usesTheFixedSizeWhenOneIsChosen() {

		assertEquals(100, Paging.pageSize(100, 600, 20));
		assertEquals(30, Paging.pageSize(Paging.FIT, 600, 20));
	}

	@Test
	void countsPagesRoundingUp() {

		assertEquals(1, Paging.pageCount(0, 25));
		assertEquals(1, Paging.pageCount(25, 25));
		assertEquals(2, Paging.pageCount(26, 25));
		assertEquals(4, Paging.pageCount(100, 30));
	}

	@Test
	void clampsAPageIntoRange() {

		assertEquals(0, Paging.clampPage(-3, 5));
		assertEquals(4, Paging.clampPage(99, 5));
		assertEquals(2, Paging.clampPage(2, 5));
	}

	@Test
	void slicesAPageAndStopsAtTheLastRow() {

		assertEquals(50, Paging.firstRow(2, 25));
		assertEquals(75, Paging.endRow(2, 25, 200));
		assertEquals(60, Paging.endRow(2, 25, 60));
	}

	@Test
	void findsThePageARowIsOn() {

		assertEquals(0, Paging.pageOf(24, 25));
		assertEquals(1, Paging.pageOf(25, 25));
		assertEquals(400, Paging.pageOf(10_000, 25));
	}
}
