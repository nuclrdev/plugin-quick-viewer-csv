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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

class CsvThumbnailTest {

	private final CsvQuickViewProvider provider = new CsvQuickViewProvider();

	@Test
	void drawsALandscapeSheetWithinTheBoxBeforeInit() {

		assertTrue(provider.supportsThumbnails());

		BufferedImage image = provider.thumbnail(new StringResource("people.csv", "name;age\nada;36\nalan;41\n"),
				200, 200, new AtomicBoolean());

		assertNotNull(image);
		assertEquals(200, image.getWidth());
		assertEquals(150, image.getHeight());
	}

	@Test
	void shadesTheHeaderRowOnlyWhenThereIsOne() {

		List<List<String>> rows = List.of(List.of("name", "age"), List.of("ada", "36"));

		BufferedImage withHeader = CsvThumbnail.draw(rows, true, 200, 150, new AtomicBoolean());
		BufferedImage withoutHeader = CsvThumbnail.draw(rows, false, 200, 150, new AtomicBoolean());

		assertNotNull(withHeader);
		assertNotNull(withoutHeader);
		// Sample the first row's fill well clear of the text and grid lines.
		int x = withHeader.getWidth() - 3;
		assertTrue(withHeader.getRGB(x, 2) != withoutHeader.getRGB(x, 2));
	}

	@Test
	void returnsNullForBinaryEmptyForeignOrCancelled() {

		assertNull(provider.thumbnail(new StringResource("blob.csv", "a,b\u0000c\n"), 100, 100, new AtomicBoolean()));
		assertNull(provider.thumbnail(new StringResource("empty.csv", ""), 100, 100, new AtomicBoolean()));
		assertNull(provider.thumbnail(new StringResource("people.txt", "a,b\n"), 100, 100, new AtomicBoolean()));
		assertNull(provider.thumbnail(new StringResource("people.csv", "a,b\n"), 100, 100, new AtomicBoolean(true)));
	}
}
