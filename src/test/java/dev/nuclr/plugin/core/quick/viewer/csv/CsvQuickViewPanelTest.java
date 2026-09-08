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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.OpenOption;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JTable;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import dev.nuclr.platform.plugin.NuclrResource;

/**
 * The panel's own plumbing, exercised headless: the parts that decide what
 * reaches the grid, not how it is painted.
 */
class CsvQuickViewPanelTest {

	private static final String TABLE = """
			name,city
			ada,London
			alan,Wilmslow
			grace,New York
			""";

	@Test
	void showsTheLoadedFileAsAPageOfRows() throws Exception {

		CsvQuickViewPanel panel = panel();

		assertTrue(panel.load(new StringResource("people.csv", TABLE), new AtomicBoolean()));
		flush();

		assertEquals(3, panel.currentView().length);
		assertEquals(3, panel.tableModel().getRowCount());
		assertEquals("ada", panel.tableModel().getValueAt(0, 1));
		assertEquals("city", panel.tableModel().getColumnName(2));
	}

	@Test
	void replacesTheFileWhenAnotherIsOpened() throws Exception {

		CsvQuickViewPanel panel = panel();

		panel.load(new StringResource("people.csv", TABLE), new AtomicBoolean());
		flush();
		panel.load(new StringResource("other.csv", "id\n1\n2\n"), new AtomicBoolean());
		flush();

		assertEquals(2, panel.currentView().length);
		assertEquals("id", panel.tableModel().getColumnName(1));
	}

	@Test
	void doesNotShowAFileTheUserHasAlreadyMovedOnFrom() throws Exception {

		CsvQuickViewPanel panel = panel();
		AtomicBoolean cancelled = new AtomicBoolean(true);

		assertFalse(panel.load(new StringResource("people.csv", TABLE), cancelled));
		flush();

		assertEquals(0, panel.tableModel().getRowCount());
	}

	@Test
	void handsBackAFileThatIsNotDelimitedText() throws Exception {

		CsvQuickViewPanel panel = panel();
		byte[] binary = { 'M', 'Z', 0, 3, 0, 0, 4 };

		assertFalse(panel.load(new StringResource("odd.csv", binary), new AtomicBoolean()));
	}

	@Test
	void keepsTheGridUpWhenAFileCannotBeRead() throws Exception {

		CsvQuickViewPanel panel = panel();

		// A resource whose stream cannot be opened at all: the viewer still claims it,
		// because the user asked for this file and deserves to be told why not.
		assertTrue(panel.load(new UnreadableResource("gone.csv"), new AtomicBoolean()));

		flush();
	}

	@Test
	void showsTheRowsTheUserSelects() throws Exception {

		CsvQuickViewPanel panel = panel();
		panel.load(new StringResource("people.csv", TABLE), new AtomicBoolean());
		flush();

		JTable table = panel.tableComponent();
		onEdt(() -> table.setRowSelectionInterval(0, 0));

		// Row selection has to stay on: setCellSelectionEnabled(false) would turn it
		// off, and every cell would then report itself unselected however many rows
		// the selection model holds - a selection nothing on screen shows.
		assertTrue(table.getRowSelectionAllowed());
		assertTrue(table.isRowSelected(0));
		assertTrue(table.isCellSelected(0, 1));
		assertFalse(table.isCellSelected(1, 1));
	}

	@Test
	void takesTheColumnHeaderBackWhenTheTableClaimsIt() throws Exception {

		CsvQuickViewPanel panel = panel();
		panel.load(new StringResource("people.csv", TABLE), new AtomicBoolean());
		flush();

		onEdt(() -> {
			// What JTable does to its scroll pane the moment it becomes displayable.
			panel.scrollPane().setColumnHeaderView(new JTable().getTableHeader());
			panel.installColumnHeader();
		});

		assertSame(panel.columnHeaderView(), panel.scrollPane().getColumnHeader().getView());
		assertEquals(2, panel.columnHeaderView().getComponentCount());
	}

	@Test
	void releasesTheFileWhenThePreviewIsClosed() throws Exception {

		CsvQuickViewPanel panel = panel();

		panel.load(new StringResource("people.csv", TABLE), new AtomicBoolean());
		flush();
		panel.clear();
		flush();

		assertEquals(0, panel.currentView().length);
		assertEquals(0, panel.tableModel().getRowCount());
		assertEquals(0, panel.tableModel().getColumnCount());
	}

	@Test
	void appliesAThemeBeforeAndAfterAFileIsLoaded() throws Exception {

		CsvQuickViewPanel panel = panel();

		onEdt(() -> panel.applyTheme(null));
		panel.load(new StringResource("people.csv", TABLE), new AtomicBoolean());
		flush();
		onEdt(() -> panel.applyTheme(null));

		assertNotNull(panel.getBackground());
	}

	private static CsvQuickViewPanel panel() throws Exception {

		CsvQuickViewPanel[] created = new CsvQuickViewPanel[1];
		onEdt(() -> created[0] = new CsvQuickViewPanel());

		return created[0];
	}

	/** Lets everything {@code load} posted to the event queue run before the assertions. */
	private static void flush() throws Exception {
		onEdt(() -> {
			// Returning from an empty EDT task means everything queued before it has run.
		});
	}

	private static void onEdt(Runnable task) throws InterruptedException, InvocationTargetException {
		SwingUtilities.invokeAndWait(task);
	}

	/** A resource that exists as far as selection is concerned but cannot be opened. */
	private static final class UnreadableResource extends NuclrResource {

		private static final long serialVersionUID = 1L;

		UnreadableResource(String name) {

			super(null);

			setUuid(name);
			setName(name);
			setFullPath(name);
			setReadable(true);
		}

		@Override
		public InputStream openInputStream(OpenOption... options) throws Exception {
			throw new IOException("no such file");
		}
	}
}
