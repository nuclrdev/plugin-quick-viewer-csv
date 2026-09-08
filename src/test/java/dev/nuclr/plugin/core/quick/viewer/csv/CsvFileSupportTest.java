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

class CsvFileSupportTest {

	@Test
	void claimsDelimitedTextFiles() {

		assertTrue(CsvFileSupport.supports(new StringResource("people.csv", "a,b\n")));
		assertTrue(CsvFileSupport.supports(new StringResource("PEOPLE.CSV", "a,b\n")));
		assertTrue(CsvFileSupport.supports(new StringResource("people.tsv", "a\tb\n")));
		assertTrue(CsvFileSupport.supports(new StringResource("people.tab", "a\tb\n")));
	}

	@Test
	void leavesEverythingElseAlone() {

		assertFalse(CsvFileSupport.supports(new StringResource("notes.txt", "a,b\n")));
		assertFalse(CsvFileSupport.supports(new StringResource("photo.png", "a,b\n")));
		assertFalse(CsvFileSupport.supports(new StringResource("csv", "a,b\n")));
		assertFalse(CsvFileSupport.supports(new StringResource(".csv", "a,b\n")));
	}

	@Test
	void refusesFoldersUnreadableEntriesAndNull() {

		StringResource folder = new StringResource("data.csv", "");
		folder.setFolder(true);
		assertFalse(CsvFileSupport.supports(folder));

		StringResource unreadable = new StringResource("data.csv", "");
		unreadable.setReadable(false);
		assertFalse(CsvFileSupport.supports(unreadable));

		assertFalse(CsvFileSupport.supports(null));
	}

	@Test
	void survivesAResourceWithNoName() {

		StringResource nameless = new StringResource("data.csv", "");
		nameless.setName(null);

		assertFalse(CsvFileSupport.supports(nameless));
	}

	@Test
	void readsExtensionsAndBaseNames() {

		assertEquals("csv", CsvFileSupport.extension("people.csv"));
		assertEquals("csv", CsvFileSupport.extension("archive.tar.csv"));
		assertEquals("", CsvFileSupport.extension("README"));
		assertEquals("", CsvFileSupport.extension(null));

		assertEquals("people", CsvFileSupport.baseName("people.csv"));
		assertEquals("archive.tar", CsvFileSupport.baseName("archive.tar.csv"));
		assertEquals("README", CsvFileSupport.baseName("README"));
		assertEquals("export", CsvFileSupport.baseName(null));
	}
}
