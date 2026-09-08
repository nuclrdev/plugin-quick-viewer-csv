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

import org.junit.jupiter.api.Test;

class NumbersTest {

	@Test
	void readsPlainNumbers() {

		assertEquals(42, Numbers.parse("42"));
		assertEquals(-3.5, Numbers.parse("-3.5"));
		assertEquals(1200, Numbers.parse("1.2e3"));
		assertEquals(0.5, Numbers.parse(" 0.5 "));
	}

	@Test
	void readsGroupedThousands() {

		assertEquals(1234567, Numbers.parse("1,234,567"));
		assertEquals(1234.56, Numbers.parse("1,234.56"));
		assertEquals(1234.56, Numbers.parse("1.234,56"));
		assertEquals(1234567, Numbers.parse("1'234'567"));
	}

	@Test
	void readsTheEuropeanDecimalComma() {
		assertEquals(1.5, Numbers.parse("1,5"));
	}

	@Test
	void readsMoneyAndPercentages() {

		assertEquals(19.99, Numbers.parse("$19.99"));
		assertEquals(-1500, Numbers.parse("(1,500)"));
		assertEquals(12.5, Numbers.parse("12.5%"));
	}

	@Test
	void refusesWhatIsNotANumber() {

		assertFalse(Numbers.isNumeric("ada"));
		assertFalse(Numbers.isNumeric(""));
		assertFalse(Numbers.isNumeric(null));
		assertFalse(Numbers.isNumeric("1,2,3"));
		assertFalse(Numbers.isNumeric("2024-01-31"));
		assertFalse(Numbers.isNumeric("1.2.3"));
		assertFalse(Numbers.isNumeric("12abc"));
		assertFalse(Numbers.isNumeric("0".repeat(60)));
	}

	@Test
	void acceptsWhatItCanRead() {

		assertTrue(Numbers.isNumeric("0"));
		assertTrue(Numbers.isNumeric("+7"));
		assertTrue(Numbers.isNumeric("1,000"));
	}
}
