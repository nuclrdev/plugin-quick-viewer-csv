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

import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import dev.nuclr.plugin.core.quick.viewer.csv.ViewerUi.Palette;

/**
 * What the pane shows instead of a grid: an empty file, a file that could not
 * be read, or the moment before the first one has been.
 */
final class MessageCard extends JPanel {

	private static final long serialVersionUID = 1L;

	private final JLabel title = new JLabel("", SwingConstants.CENTER);
	private final JLabel detail = new JLabel("", SwingConstants.CENTER);
	private final JLabel hint = new JLabel("", SwingConstants.CENTER);

	MessageCard() {

		super(new GridBagLayout());
		setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = 0;
		constraints.gridy = GridBagConstraints.RELATIVE;
		constraints.insets = new Insets(2, 0, 2, 0);

		add(title, constraints);
		add(detail, constraints);
		add(hint, constraints);
	}

	/**
	 * @param titleText  the headline, e.g. why there is no grid
	 * @param detailText the file name or the error, may be {@code null}
	 * @param hintText   what the user can do about it, may be {@code null}
	 */
	void show(String titleText, String detailText, String hintText) {

		title.setText(titleText == null ? "" : titleText);
		detail.setText(detailText == null ? "" : ViewerUi.ellipsizeEnd(detailText, 120));
		hint.setText(hintText == null ? "" : hintText);

		detail.setVisible(!detail.getText().isEmpty());
		hint.setVisible(!hint.getText().isEmpty());
	}

	void applyTheme(Palette palette) {

		setBackground(palette.background());

		Font font = ViewerUi.defaultFont();
		title.setFont(font.deriveFont(Font.BOLD, font.getSize2D() + 1f));
		detail.setFont(font);
		hint.setFont(ViewerUi.smallFont());

		title.setForeground(palette.foreground());
		detail.setForeground(palette.muted());
		hint.setForeground(palette.muted());
	}
}
