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
import java.awt.Component;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingConstants;

import org.junit.jupiter.api.Test;

import dev.nuclr.platform.NuclrThemeScheme;
import dev.nuclr.plugin.core.quick.viewer.csv.ViewerUi.Palette;

class CsvCellRendererTest {

	private static final String TABLE = """
			name,qty
			ada,9
			alan,10
			""";

	private static final NuclrThemeScheme THEME = new NuclrThemeScheme("Test", Map.of(
			"Table.background", "#202020",
			"Table.foreground", "#E0E0E0",
			"Table.selectionBackground", "#3A7BD5",
			"Table.selectionForeground", "#FFFFFF"));

	@Test
	void paintsASelectedRowInTheThemesSelectionColour() {

		Palette palette = ViewerUi.palette(THEME);
		Fixture fixture = new Fixture(palette);

		Component selected = fixture.render(0, 1, true);
		assertEquals(palette.selection(), selected.getBackground());
		assertEquals(palette.selectionForeground(), selected.getForeground());

		Component unselected = fixture.render(0, 1, false);
		assertNotEquals(palette.selection(), unselected.getBackground());
	}

	@Test
	void bandsUnselectedRowsWithTheThemesRowColours() {

		Palette palette = ViewerUi.palette(THEME);
		Fixture fixture = new Fixture(palette);

		assertEquals(palette.background(), fixture.render(0, 1, false).getBackground());
		assertEquals(palette.stripe(), fixture.render(1, 1, false).getBackground());
	}

	@Test
	void rightAlignsNumbersAndTheRowNumberGutter() {

		Fixture fixture = new Fixture(ViewerUi.palette(THEME));

		assertEquals(SwingConstants.RIGHT, ((JLabel) fixture.render(0, 0, false)).getHorizontalAlignment());
		assertEquals(SwingConstants.RIGHT, ((JLabel) fixture.render(0, 2, false)).getHorizontalAlignment());
		assertEquals(SwingConstants.LEFT, ((JLabel) fixture.render(0, 1, false)).getHorizontalAlignment());
	}

	@Test
	void tintsTheCellsASearchMatched() {

		Palette palette = ViewerUi.palette(THEME);
		Fixture fixture = new Fixture(palette);

		CsvSearch.Hits hits = CsvSearch.find(fixture.data, CsvIndex.all(fixture.data),
				TextMatcher.compile("ada", false, false), new AtomicBoolean());
		fixture.renderer.setHits(hits, 0, 0);

		Color matched = fixture.render(0, 1, false).getBackground();
		Color plain = fixture.render(1, 1, false).getBackground();

		assertNotEquals(plain, matched);
		// Tinted towards the accent, so it reads as a hit rather than as another row.
		assertTrue(ViewerUi.contrastRatio(matched, palette.accent()) < ViewerUi.contrastRatio(plain, palette.accent()));
	}

	/** A model, a table and a renderer wired the way the panel wires them. */
	private static final class Fixture {

		private final CsvData data = TestTables.of(TABLE);
		private final CsvTableModel model = new CsvTableModel();
		private final CsvCellRenderer renderer = new CsvCellRenderer(model);
		private final JTable table;

		Fixture(Palette palette) {

			model.setData(data);
			model.setPage(CsvIndex.all(data), 0, data.rowCount());
			renderer.applyTheme(palette);
			table = new JTable(model);
		}

		Component render(int row, int column, boolean selected) {
			return renderer.getTableCellRendererComponent(table, model.getValueAt(row, column), selected, false,
					row, column);
		}
	}
}
