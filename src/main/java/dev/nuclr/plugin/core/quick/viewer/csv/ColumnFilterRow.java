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

import java.awt.Dimension;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ObjIntConsumer;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.event.ChangeEvent;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.table.TableColumnModel;

import dev.nuclr.plugin.core.quick.viewer.csv.ViewerUi.Palette;

/**
 * A row of filter fields, one under each column heading.
 *
 * <p>It lives inside the scroll pane's column header, below the table header
 * itself, so it scrolls sideways with the grid and every field stays under its
 * column. Nothing lays it out but this class: the fields are positioned from
 * the table's own column widths, which is what keeps them aligned while the
 * user drags a column edge.
 */
final class ColumnFilterRow extends JPanel {

	private static final long serialVersionUID = 1L;

	private static final int GAP = 1;

	private final JTable table;
	private final ObjIntConsumer<String> onChange;
	private final List<JTextField> fields = new ArrayList<>();

	private Palette palette = ViewerUi.palette(null);
	private boolean updating;

	/**
	 * @param table    the table whose columns the fields follow
	 * @param onChange called with the new text and the file column it filters
	 */
	ColumnFilterRow(JTable table, ObjIntConsumer<String> onChange) {

		this.table = table;
		this.onChange = onChange;

		setLayout(null);
		setOpaque(true);

		table.getColumnModel().addColumnModelListener(new TableColumnModelListener() {

			@Override
			public void columnMarginChanged(ChangeEvent e) {
				revalidate();
				repaint();
			}

			@Override
			public void columnMoved(TableColumnModelEvent e) {
				revalidate();
				repaint();
			}

			@Override
			public void columnAdded(TableColumnModelEvent e) {
				revalidate();
			}

			@Override
			public void columnRemoved(TableColumnModelEvent e) {
				revalidate();
			}

			@Override
			public void columnSelectionChanged(ListSelectionEvent e) {
				// Selection does not move a column, so nothing to re-lay out.
			}
		});
	}

	/** Rebuilds the fields for a newly opened file. */
	void rebuild() {

		updating = true;
		try {
			removeAll();
			fields.clear();

			for (int column = 0; column < table.getColumnCount(); column++) {
				int dataColumn = CsvTableModel.dataColumn(column);
				if (dataColumn < 0) {
					// Nothing to filter on the row-number gutter.
					fields.add(null);
					continue;
				}
				JTextField field = field(dataColumn, table.getColumnName(column));
				fields.add(field);
				add(field);
			}
		} finally {
			updating = false;
		}

		revalidate();
		repaint();
	}

	/** Clears every field without reporting the change - used when the filter is reset elsewhere. */
	void clear() {

		updating = true;
		try {
			for (JTextField field : fields) {
				if (field != null) {
					field.setText("");
					applyFieldTheme(field);
				}
			}
		} finally {
			updating = false;
		}
	}

	void applyTheme(Palette palette) {

		this.palette = palette;
		setBackground(palette.header());

		for (JTextField field : fields) {
			if (field != null) {
				applyFieldTheme(field);
			}
		}
	}

	@Override
	public Dimension getPreferredSize() {

		int height = 0;
		for (JTextField field : fields) {
			if (field != null) {
				height = Math.max(height, field.getPreferredSize().height);
			}
		}

		if (height == 0) {
			height = ViewerUi.compact(new JTextField()).getPreferredSize().height;
		}

		return new Dimension(table.getColumnModel().getTotalColumnWidth(), height + 2 * GAP);
	}

	@Override
	public void doLayout() {

		TableColumnModel columns = table.getColumnModel();
		int x = 0;

		for (int i = 0; i < columns.getColumnCount(); i++) {

			int width = columns.getColumn(i).getWidth();
			int modelColumn = columns.getColumn(i).getModelIndex();

			JTextField field = modelColumn >= 0 && modelColumn < fields.size() ? fields.get(modelColumn) : null;
			if (field != null) {
				field.setBounds(x + GAP, GAP, Math.max(0, width - 2 * GAP), getHeight() - 2 * GAP);
			}

			x += width;
		}
	}

	// -------------------------------------------------------------------------

	private JTextField field(int dataColumn, String columnTitle) {

		JTextField field = ViewerUi.compact(new JTextField());
		field.setToolTipText("Filter " + columnTitle);
		field.putClientProperty("JTextField.showClearButton", Boolean.TRUE);

		// Let Escape through to the panel, which uses it to close the find bar or
		// clear the filters, instead of the field swallowing it.
		field.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "none");

		field.getDocument().addDocumentListener(new DocumentListener() {

			@Override
			public void insertUpdate(DocumentEvent e) {
				changed();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				changed();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				changed();
			}

			private void changed() {
				if (updating) {
					return;
				}
				applyFieldTheme(field);
				onChange.accept(field.getText(), dataColumn);
			}
		});

		applyFieldTheme(field);
		return field;
	}

	/** An active field is tinted, so a filter left on a column that scrolled off-screen is still visible. */
	private void applyFieldTheme(JTextField field) {

		boolean active = !field.getText().isEmpty();

		field.setBackground(active
				? ViewerUi.blend(palette.background(), palette.accent(), 0.18f)
				: palette.background());
		field.setForeground(palette.foreground());
		field.setCaretColor(palette.foreground());
		// The border carries the grid colour, so it is re-made here rather than at
		// construction: a theme change has to reach it too.
		field.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 0, 1, 1, palette.grid()),
				BorderFactory.createEmptyBorder(1, 4, 1, 4)));
	}
}
