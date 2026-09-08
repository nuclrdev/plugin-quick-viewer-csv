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
 * Reads the numbers people actually put in spreadsheets, so that a column of
 * them sorts 2 before 10 and right-aligns like a number should.
 *
 * <p>Everything here is a best effort on a string of unknown origin: what it
 * cannot read is simply not a number, and the caller falls back to comparing
 * text.
 */
final class Numbers {

	/** Long enough for any real figure; past it the string is prose, not a number. */
	private static final int MAX_LENGTH = 40;

	private static final String GROUPED_COMMA = "\\d{1,3}(,\\d{3})+";
	private static final String GROUPED_COMMA_DECIMAL = "\\d{1,3}(,\\d{3})+(\\.\\d+)?";
	private static final String GROUPED_DOT_DECIMAL = "\\d{1,3}(\\.\\d{3})+,\\d+";
	private static final String GROUPED_SPACE_DECIMAL = "\\d{1,3}( \\d{3})+(\\.\\d+)?";
	private static final String COMMA_DECIMAL = "\\d+,\\d+";

	private Numbers() {
	}

	/**
	 * Parses a cell as a number.
	 *
	 * <p>Understands a leading currency symbol, thousands separators (comma,
	 * apostrophe or space, but only in correct groups of three), accounting
	 * negatives in parentheses, a trailing percent sign, and a comma used as the
	 * decimal point - the European convention that makes semicolon-separated
	 * files exist in the first place.
	 *
	 * @param value the raw cell text; may be {@code null}
	 * @return the value, or {@link Double#NaN} when it is not a number
	 */
	static double parse(String value) {

		if (value == null) {
			return Double.NaN;
		}

		String text = value.trim();
		if (text.isEmpty() || text.length() > MAX_LENGTH) {
			return Double.NaN;
		}

		boolean negative = false;

		if (text.length() > 2 && text.charAt(0) == '(' && text.charAt(text.length() - 1) == ')') {
			negative = true;
			text = text.substring(1, text.length() - 1).trim();
		}

		if (!text.isEmpty() && (text.charAt(0) == '-' || text.charAt(0) == '+')) {
			negative ^= text.charAt(0) == '-';
			text = text.substring(1).trim();
		}

		if (!text.isEmpty() && isCurrency(text.charAt(0))) {
			text = text.substring(1).trim();
		}

		if (text.endsWith("%")) {
			text = text.substring(0, text.length() - 1).trim();
		}

		if (text.isEmpty() || !Character.isDigit(text.charAt(0))) {
			return Double.NaN;
		}

		String plain = withoutGrouping(text);
		if (plain == null) {
			return Double.NaN;
		}

		try {
			double parsed = Double.parseDouble(plain);
			return negative ? -parsed : parsed;
		} catch (NumberFormatException e) {
			return Double.NaN;
		}
	}

	/** Whether a cell reads as a number, which is how a column earns right alignment. */
	static boolean isNumeric(String value) {
		return !Double.isNaN(parse(value));
	}

	private static boolean isCurrency(char ch) {
		return ch == '$' || ch == '€' || ch == '£' || ch == '¥'
				|| ch == '₽' || ch == '₴';
	}

	/**
	 * Strips grouping separators, or returns {@code null} when the string is not
	 * grouped the way a number is - which is what keeps "1,2,3", a date or a
	 * version string from being read as one.
	 */
	private static String withoutGrouping(String text) {

		int comma = text.indexOf(',');
		int dot = text.indexOf('.');

		if (comma >= 0 && dot < 0) {
			// Either grouping (1,234,567) or a European decimal point (1,5).
			if (text.matches(GROUPED_COMMA)) {
				return text.replace(",", "");
			}
			return text.matches(COMMA_DECIMAL) ? text.replace(',', '.') : null;
		}

		if (comma >= 0) {
			if (comma > dot) {
				// 1.234,56 - the dot groups and the comma is the decimal point.
				return text.matches(GROUPED_DOT_DECIMAL) ? text.replace(".", "").replace(',', '.') : null;
			}
			return text.matches(GROUPED_COMMA_DECIMAL) ? text.replace(",", "") : null;
		}

		// Apostrophes (Swiss) and non-breaking spaces (French) group as well.
		String plain = text.replace(' ', ' ').replace(' ', ' ').replace("'", "");

		if (plain.indexOf(' ') >= 0) {
			return plain.matches(GROUPED_SPACE_DECIMAL) ? plain.replace(" ", "") : null;
		}

		// A second dot is a date or a version, not a number.
		return plain.indexOf('.') == plain.lastIndexOf('.') ? plain : null;
	}
}
