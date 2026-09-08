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

import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

class CsvQuickViewProviderTest {

	@Test
	void claimsOnlyDelimitedTextFiles() {

		CsvQuickViewProvider provider = new CsvQuickViewProvider();

		assertTrue(provider.supports(new StringResource("people.csv", "a,b\n")));
		assertFalse(provider.supports(new StringResource("people.txt", "a,b\n")));
		assertFalse(provider.supports(null));
	}

	@Test
	void buildsItsPanelLazilyAndKeepsTheSameOne() throws Exception {

		CsvQuickViewProvider provider = new CsvQuickViewProvider();

		JComponent[] panels = new JComponent[2];
		SwingUtilities.invokeAndWait(() -> {
			panels[0] = provider.panel();
			panels[1] = provider.panel();
		});

		assertNotNull(panels[0]);
		assertSame(panels[0], panels[1]);
	}

	@Test
	void tracksTheOpenResourceAndForgetsItOnClose() throws Exception {

		CsvQuickViewProvider provider = new CsvQuickViewProvider();
		StringResource resource = new StringResource("people.csv", "name\nada\n");

		SwingUtilities.invokeAndWait(() -> provider.openResource(resource, new AtomicBoolean()));

		assertSame(resource, provider.getCurrentResource());
		assertEquals("Quick View: people.csv", provider.getWindowTitle());

		provider.closeResource();
		SwingUtilities.invokeAndWait(() -> {
			// Let the panel's own clean-up run.
		});

		assertEquals(null, provider.getCurrentResource());
	}

	@Test
	void cancelsThePreviousOpenWhenTheCursorMovesOn() throws Exception {

		CsvQuickViewProvider provider = new CsvQuickViewProvider();
		AtomicBoolean first = new AtomicBoolean();

		SwingUtilities.invokeAndWait(() -> {
			provider.openResource(new StringResource("a.csv", "name\nada\n"), first);
			provider.openResource(new StringResource("b.csv", "name\nalan\n"), new AtomicBoolean());
		});

		assertTrue(first.get(), "the superseded open should have been cancelled");
	}

	@Test
	void isNotFocusedBeforeItIsShown() throws Exception {

		CsvQuickViewProvider provider = new CsvQuickViewProvider();

		SwingUtilities.invokeAndWait(provider::panel);

		assertFalse(provider.isFocused());
		assertFalse(provider.onFocusGained());
	}

	@Test
	void identifiesItselfWithTheIdFromTheManifest() {
		assertEquals("dev.nuclr.plugin.core.quickviewer.csv", new CsvQuickViewProvider().uuid());
	}
}
