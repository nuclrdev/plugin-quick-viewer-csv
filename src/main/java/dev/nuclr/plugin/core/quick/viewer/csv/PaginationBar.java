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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import dev.nuclr.plugin.core.quick.viewer.csv.ViewerUi.Palette;

/**
 * The foot of the viewer: which page is showing, how to get to another one, how
 * many rows a page holds, and - the number the user actually came for - how
 * many rows the file has.
 */
final class PaginationBar extends JPanel {

	private static final long serialVersionUID = 1L;

	/** The label for {@link Paging#FIT} in the page-size list. */
	private static final String FIT_LABEL = "Fit";

	/** What the bar reports back to the viewer. */
	interface Listener {

		/** A page was asked for, already 0-based and clamped by the bar. */
		void pageRequested(int page);

		/** The rows-per-page choice changed, either to {@link Paging#FIT} or to a fixed count. */
		void pageSizeChoiceChanged(int choice);
	}

	private final JButton first = button("|◀", "First page");
	private final JButton previous = button("◀", "Previous page (Alt+Left)");
	private final JButton next = button("▶", "Next page (Alt+Right)");
	private final JButton last = button("▶|", "Last page");
	private final JTextField pageField = ViewerUi.compact(new JTextField(3));
	private final JLabel pageCountLabel = ViewerUi.compact(new JLabel("/ 1"));
	private final JComboBox<String> pageSize = ViewerUi.compact(new JComboBox<>());
	private final JLabel status = ViewerUi.compact(new JLabel(" "));

	private final Listener listener;

	private int pageCount = 1;
	private boolean updating;

	PaginationBar(Listener listener) {

		super(new BorderLayout());
		this.listener = listener;

		setBorder(BorderFactory.createEmptyBorder(2, 4, 3, 4));

		pageField.setHorizontalAlignment(SwingConstants.RIGHT);
		pageField.setToolTipText("Go to page");
		pageField.addActionListener(e -> goToTypedPage());

		pageSize.addItem(FIT_LABEL);
		for (int choice : Paging.CHOICES) {
			pageSize.addItem(String.valueOf(choice));
		}
		pageSize.setToolTipText("Rows per page");
		pageSize.addActionListener(e -> {
			if (!updating) {
				listener.pageSizeChoiceChanged(choiceOf((String) pageSize.getSelectedItem()));
			}
		});

		first.addActionListener(e -> listener.pageRequested(0));
		previous.addActionListener(e -> listener.pageRequested(currentPage() - 1));
		next.addActionListener(e -> listener.pageRequested(currentPage() + 1));
		last.addActionListener(e -> listener.pageRequested(pageCount - 1));

		add(navigation(), BorderLayout.NORTH);
		add(status, BorderLayout.SOUTH);
	}

	/**
	 * Shows which page of how many is on screen.
	 *
	 * @param page      the current page, 0-based
	 * @param pageCount how many pages there are, at least one
	 */
	void update(int page, int pageCount) {

		this.pageCount = Math.max(1, pageCount);

		updating = true;
		try {
			pageField.setText(String.valueOf(page + 1));
			pageCountLabel.setText("/ " + ViewerUi.count(this.pageCount));
		} finally {
			updating = false;
		}

		boolean hasPrevious = page > 0;
		boolean hasNext = page < this.pageCount - 1;
		first.setEnabled(hasPrevious);
		previous.setEnabled(hasPrevious);
		next.setEnabled(hasNext);
		last.setEnabled(hasNext);
	}

	/** The line that carries the row counts, the selection count and any truncation warning. */
	void setStatus(String text) {
		status.setText(text == null || text.isBlank() ? " " : text);
		status.setToolTipText(status.getText());
	}

	void setPageSizeChoice(int choice) {

		updating = true;
		try {
			pageSize.setSelectedItem(choice == Paging.FIT ? FIT_LABEL : String.valueOf(choice));
		} finally {
			updating = false;
		}
	}

	int pageSizeChoice() {
		return choiceOf((String) pageSize.getSelectedItem());
	}

	void applyTheme(Palette palette) {

		setBackground(palette.header());
		status.setForeground(palette.muted());
		pageCountLabel.setForeground(palette.muted());
		pageField.setBackground(palette.background());
		pageField.setForeground(palette.foreground());
		pageField.setCaretColor(palette.foreground());

		for (Component component : getComponents()) {
			component.setFont(ViewerUi.smallFont());
			if (component instanceof JPanel panel) {
				panel.setBackground(palette.header());
				for (Component child : panel.getComponents()) {
					child.setFont(ViewerUi.smallFont());
				}
			}
		}
	}

	// -------------------------------------------------------------------------

	private JPanel navigation() {

		JPanel row = new JPanel(new GridBagLayout());
		row.setOpaque(false);

		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridy = 0;
		constraints.insets = new Insets(0, 0, 0, 2);

		row.add(first, constraints);
		row.add(previous, constraints);
		row.add(pageField, constraints);
		row.add(pageCountLabel, constraints);
		row.add(next, constraints);
		row.add(last, constraints);

		// The spacer takes the slack, so the page controls stay left and the rows
		// selector stays right however wide the pane is.
		constraints.weightx = 1;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		row.add(Box.createHorizontalGlue(), constraints);

		constraints.weightx = 0;
		constraints.fill = GridBagConstraints.NONE;
		constraints.insets = new Insets(0, 0, 0, 0);
		row.add(pageSize, constraints);

		return row;
	}

	private int currentPage() {

		try {
			return Math.max(0, Integer.parseInt(pageField.getText().trim()) - 1);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private void goToTypedPage() {

		int page = currentPage();
		listener.pageRequested(Math.max(0, Math.min(page, pageCount - 1)));
	}

	private static int choiceOf(String label) {

		if (label == null || FIT_LABEL.equals(label)) {
			return Paging.FIT;
		}

		try {
			return Integer.parseInt(label);
		} catch (NumberFormatException e) {
			return Paging.FIT;
		}
	}

	private static JButton button(String text, String tooltip) {

		JButton button = ViewerUi.compact(new JButton(text));
		button.setToolTipText(tooltip);
		button.setFocusable(false);
		button.setMargin(new Insets(1, 4, 1, 4));

		return button;
	}
}
