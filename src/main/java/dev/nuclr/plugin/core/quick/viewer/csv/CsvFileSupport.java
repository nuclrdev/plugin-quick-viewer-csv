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

import java.nio.file.Path;
import java.util.Set;

import dev.nuclr.platform.plugin.NuclrResource;

/**
 * Which resources this viewer claims.
 *
 * <p>Selection is by name alone. The host calls it for every entry the cursor
 * lands on, including entries of a bucket, a repository or an archive that only
 * offer a stream, so it must not read the file: a delimiter sniff is worth
 * nothing here when answering for it could mean downloading the object.
 */
final class CsvFileSupport {

	private static final Set<String> EXTENSIONS = Set.of("csv", "tsv", "tab");

	private CsvFileSupport() {
	}

	/**
	 * Whether this plugin should preview {@code resource}.
	 *
	 * @param resource the resource being selected; may be {@code null}
	 * @return {@code true} for a readable delimited-text file
	 */
	static boolean supports(NuclrResource resource) {

		if (resource == null || resource.isFolder() || !resource.isReadable()) {
			return false;
		}

		return EXTENSIONS.contains(extension(name(resource)));
	}

	/** The resource's own name, falling back to its file name for resources that carry none. */
	static String name(NuclrResource resource) {

		if (resource == null) {
			return null;
		}

		if (resource.getName() != null && !resource.getName().isBlank()) {
			return resource.getName();
		}

		Path path = resource.getPath();
		if (path == null) {
			return null;
		}

		return path.getFileName() != null ? path.getFileName().toString() : path.toString();
	}

	/** The lower-case extension of a file name, or an empty string when it has none. */
	static String extension(String fileName) {

		if (fileName == null || fileName.isBlank()) {
			return "";
		}

		int dot = fileName.lastIndexOf('.');
		return dot > 0 ? fileName.substring(dot + 1).toLowerCase() : "";
	}

	/** The file name without its extension, used to name an export next to its source. */
	static String baseName(String fileName) {

		if (fileName == null || fileName.isBlank()) {
			return "export";
		}

		int dot = fileName.lastIndexOf('.');
		return dot > 0 ? fileName.substring(0, dot) : fileName;
	}
}
