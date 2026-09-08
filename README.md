# 📊 CSV Quick Viewer

> Spreadsheet-style previews for delimited files inside [Nuclr Commander](https://nuclr.dev).

Hit **Ctrl+Q** on a `.csv` and get a real grid in the opposite pane — sortable, filterable, searchable, and ready to export the rows you pick. 🧮

## 🚀 What It Does

- 🧱 **Paged grid** — the page fills the pane by default, or pick a fixed 25 / 50 / 100 / 250 / 500 / 1000 rows
- 🔢 **Total row count** at the bottom, with the range on screen and how many rows a filter removed
- ↕️ **Click-to-sort** headers — ascending, descending, back to file order; numbers sort as numbers, blanks stay last
- 🔎 **Filtering** — one query across every column, plus a filter field under each column heading, as plain text or a regular expression, case-sensitive or not
- 🔦 **Ctrl+F search** that highlights every matching cell, counts them, and jumps to the right page
- 📤 **Export** the rows you selected — across pages — to a CSV file or the clipboard
- 🧭 **Separator sniffing** for comma, semicolon, tab and pipe, overridable from the right-click menu
- 🛡️ **Bounded loading**: at most 200,000 rows / 24M characters, so a runaway file cannot freeze the pane
- ⛔ Cancellation-aware, so moving the cursor down a folder never leaves stale content behind

## 🎯 Supported File Types

| Extension | Sniffed as |
|-----------|-----------|
| `csv` | comma, semicolon, tab or pipe — whichever parses most consistently |
| `tsv`, `tab` | tab |

UTF-8 (with or without a BOM) and UTF-16 with a BOM are decoded; anything undecodable is replaced rather than refused. A file with NUL bytes is handed back so another viewer can take it.

## ⌨️ Keys

| Key | Action |
|-----|--------|
| `Ctrl+F` | Open the find bar |
| `F3` / `Shift+F3` | Next / previous match |
| `Esc` | Close the find bar, then clear the selection, then clear the filters |
| `Ctrl+C` | Copy the selected rows (or everything in view) as CSV |
| `Ctrl+A` | Select every row in view, across pages |
| `Alt+←` / `Alt+→` | Previous / next page |
| `Alt+Home` / `Alt+End` | First / last page |

## 📥 Installation

Copy the signed plugin archive and detached signature into the Nuclr Commander `plugins/` directory:

```text
quick-view-csv-<version>.zip
quick-view-csv-<version>.zip.sig
```

Nuclr Commander verifies the RSA-SHA256 signature against `nuclr-cert.pem` on load. The plugin becomes available immediately without a restart.

## 🧠 How It Works

```text
CsvQuickViewProvider  → claims .csv/.tsv/.tab and owns the plugin lifecycle
CsvFileSupport        → name-only selection, so remote resources are never downloaded to answer
CsvLoader / CsvParser → lenient RFC 4180 parsing inside a fixed row and character budget
CsvDialect / Numbers  → separator sniffing, header detection, the numbers people put in spreadsheets
CsvIndex / Paging     → the view: which rows the filter kept, in which order, sliced into pages
CsvSearch             → every matching cell in the current view, packed two-per-long
CsvExporter           → RFC 4180 output to a file or the clipboard
CsvQuickViewPanel     → the grid, the bars and the threading between them
```

### The view

The file is parsed once and never copied again. Filtering and sorting produce an array of row indices — the *view* — and a page is a slice of that array. That is why:

- the `#` gutter still shows the row's place in the **file** after sorting and filtering,
- rows selected on one page survive paging, sorting and re-filtering, and export in display order,
- a 200,000-row file re-filters on each keystroke without the pane stalling.

### Threading

Parsing happens on the thread the host opens the file on. Filtering, sorting and searching run inline for small files and on virtual threads beyond 20,000 rows, each keyed by a generation counter so a result that arrives after the user has typed on is dropped rather than shown. Everything that touches the grid runs on the event-dispatch thread.

## 🧪 Building

```bash
mvn clean verify
```

Produces `target/quick-view-csv-<version>.zip` and its detached `.zip.sig`. Signing reads `jarsigner.storepass` from the Maven settings or `-D` properties; no credentials live in this repository.

## 📄 License

Apache License 2.0 — see [LICENSE](LICENSE).
