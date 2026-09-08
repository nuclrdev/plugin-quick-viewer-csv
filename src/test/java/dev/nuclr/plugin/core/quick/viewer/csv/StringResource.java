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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.OpenOption;

import dev.nuclr.platform.plugin.NuclrResource;

/**
 * An in-memory resource, so the loader can be tested on exactly the bytes a
 * case is about - including the ones no file on disk should have.
 */
final class StringResource extends NuclrResource {

	private static final long serialVersionUID = 1L;

	private final transient byte[] content;

	StringResource(String name, String content) {
		this(name, content.getBytes(StandardCharsets.UTF_8));
	}

	StringResource(String name, byte[] content) {

		super(null);

		this.content = content;
		setUuid(name);
		setName(name);
		setFullPath(name);
		setLength(content.length);
		setReadable(true);
	}

	@Override
	public InputStream openInputStream(OpenOption... options) {
		return new ByteArrayInputStream(content);
	}
}
