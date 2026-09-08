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

import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import dev.nuclr.plugin.core.quick.viewer.csv.ViewerUi.Palette;

/**
 * The Ctrl+F bar: a query, how many cells it matched, and the two keys that
 * walk between them.
 *
 * <p>Separate from the filter above it on purpose. Filtering answers "show me
 * only these rows"; finding answers "where is this?" and leaves the rest of the
 * file where it was - a distinction that matters most in the file where the
 * answer is "row 40,112 of 200,000".
 */
final class FindBar extends JPanel {

	private static final long serialVersionUID = 1L;

	/** What the bar reports back to the viewer. */
	interface Listener {

		/** The query or the way it is read changed. */
		void queryChanged(String query, boolean regex, boolean caseSensitive);

		/** Go to the next match. */
		void next();

		/** Go to the previous match. */
		void previous();

		/** The bar was dismissed and its highlights should go with it. */
		void closed();
	}

	private final JTextField query = ViewerUi.compact(new JTextField());
	private final JLabel status = ViewerUi.compact(new JLabel(" "));
	private final JToggleButton caseSensitive = toggle("Aa", "Match case");
	private final JToggleButton regex = toggle(".*", "Regular expression");
	private final JButton previous = button("▲", "Previous match (Shift+F3)");
	private final JButton next = button("▼", "Next match (F3)");
	private final JButton close = button("✕", "Close (Esc)");

	private final Listener listener;

	private Palette palette = ViewerUi.palette(null);

	FindBar(Listener listener) {

		super(new GridBagLayout());
		this.listener = listener;

		setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));
		setVisible(false);

		query.putClientProperty("JTextField.placeholderText", "Find…");
		query.putClientProperty("JTextField.showClearButton", Boolean.TRUE);
		query.getDocument().addDocumentListener(new DocumentListener() {

			@Override
			public void insertUpdate(DocumentEvent e) {
				report();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				report();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				report();
			}
		});

		bind(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "findNext", listener::next);
		bind(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.SHIFT_DOWN_MASK), "findPrevious", listener::previous);
		bind(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "closeFind", this::close);

		caseSensitive.addActionListener(e -> report());
		regex.addActionListener(e -> report());
		next.addActionListener(e -> listener.next());
		previous.addActionListener(e -> listener.previous());
		close.addActionListener(e -> close());

		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridy = 0;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.insets = new Insets(0, 0, 0, 3);
		constraints.weightx = 1;
		add(query, constraints);

		constraints.weightx = 0;
		add(status, constraints);
		add(caseSensitive, constraints);
		add(regex, constraints);
		add(previous, constraints);
		add(next, constraints);
		constraints.insets = new Insets(0, 0, 0, 0);
		add(close, constraints);
	}

	/** Shows the bar and puts the caret in it, ready for the user to type over the last query. */
	void open() {

		setVisible(true);
		revalidate();

		SwingUtilities.invokeLater(() -> {
			query.requestFocusInWindow();
			query.selectAll();
		});

		report();
	}

	void close() {

		if (!isVisible()) {
			return;
		}

		setVisible(false);
		revalidate();
		listener.closed();
	}

	/** The match counter, e.g. {@code 3 / 27}. */
	void setStatus(String text) {
		status.setText(text == null || text.isBlank() ? " " : text);
	}

	/**
	 * Marks the query as one that could not be compiled.
	 *
	 * @param message why, or {@code null} when the query is fine
	 */
	void setInvalid(String message) {

		query.setBorder(message == null
				? null
				: BorderFactory.createLineBorder(ViewerUi.blend(palette.accent(), Color.RED, 0.5f)));
		query.setToolTipText(message);
	}

	String query() {
		return query.getText();
	}

	void applyTheme(Palette palette) {

		this.palette = palette;
		setBackground(palette.header());

		query.setBackground(palette.background());
		query.setForeground(palette.foreground());
		query.setCaretColor(palette.foreground());
		status.setForeground(palette.muted());

		for (Component component : getComponents()) {
			component.setFont(ViewerUi.smallFont());
		}
	}

	// -------------------------------------------------------------------------

	private void report() {
		listener.queryChanged(query.getText(), regex.isSelected(), caseSensitive.isSelected());
	}

	private void bind(KeyStroke keyStroke, String name, Runnable action) {

		query.getInputMap(WHEN_FOCUSED).put(keyStroke, name);
		query.getActionMap().put(name, new AbstractAction() {

			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(ActionEvent e) {
				action.run();
			}
		});
	}

	private static JToggleButton toggle(String text, String tooltip) {

		JToggleButton button = ViewerUi.compact(new JToggleButton(text));
		button.setToolTipText(tooltip);
		button.setFocusable(false);
		button.setMargin(new Insets(1, 4, 1, 4));

		return button;
	}

	private static JButton button(String text, String tooltip) {

		JButton button = ViewerUi.compact(new JButton(text));
		button.setToolTipText(tooltip);
		button.setFocusable(false);
		button.setMargin(new Insets(1, 4, 1, 4));

		return button;
	}
}
