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

import org.junit.jupiter.api.Test;

class CsvTableModelTest {

	private static final String TABLE = """
			name,city
			ada,London
			alan,Wilmslow
			grace,New York
			edsger,Austin
			""";

	@Test
	void showsOnlyTheRowsOfThePage() {

		CsvTableModel model = model(TABLE);
		model.setPage(CsvIndex.all(model.data()), 2, 2);

		assertEquals(2, model.getRowCount());
		assertEquals("grace", model.getValueAt(0, 1));
		assertEquals("Austin", model.getValueAt(1, 2));
	}

	@Test
	void putsTheFileRowNumberInTheGutter() {

		CsvTableModel model = model(TABLE);
		model.setPage(CsvIndex.all(model.data()), 2, 2);

		assertEquals("#", model.getColumnName(0));
		assertEquals("3", model.getValueAt(0, 0));
		assertEquals("4", model.getValueAt(1, 0));
	}

	@Test
	void keepsTheFileRowNumberAfterSorting() {

		CsvTableModel model = model(TABLE);
		CsvData data = model.data();

		int[] view = CsvIndex.all(data);
		CsvIndex.sort(view, data, 0, false);
		model.setPage(view, 0, 4);

		// "ada" sorts first and is still row 1 of the file; "edsger" moves up from row 4.
		assertEquals("ada", model.getValueAt(0, 1));
		assertEquals("1", model.getValueAt(0, 0));
		assertEquals("edsger", model.getValueAt(2, 1));
		assertEquals("4", model.getValueAt(2, 0));
	}

	@Test
	void mapsTableRowsBackToTheFile() {

		CsvTableModel model = model(TABLE);
		model.setPage(CsvIndex.all(model.data()), 1, 2);

		assertEquals(1, model.sourceRow(0));
		assertEquals(2, model.sourceRow(1));
		assertEquals(-1, model.sourceRow(9));
		assertEquals(1, model.viewRow(0));
	}

	@Test
	void doesNotRunOffTheEndOfAShortLastPage() {

		CsvTableModel model = model(TABLE);
		model.setPage(CsvIndex.all(model.data()), 3, 25);

		assertEquals(1, model.getRowCount());
	}

	@Test
	void namesTheColumnsAfterTheFileAndNeverEditsThem() {

		CsvTableModel model = model(TABLE);

		assertEquals(3, model.getColumnCount());
		assertEquals("name", model.getColumnName(1));
		assertEquals("city", model.getColumnName(2));
		assertFalse(model.isCellEditable(0, 1));
	}

	@Test
	void emptiesOutWhenTheFileIsDropped() {

		CsvTableModel model = model(TABLE);
		model.setData(null);

		assertEquals(0, model.getColumnCount());
		assertEquals(0, model.getRowCount());
		assertEquals("", model.getValueAt(0, 0));
	}

	@Test
	void offsetsFileColumnsPastTheGutter() {

		assertEquals(-1, CsvTableModel.dataColumn(0));
		assertEquals(0, CsvTableModel.dataColumn(1));
		assertEquals(1, CsvTableModel.tableColumn(0));
	}

	private static CsvTableModel model(String csv) {

		CsvTableModel model = new CsvTableModel();
		model.setData(TestTables.of(csv));

		return model;
	}
}
