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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.nuclr.platform.NuclrThemeScheme;
import dev.nuclr.plugin.core.quick.viewer.csv.ViewerUi.Palette;

class ViewerUiTest {

	@Test
	void takesItsColoursFromTheThemeScheme() {

		Palette palette = ViewerUi.palette(new NuclrThemeScheme("Test", Map.of(
				"Table.background", "#202020",
				"Table.foreground", "#E0E0E0",
				"Table.selectionBackground", "#3A7BD5",
				"TableHeader.background", "#2A2A2A")));

		assertEquals(new Color(0x20, 0x20, 0x20), palette.background());
		assertEquals(new Color(0xE0, 0xE0, 0xE0), palette.foreground());
		assertEquals(new Color(0x2A, 0x2A, 0x2A), palette.header());
		// Already distinct from the background, so the theme's colour is used as-is.
		assertEquals(new Color(0x3A, 0x7B, 0xD5), palette.selection());
	}

	@Test
	void bandsRowsWithTheThemesAlternateRowColourWhenItHasOne() {

		Palette themed = ViewerUi.palette(new NuclrThemeScheme("Test", Map.of(
				"Table.background", "#202020",
				"Table.alternateRowColor", "#262626")));

		assertEquals(new Color(0x26, 0x26, 0x26), themed.stripe());
	}

	@Test
	void keepsASelectedRowVisibleWhenTheThemesSelectionIsTooCloseToTheBackground() {

		Color background = new Color(0x20, 0x22, 0x24);
		Color barelyThere = new Color(0x23, 0x25, 0x27);

		Color adjusted = ViewerUi.ensureSelectionContrast(background, barelyThere);

		assertNotEquals(barelyThere, adjusted);
		assertTrue(ViewerUi.contrastRatio(background, adjusted) >= 3.0);
	}

	@Test
	void leavesASelectionColourThatAlreadyStandsOut() {

		Color background = new Color(0xFA, 0xFA, 0xFA);
		Color selection = new Color(0x24, 0x64, 0xA8);

		assertEquals(selection, ViewerUi.ensureSelectionContrast(background, selection));
	}

	@Test
	void picksAForegroundThatCanBeReadOnTheSelection() {

		Color selection = new Color(0x33, 0x66, 0x99);
		Color foreground = ViewerUi.readable(selection, new Color(0x3A, 0x69, 0x98), new Color(0x40, 0x70, 0xA0));

		assertTrue(ViewerUi.contrastRatio(selection, foreground) >= 4.5);
	}

	@Test
	void survivesAThemeThatOverridesNothing() {

		Palette palette = ViewerUi.palette(null);

		assertTrue(ViewerUi.contrastRatio(palette.background(), palette.selection()) >= 3.0);
		assertTrue(ViewerUi.contrastRatio(palette.selection(), palette.selectionForeground()) >= 4.5);
	}
}
