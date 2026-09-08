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

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

/**
 * Shows a file in the desktop's own file manager, with the file selected.
 *
 * <p>Selecting the file rather than merely opening its folder is the point: an
 * export lands next to a hundred other files, and the answer to "where did it
 * go?" should be visible without reading a path. Each platform has its own way
 * of being asked, and none of them is {@link Desktop} - which cannot select a
 * file at all on Windows - so the desktop API is only the last resort.
 *
 * <p>Everything here starts a process, so call it off the event-dispatch thread.
 */
@Slf4j
final class FileReveal {

	/** How long to wait for the Linux file-manager call before falling back. */
	private static final int DBUS_TIMEOUT_SECONDS = 3;

	private FileReveal() {
	}

	/** The three ways of asking, as far as this class is concerned. */
	enum Platform {

		WINDOWS,
		MACOS,
		/** Linux, BSD, anything else with a freedesktop.org file manager. */
		OTHER;

		static Platform current() {
			return of(System.getProperty("os.name"));
		}

		static Platform of(String osName) {

			String name = osName == null ? "" : osName.toLowerCase(Locale.ROOT);

			// Darwin before Windows, and "starts with" rather than "contains":
			// "darwin" contains "win", and a Mac must not be sent to explorer.exe.
			if (name.contains("mac") || name.contains("darwin")) {
				return MACOS;
			}
			if (name.startsWith("win")) {
				return WINDOWS;
			}

			return OTHER;
		}
	}

	/**
	 * The command that opens a file manager with {@code file} selected.
	 *
	 * <p>On Linux this is the freedesktop.org {@code ShowItems} call, which every
	 * modern file manager (Nautilus, Dolphin, Nemo, Thunar, …) answers on the
	 * session bus. {@link #fallbackCommand} covers the desktops that do not.
	 *
	 * @param platform the desktop being asked
	 * @param file     the file to select
	 * @return the command and its arguments
	 */
	static List<String> command(Platform platform, Path file) {

		Path absolute = file.toAbsolutePath();

		return switch (platform) {
			// The switch and the path must be separate arguments. Joined into one,
			// Explorer silently ignores any path containing a space (and opens the
			// user's Documents folder instead) - verified on Windows 11, where the
			// joined form fails and this one selects the file.
			case WINDOWS -> List.of("explorer.exe", "/select,", absolute.toString());
			case MACOS -> List.of("open", "-R", absolute.toString());
			case OTHER -> List.of("dbus-send",
					"--session",
					"--dest=org.freedesktop.FileManager1",
					"--type=method_call",
					"/org/freedesktop/FileManager1",
					"org.freedesktop.FileManager1.ShowItems",
					"array:string:" + absolute.toUri(),
					"string:");
		};
	}

	/** Opening the containing folder, for a desktop with no {@code FileManager1} service. */
	static List<String> fallbackCommand(Path file) {

		Path parent = file.toAbsolutePath().getParent();
		return parent == null ? List.of() : List.of("xdg-open", parent.toString());
	}

	/**
	 * Shows {@code file} in the file manager.
	 *
	 * @param file the file to select; may be {@code null}
	 * @return {@code true} if a file manager was launched, {@code false} if the
	 *         platform gave the caller nothing to work with - the caller then owes
	 *         the user some other sign that the file was written
	 */
	static boolean reveal(Path file) {

		if (file == null) {
			return false;
		}

		Platform platform = Platform.current();

		// The freedesktop call reports whether a file manager actually answered, so
		// it is worth waiting the moment it takes; explorer and open do not (explorer
		// even exits non-zero on success), so those are fire-and-forget.
		if (start(command(platform, file), platform == Platform.OTHER)) {
			return true;
		}

		if (platform == Platform.OTHER && start(fallbackCommand(file), false)) {
			return true;
		}

		return desktopFallback(file);
	}

	// -------------------------------------------------------------------------

	private static boolean start(List<String> command, boolean waitForExit) {

		if (command.isEmpty()) {
			return false;
		}

		try {
			Process process = new ProcessBuilder(command).start();

			if (!waitForExit) {
				return true;
			}

			return process.waitFor(DBUS_TIMEOUT_SECONDS, TimeUnit.SECONDS) && process.exitValue() == 0;

		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		} catch (Exception e) {
			log.debug("Could not run {}: {}", command.get(0), e.getMessage());
			return false;
		}
	}

	/**
	 * The desktop API, which selects the file where it can and otherwise just
	 * opens the folder. Reached only when the platform's own command is missing.
	 */
	private static boolean desktopFallback(Path file) {

		if (!Desktop.isDesktopSupported()) {
			return false;
		}

		Desktop desktop = Desktop.getDesktop();

		try {
			if (desktop.isSupported(Desktop.Action.BROWSE_FILE_DIR)) {
				desktop.browseFileDirectory(file.toAbsolutePath().toFile());
				return true;
			}

			Path parent = file.toAbsolutePath().getParent();
			if (parent != null && desktop.isSupported(Desktop.Action.OPEN)) {
				desktop.open(new File(parent.toString()));
				return true;
			}
		} catch (Exception e) {
			log.debug("Could not open a file manager for {}: {}", file, e.getMessage());
		}

		return false;
	}
}
