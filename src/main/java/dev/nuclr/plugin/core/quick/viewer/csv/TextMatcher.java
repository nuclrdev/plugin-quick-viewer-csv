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

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * One compiled query - the single place where "does this cell match?" is
 * decided, so the filter fields and the find bar agree on what a match is.
 *
 * <p>Both modes are substring matches: plain text looks for the query anywhere
 * in the cell, a regular expression is applied with {@code find()} rather than
 * {@code matches()}, so {@code ^…$} anchors mean what the user expects and an
 * unanchored expression behaves like the text mode it sits next to.
 */
final class TextMatcher {

	private final String query;
	private final boolean regex;
	private final boolean caseSensitive;
	private final Pattern pattern;

	private TextMatcher(String query, boolean regex, boolean caseSensitive, Pattern pattern) {
		this.query = query;
		this.regex = regex;
		this.caseSensitive = caseSensitive;
		this.pattern = pattern;
	}

	/**
	 * Compiles a query.
	 *
	 * @param query         what the user typed; may be {@code null}
	 * @param regex         whether to read it as a regular expression
	 * @param caseSensitive whether case matters
	 * @return the matcher, or {@code null} when the query is blank - meaning
	 *         "no constraint", which every caller treats as matching everything
	 * @throws PatternSyntaxException if regex mode is on and the expression is
	 *                                not yet valid (typed half-way, usually)
	 */
	static TextMatcher compile(String query, boolean regex, boolean caseSensitive) {

		if (query == null || query.isEmpty()) {
			return null;
		}

		if (!regex) {
			return new TextMatcher(query, false, caseSensitive, null);
		}

		int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
		return new TextMatcher(query, true, caseSensitive, Pattern.compile(query, flags));
	}

	/**
	 * Whether {@code value} matches.
	 *
	 * @param value the cell text; {@code null} is treated as empty
	 * @return {@code true} on a match
	 */
	boolean matches(String value) {

		String text = value == null ? "" : value;

		if (regex) {
			return pattern.matcher(text).find();
		}

		return indexIn(text, 0) >= 0;
	}

	/**
	 * Where the match starts in {@code value}, at or after {@code from} - the
	 * offset the cell renderer highlights.
	 *
	 * @param value the cell text; {@code null} is treated as empty
	 * @param from  the offset to start looking from
	 * @return the start offset, or {@code -1} when there is no match
	 */
	int indexIn(String value, int from) {

		String text = value == null ? "" : value;

		if (regex) {
			var matcher = pattern.matcher(text);
			return matcher.find(Math.max(0, from)) ? matcher.start() : -1;
		}

		if (caseSensitive) {
			return text.indexOf(query, from);
		}

		// regionMatches rather than lowercasing: this runs over every cell of
		// every row on each keystroke, and allocating a copy of the file to
		// answer it would be the whole cost of filtering.
		int last = text.length() - query.length();
		for (int i = Math.max(0, from); i <= last; i++) {
			if (text.regionMatches(true, i, query, 0, query.length())) {
				return i;
			}
		}

		return -1;
	}

	/** How long the matched text is, for the highlight; a regex match can differ per cell. */
	int matchLength(String value, int start) {

		if (!regex) {
			return query.length();
		}

		String text = value == null ? "" : value;
		var matcher = pattern.matcher(text);
		return matcher.find(start) && matcher.start() == start ? matcher.end() - start : 0;
	}

	String query() {
		return query;
	}
}
