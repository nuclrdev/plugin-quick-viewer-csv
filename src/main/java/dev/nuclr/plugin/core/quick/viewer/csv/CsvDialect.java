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

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The separator conventions of one file. "CSV" in the wild means comma,
 * semicolon (European locales, where the comma is the decimal point), tab or
 * pipe, and the user should not have to say which - so the dialect is sniffed
 * from the head of the file and can then be overridden from the viewer.
 *
 * @param delimiter the field separator
 * @param quote     the quote character, always a double quote in practice
 */
record CsvDialect(char delimiter, char quote) {

	/** Separators worth trying, best-known first; ties go to the earlier one. */
	static final char[] CANDIDATES = { ',', ';', '\t', '|' };

	/** How many records of the sample the sniffer scores. */
	private static final int SAMPLE_RECORDS = 25;

	static CsvDialect of(char delimiter) {
		return new CsvDialect(delimiter, '"');
	}

	/** A readable name for the separator, for the viewer's dialect menu. */
	String delimiterName() {
		return switch (delimiter) {
			case ',' -> "Comma";
			case ';' -> "Semicolon";
			case '\t' -> "Tab";
			case '|' -> "Pipe";
			default -> String.valueOf(delimiter);
		};
	}

	/**
	 * Picks the separator that parses {@code sample} into the most consistent
	 * table: the one whose records agree most often on their field count, and
	 * among equally consistent candidates the one that yields more columns.
	 *
	 * <p>A tab-separated file gets the tab before anything is parsed - a .tsv
	 * full of prose commas would otherwise sniff as comma-separated.
	 *
	 * @param sample   the first few KB of the file, already decoded
	 * @param fileName the resource name, for the extension hint; may be {@code null}
	 * @return the winning dialect, comma when nothing scores
	 */
	static CsvDialect sniff(String sample, String fileName) {

		String extension = CsvFileSupport.extension(fileName);
		if ("tsv".equals(extension) || "tab".equals(extension)) {
			return of('\t');
		}

		if (sample == null || sample.isBlank()) {
			return of(',');
		}

		CsvDialect best = of(',');
		double bestScore = -1;

		for (char candidate : CANDIDATES) {
			double score = score(sample, candidate);
			if (score > bestScore) {
				bestScore = score;
				best = of(candidate);
			}
		}

		return best;
	}

	/**
	 * Scores a candidate separator: the share of records that have the most
	 * common field count, weighted by that count so a separator that finds real
	 * columns beats one that finds a single column on every line.
	 */
	private static double score(String sample, char delimiter) {

		List<Integer> fieldCounts = new ArrayList<>();

		try (CsvParser parser = new CsvParser(new StringReader(sample), delimiter, '"')) {
			List<String> record;
			while (fieldCounts.size() < SAMPLE_RECORDS && (record = parser.next()) != null) {
				if (record.size() == 1 && record.get(0).isBlank()) {
					continue;
				}
				fieldCounts.add(record.size());
			}
		} catch (IOException e) {
			return -1;
		}

		// The last record of a sample is usually cut mid-line: judging it would
		// punish the separator that actually split it into the most fields.
		if (fieldCounts.size() > 1) {
			fieldCounts.remove(fieldCounts.size() - 1);
		}

		if (fieldCounts.isEmpty()) {
			return -1;
		}

		Map<Integer, Integer> histogram = new HashMap<>();
		for (int count : fieldCounts) {
			histogram.merge(count, 1, Integer::sum);
		}

		int modalCount = 0;
		int modalOccurrences = 0;
		for (Map.Entry<Integer, Integer> entry : histogram.entrySet()) {
			if (entry.getValue() > modalOccurrences
					|| (entry.getValue() == modalOccurrences && entry.getKey() > modalCount)) {
				modalCount = entry.getKey();
				modalOccurrences = entry.getValue();
			}
		}

		if (modalCount < 2) {
			return 0;
		}

		double consistency = modalOccurrences / (double) fieldCounts.size();
		// Columns beyond a handful say little more about the separator being right,
		// so their contribution flattens out instead of running away with the score.
		return consistency * (1 + Math.log(modalCount));
	}

	/**
	 * Whether the first record reads like column titles rather than data.
	 *
	 * <p>Titles are non-empty, distinct and not numbers. The numeric test carries
	 * most of the weight: a row of names above rows of names is ambiguous to any
	 * heuristic, and treating it as a header is both the common case and the
	 * cheaper mistake, since the viewer lets the user turn the header off.
	 *
	 * @param first the first record, or {@code null}
	 * @return {@code true} when it should be used as the column names
	 */
	static boolean looksLikeHeader(List<String> first) {

		if (first == null || first.isEmpty()) {
			return false;
		}

		List<String> seen = new ArrayList<>(first.size());
		for (String cell : first) {
			String value = cell == null ? "" : cell.trim();
			if (value.isEmpty() || !Double.isNaN(Numbers.parse(value))) {
				return false;
			}
			if (seen.contains(value)) {
				return false;
			}
			seen.add(value);
		}

		return true;
	}
}
