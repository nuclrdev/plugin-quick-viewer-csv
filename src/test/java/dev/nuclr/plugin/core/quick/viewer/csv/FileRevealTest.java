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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.nuclr.plugin.core.quick.viewer.csv.FileReveal.Platform;

class FileRevealTest {

	private static final Path FILE = Path.of("exports", "sales export.csv");

	@Test
	void namesTheDesktopFromTheOperatingSystem() {

		assertEquals(Platform.WINDOWS, Platform.of("Windows 11"));
		assertEquals(Platform.MACOS, Platform.of("Mac OS X"));
		assertEquals(Platform.MACOS, Platform.of("Darwin"));
		assertEquals(Platform.OTHER, Platform.of("Linux"));
		assertEquals(Platform.OTHER, Platform.of("FreeBSD"));
		assertEquals(Platform.OTHER, Platform.of(null));
	}

	@Test
	void asksExplorerToSelectTheFile() {

		List<String> command = FileReveal.command(Platform.WINDOWS, FILE);

		// Three arguments, not two: with "/select," and the path joined into one,
		// Explorer ignores any path containing a space.
		assertEquals(List.of("explorer.exe", "/select,", FILE.toAbsolutePath().toString()), command);
	}

	@Test
	void asksFinderToRevealTheFile() {

		List<String> command = FileReveal.command(Platform.MACOS, FILE);

		assertEquals(List.of("open", "-R", FILE.toAbsolutePath().toString()), command);
	}

	@Test
	void asksTheFreedesktopFileManagerToShowTheFile() {

		List<String> command = FileReveal.command(Platform.OTHER, FILE);

		assertEquals("dbus-send", command.get(0));
		assertTrue(command.contains("org.freedesktop.FileManager1.ShowItems"), command.toString());
		assertTrue(command.contains("--dest=org.freedesktop.FileManager1"), command.toString());
		// A URI, not a path: spaces and anything else non-ASCII have to be encoded.
		assertTrue(command.contains("array:string:" + FILE.toAbsolutePath().toUri()), command.toString());
		assertEquals("string:", command.get(command.size() - 1));
	}

	@Test
	void fallsBackToOpeningTheContainingFolder() {

		List<String> command = FileReveal.fallbackCommand(FILE);

		assertEquals(List.of("xdg-open", FILE.toAbsolutePath().getParent().toString()), command);
	}

	@Test
	void hasNothingToOpenForAFileWithNoParent() {

		Path root = Path.of("/").toAbsolutePath().getRoot();

		assertTrue(FileReveal.fallbackCommand(root).isEmpty());
	}
}
