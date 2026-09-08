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
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.HeadlessException;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.PatternSyntaxException;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;

import dev.nuclr.platform.NuclrSettings;
import dev.nuclr.platform.NuclrThemeScheme;
import dev.nuclr.platform.plugin.NuclrResource;
import dev.nuclr.plugin.core.quick.viewer.csv.ViewerUi.Palette;
import lombok.extern.slf4j.Slf4j;

/**
 * The viewer itself: a paged grid over one file, with the filter bar above it,
 * the find bar between them and the page controls below.
 *
 * <p>The file is parsed once, on the thread the host opens it on, and is never
 * copied again: filtering and sorting produce an array of row indices (the
 * view), and a page is a slice of that array. That is what lets a 200,000-row
 * file re-sort and re-filter without the pane stalling, and it is why the row
 * numbers in the gutter still refer to the file after both.
 *
 * <p>Everything that touches the grid runs on the event-dispatch thread; the
 * scans that do not (parse, filter, sort, search) run on virtual threads and
 * are keyed by a generation counter, so a result that arrives after the user
 * has typed on is dropped rather than shown.
 */
@Slf4j
final class CsvQuickViewPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	/** Below this many rows a filter or sort is imperceptible, so it runs inline and the grid never flickers. */
	private static final int SYNC_ROWS = 20_000;

	/** How long typing settles before the filter runs. */
	private static final int FILTER_DEBOUNCE_MS = 250;

	/** How long a resize settles before the fitted page size is recalculated. */
	private static final int RESIZE_DEBOUNCE_MS = 200;

	private static final int MIN_COLUMN_WIDTH = 48;
	private static final int MAX_COLUMN_WIDTH = 320;
	private static final int COLUMN_PADDING = 18;

	/** Rows measured to choose column widths; enough to be representative, few enough to be free. */
	private static final int WIDTH_SAMPLE_ROWS = 40;

	/** Height assumed for the fitted page size before the pane has been laid out. */
	private static final int ASSUMED_VIEWPORT_HEIGHT = 600;

	/**
	 * Stands for "not sorted at all", which is not the same as sorting by the
	 * gutter: the gutter is column -1 and does sort, by the file's own order.
	 */
	private static final int NO_SORT = Integer.MIN_VALUE;

	private static final String CARD_TABLE = "table";
	private static final String CARD_MESSAGE = "message";

	private static final String SETTINGS_NAMESPACE = "dev.nuclr.plugin.core.quickviewer.csv";
	private static final String SETTING_PAGE_SIZE = "pageSizeChoice";
	private static final String SETTING_COLUMN_FILTERS = "columnFilters";
	private static final String SETTING_EXPORT_HEADER = "exportHeader";

	private final CsvTableModel model = new CsvTableModel();

	/**
	 * A JTable puts its own header back into the enclosing scroll pane every time
	 * it becomes displayable, which would throw away the composite header the
	 * per-column filter row lives in. Re-installing it here is the supported way
	 * to keep a header of one's own.
	 */
	private final JTable table = new JTable(model) {

		private static final long serialVersionUID = 1L;

		@Override
		protected void configureEnclosingScrollPane() {
			super.configureEnclosingScrollPane();
			installColumnHeader();
		}
	};
	private final CsvCellRenderer cellRenderer = new CsvCellRenderer(model);
	private final CsvHeaderRenderer headerRenderer = new CsvHeaderRenderer();
	private final JScrollPane scroll = new JScrollPane(table);
	private final JPanel columnHeader = new JPanel(new BorderLayout());
	private final ColumnFilterRow columnFilterRow;
	private final FilterBar filterBar;
	private final FindBar findBar;
	private final PaginationBar paginationBar;
	private final MessageCard messageCard = new MessageCard();
	private final CardLayout cards = new CardLayout();
	private final JPanel deck = new JPanel(cards);

	/** Assigned here rather than in the constructor so the listeners built there can already restart them. */
	private final Timer filterDebounce = new Timer(FILTER_DEBOUNCE_MS, e -> recomputeView(false));
	private final Timer resizeDebounce = new Timer(RESIZE_DEBOUNCE_MS, e -> refreshFittedPage());

	/** Row indices of the file that the user has ticked, in the order they were ticked. */
	private final Set<Integer> selectedRows = new LinkedHashSet<>();

	private final AtomicLong viewGeneration = new AtomicLong();
	private final AtomicLong searchGeneration = new AtomicLong();
	private volatile AtomicBoolean viewCancelled = new AtomicBoolean();
	private volatile AtomicBoolean searchCancelled = new AtomicBoolean();

	private NuclrSettings settings;
	private NuclrResource resource;
	private String fileName = "";

	private CsvData data;
	private int[] view = new int[0];
	private FilterSpec filter = FilterSpec.NONE;
	private int sortColumn = NO_SORT;
	private boolean sortDescending;
	private int pageSizeChoice = Paging.FIT;
	private int page;

	private CsvSearch.Hits hits = CsvSearch.Hits.NONE;
	private TextMatcher searchMatcher;
	private int currentHit = -1;

	private boolean exportIncludesHeader = true;
	private boolean syncingSelection;

	private Palette palette = ViewerUi.palette(null);
	private NuclrThemeScheme theme;

	CsvQuickViewPanel() {

		super(new BorderLayout());

		filterBar = new FilterBar(new FilterBar.Listener() {

			@Override
			public void filterChanged(String query) {
				filter = filter.withQuery(query);
				filterDebounce.restart();
			}

			@Override
			public void modeChanged(boolean regex, boolean caseSensitive) {
				filter = filter.withRegex(regex).withCaseSensitive(caseSensitive);
				filterDebounce.restart();
			}

			@Override
			public void columnFiltersToggled(boolean visible) {
				setColumnFiltersVisible(visible);
				store(SETTING_COLUMN_FILTERS, visible);
			}

			@Override
			public void exportRequested(Component anchor) {
				JPopupMenu menu = exportMenu();
				menu.show(anchor, 0, anchor.getHeight());
			}
		});

		findBar = new FindBar(new FindBar.Listener() {

			@Override
			public void queryChanged(String query, boolean regex, boolean caseSensitive) {
				setSearchQuery(query, regex, caseSensitive);
			}

			@Override
			public void next() {
				moveToHit(true);
			}

			@Override
			public void previous() {
				moveToHit(false);
			}

			@Override
			public void closed() {
				clearSearch();
			}
		});

		paginationBar = new PaginationBar(new PaginationBar.Listener() {

			@Override
			public void pageRequested(int requested) {
				goToPage(requested);
			}

			@Override
			public void pageSizeChoiceChanged(int choice) {
				pageSizeChoice = choice;
				store(SETTING_PAGE_SIZE, choice);
				refreshPage();
			}
		});

		columnFilterRow = new ColumnFilterRow(table, (query, dataColumn) -> {
			filter = filter.withColumnQuery(dataColumn, query);
			filterDebounce.restart();
		});

		filterDebounce.setRepeats(false);
		resizeDebounce.setRepeats(false);

		configureTable();
		layoutComponents();
		installKeyBindings();
	}

	// ── Opening and closing ──────────────────────────────────────────────────

	/**
	 * Parses {@code item} and shows it. Called on the host's loading thread.
	 *
	 * @param item      the file to preview
	 * @param cancelled set by the host when the user moves on
	 * @return {@code true} if the file was taken on (including when all that can
	 *         be shown is why it could not be read), {@code false} to let another
	 *         viewer have it
	 */
	boolean load(NuclrResource item, AtomicBoolean cancelled) {

		String name = CsvFileSupport.name(item);

		CsvData loaded;
		try {
			loaded = CsvLoader.load(item, null, null, cancelled);
		} catch (CsvLoader.NotTabularException e) {
			log.debug("Not delimited text, leaving it to another viewer: {}", name);
			return false;
		} catch (Exception e) {
			log.warn("Failed to read [{}]: {}", name, e.getMessage());
			showMessage("Couldn’t read this file", name, e.getMessage(), cancelled);
			return true;
		}

		if (loaded == null || cancelled.get()) {
			return false;
		}

		SwingUtilities.invokeLater(() -> {
			if (!cancelled.get()) {
				install(item, name, loaded);
			}
		});

		return true;
	}

	/** Drops the file and everything derived from it, so nothing is held once the preview is closed. */
	void clear() {

		SwingUtilities.invokeLater(() -> {
			cancelBackgroundWork();
			filterDebounce.stop();
			resizeDebounce.stop();
			findBar.close();
			data = null;
			resource = null;
			view = new int[0];
			selectedRows.clear();
			hits = CsvSearch.Hits.NONE;
			searchMatcher = null;
			currentHit = -1;
			cellRenderer.setHits(CsvSearch.Hits.NONE, -1, -1);
			model.setData(null);
			columnFilterRow.rebuild();
			paginationBar.setStatus(" ");
			showMessageNow("Nothing to preview", null, null);
		});
	}

	/** The scroll pane the grid lives in; a test seam, like {@link #tableModel()}. */
	JScrollPane scrollPane() {
		return scroll;
	}

	/** The header component that carries the column titles and the filter row; a test seam. */
	JPanel columnHeaderView() {
		return columnHeader;
	}

	/** The grid itself; a test seam, like {@link #tableModel()}. */
	JTable tableComponent() {
		return table;
	}

	/** The page currently on screen; the seam the panel's own tests read it through. */
	CsvTableModel tableModel() {
		return model;
	}

	/** The rows the filter and sort left, in display order; a test seam like {@link #tableModel()}. */
	int[] currentView() {
		return view;
	}

	void setSettings(NuclrSettings settings) {

		this.settings = settings;
		if (settings == null) {
			return;
		}

		// Reading is I/O and can happen on whatever thread the host initialises on;
		// the state it restores belongs to the event-dispatch thread with the rest.
		int storedPageSize = settings.getOrDefault(SETTINGS_NAMESPACE, SETTING_PAGE_SIZE, Paging.FIT);
		boolean storedExportHeader = settings.getOrDefault(SETTINGS_NAMESPACE, SETTING_EXPORT_HEADER, Boolean.TRUE);
		boolean storedColumnFilters = settings.getOrDefault(SETTINGS_NAMESPACE, SETTING_COLUMN_FILTERS, Boolean.FALSE);

		SwingUtilities.invokeLater(() -> {
			pageSizeChoice = storedPageSize;
			exportIncludesHeader = storedExportHeader;
			paginationBar.setPageSizeChoice(storedPageSize);
			filterBar.setColumnFiltersVisible(storedColumnFilters);
			setColumnFiltersVisible(storedColumnFilters);
		});
	}

	/** Puts the caret in the grid, so the arrow keys and Ctrl+F work without a click first. */
	boolean focusTable() {
		return table.isShowing() && table.requestFocusInWindow();
	}

	/** Whether the keyboard is in this viewer - a text field of its own counts, not just the grid. */
	boolean isFocusOwnerWithin() {

		Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
		return owner != null && SwingUtilities.isDescendingFrom(owner, this);
	}

	/**
	 * Re-reads every colour the viewer paints with.
	 *
	 * <p>The host installs the look-and-feel, folds the scheme's overrides into
	 * UIManager and restyles the component tree <em>before</em> it tells plugins
	 * about the new theme, so reading UIManager here is reading the theme that is
	 * actually in force.
	 */
	void applyTheme(NuclrThemeScheme theme) {

		this.theme = theme;
		palette = ViewerUi.palette(theme);

		setBackground(palette.background());
		deck.setBackground(palette.background());
		columnHeader.setBackground(palette.header());

		table.setBackground(palette.background());
		table.setForeground(palette.foreground());
		table.setGridColor(palette.grid());
		table.setSelectionBackground(palette.selection());
		table.setSelectionForeground(palette.selectionForeground());
		table.setFont(ViewerUi.defaultFont());
		table.setRowHeight(rowHeight());

		JTableHeader header = table.getTableHeader();
		header.setBackground(palette.header());
		header.setForeground(palette.headerForeground());
		header.setFont(ViewerUi.defaultFont());

		scroll.setBackground(palette.background());
		scroll.getViewport().setBackground(palette.background());
		scroll.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, palette.grid()));

		cellRenderer.applyTheme(palette);
		headerRenderer.applyTheme(palette);
		columnFilterRow.applyTheme(palette);
		filterBar.applyTheme(palette);
		findBar.applyTheme(palette);
		paginationBar.applyTheme(palette);
		messageCard.applyTheme(palette);

		repaint();
	}

	@Override
	public void updateUI() {

		super.updateUI();

		// updateComponentTreeUI reaches this panel whenever the look-and-feel is
		// swapped, including for a panel detached from the window that the theme
		// broadcast may not reach. The colours here are explicit (not UIResource),
		// so nothing else would re-derive them. Deferred because this runs during
		// the tree walk that is still reinstalling the UI delegates below us - and
		// during the superclass constructor, where the fields are not there yet.
		if (deck != null) {
			SwingUtilities.invokeLater(() -> applyTheme(theme));
		}
	}

	// ── Building the UI ──────────────────────────────────────────────────────

	private void configureTable() {

		table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		table.setAutoCreateColumnsFromModel(true);
		table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		// Rows, never cells. Do not "clarify" this with setCellSelectionEnabled(false):
		// that turns row selection off as well, and isCellSelected then answers false
		// for every cell - rows still go into the selection model, and nothing on
		// screen ever looks selected.
		table.setRowSelectionAllowed(true);
		table.setColumnSelectionAllowed(false);
		table.setShowGrid(true);
		table.setIntercellSpacing(new Dimension(0, 1));
		table.setFillsViewportHeight(true);
		table.setDefaultRenderer(String.class, cellRenderer);
		table.setDefaultRenderer(Object.class, cellRenderer);
		table.setRowHeight(rowHeight());

		JTableHeader header = table.getTableHeader();
		header.setReorderingAllowed(false);
		header.setDefaultRenderer(headerRenderer);
		header.addMouseListener(new MouseAdapter() {

			@Override
			public void mouseClicked(MouseEvent e) {
				if (SwingUtilities.isLeftMouseButton(e)) {
					sortByHeaderAt(e);
				}
			}
		});

		table.getSelectionModel().addListSelectionListener(e -> {
			if (!syncingSelection && !e.getValueIsAdjusting()) {
				captureSelection();
			}
		});

		table.addMouseListener(new MouseAdapter() {

			@Override
			public void mousePressed(MouseEvent e) {
				showContextMenu(e);
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				showContextMenu(e);
			}
		});

		scroll.getViewport().addComponentListener(new ComponentAdapter() {

			@Override
			public void componentResized(ComponentEvent e) {
				// Only the fitted page size depends on the height, and it should not be
				// recomputed for every pixel of a drag.
				if (pageSizeChoice == Paging.FIT) {
					resizeDebounce.restart();
				}
			}
		});
	}

	private void layoutComponents() {

		columnFilterRow.setVisible(false);
		installColumnHeader();
		scroll.getViewport().setBackground(palette.background());

		JPanel top = new JPanel(new BorderLayout());
		top.add(filterBar, BorderLayout.NORTH);
		top.add(findBar, BorderLayout.SOUTH);

		JPanel grid = new JPanel(new BorderLayout());
		grid.add(top, BorderLayout.NORTH);
		grid.add(scroll, BorderLayout.CENTER);
		grid.add(paginationBar, BorderLayout.SOUTH);

		deck.add(grid, CARD_TABLE);
		deck.add(messageCard, CARD_MESSAGE);
		add(deck, BorderLayout.CENTER);

		messageCard.show("Loading…", null, null);
		cards.show(deck, CARD_MESSAGE);
	}

	/**
	 * Puts the table header and the column filter row back into the scroll pane's
	 * header, as one component. Adding the header here takes it back off the
	 * viewport the table just gave it to.
	 */
	void installColumnHeader() {

		columnHeader.add(table.getTableHeader(), BorderLayout.NORTH);
		columnHeader.add(columnFilterRow, BorderLayout.SOUTH);

		if (scroll.getColumnHeader() == null || scroll.getColumnHeader().getView() != columnHeader) {
			scroll.setColumnHeaderView(columnHeader);
		}
	}

	private void installKeyBindings() {

		int menuMask = menuShortcutMask();

		bind(this, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT,
				KeyStroke.getKeyStroke(KeyEvent.VK_F, menuMask), "csvFind", findBar::open);
		bind(this, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT,
				KeyStroke.getKeyStroke(KeyEvent.VK_F3, 0), "csvFindNext", () -> moveToHit(true));
		bind(this, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT,
				KeyStroke.getKeyStroke(KeyEvent.VK_F3, KeyEvent.SHIFT_DOWN_MASK), "csvFindPrevious",
				() -> moveToHit(false));
		bind(this, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT,
				KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "csvEscape", this::onEscape);

		// Alt rather than Ctrl for paging: Ctrl+Home/End and the arrows already move
		// the caret inside the grid, and taking those would break the table.
		bind(this, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT,
				KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, KeyEvent.ALT_DOWN_MASK), "csvPreviousPage",
				() -> goToPage(page - 1));
		bind(this, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT,
				KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, KeyEvent.ALT_DOWN_MASK), "csvNextPage",
				() -> goToPage(page + 1));
		bind(this, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT,
				KeyStroke.getKeyStroke(KeyEvent.VK_HOME, KeyEvent.ALT_DOWN_MASK), "csvFirstPage",
				() -> goToPage(0));
		bind(this, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT,
				KeyStroke.getKeyStroke(KeyEvent.VK_END, KeyEvent.ALT_DOWN_MASK), "csvLastPage",
				() -> goToPage(Integer.MAX_VALUE));

		// On the table's own map so they replace its defaults, which copy the visible
		// cells and select the visible page rather than what the user is exporting.
		bind(table, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT,
				KeyStroke.getKeyStroke(KeyEvent.VK_C, menuMask), "csvCopy",
				() -> copyToClipboard(selectionOrView()));
		bind(table, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT,
				KeyStroke.getKeyStroke(KeyEvent.VK_A, menuMask), "csvSelectAll", this::selectAllRows);
	}

	/**
	 * Ctrl on Windows and Linux, Command on macOS - and Ctrl again when there is
	 * no toolkit to ask, which is only ever a headless test.
	 */
	private static int menuShortcutMask() {

		try {
			return Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
		} catch (HeadlessException e) {
			return InputEvent.CTRL_DOWN_MASK;
		}
	}

	private static void bind(JComponent component, int condition, KeyStroke keyStroke, String name, Runnable action) {

		component.getInputMap(condition).put(keyStroke, name);
		component.getActionMap().put(name, new AbstractAction() {

			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(ActionEvent e) {
				action.run();
			}
		});
	}

	// ── Showing a file ───────────────────────────────────────────────────────

	private void install(NuclrResource item, String name, CsvData loaded) {

		cancelBackgroundWork();

		this.resource = item;
		this.fileName = name == null ? "" : name;
		this.data = loaded;

		// A new file keeps how the user likes to search (regex, case) but not what
		// they were searching for: the queries belonged to the previous file.
		filter = filter.withQuery("").withoutColumnQueries();
		filterBar.reset();
		filterBar.setInvalid(null);
		findBar.close();
		clearSearch();

		sortColumn = NO_SORT;
		sortDescending = false;
		page = 0;
		selectedRows.clear();
		headerRenderer.setSort(NO_SORT, false);

		model.setData(loaded);
		configureColumns();
		columnFilterRow.rebuild();
		columnFilterRow.applyTheme(palette);

		if (loaded.isEmpty()) {
			showMessageNow("No rows to show",
					fileName,
					loaded.hasHeaderRow() ? "The file has a header row and nothing under it." : null);
			return;
		}

		cards.show(deck, CARD_TABLE);
		applyView(CsvIndex.all(loaded), false);
	}

	/** Column widths from the content, so the first look at a file is already readable. */
	private void configureColumns() {

		if (data == null || table.getColumnCount() == 0) {
			return;
		}

		FontMetrics metrics = table.getFontMetrics(ViewerUi.defaultFont());
		int sampled = Math.min(data.rowCount(), WIDTH_SAMPLE_ROWS);
		int total = 0;

		for (int column = 0; column < table.getColumnCount(); column++) {

			TableColumn tableColumn = table.getColumnModel().getColumn(column);
			int dataColumn = CsvTableModel.dataColumn(column);

			int width;
			if (dataColumn < 0) {
				width = metrics.stringWidth(ViewerUi.count(data.rowCount())) + COLUMN_PADDING;
			} else {
				width = metrics.stringWidth(data.columnName(dataColumn)) + COLUMN_PADDING;
				for (int row = 0; row < sampled; row++) {
					width = Math.max(width, metrics.stringWidth(data.value(row, dataColumn)) + COLUMN_PADDING);
				}
				width = Math.max(MIN_COLUMN_WIDTH, Math.min(MAX_COLUMN_WIDTH, width));
			}

			tableColumn.setPreferredWidth(width);
			tableColumn.setWidth(width);
			total += width;
		}

		// A narrow file would otherwise leave the pane half empty: give the slack to
		// the last column so the grid fills the width it has.
		int available = scroll.getViewport().getWidth();
		int last = table.getColumnCount() - 1;
		if (available > 0 && total < available && last > 0) {
			TableColumn lastColumn = table.getColumnModel().getColumn(last);
			lastColumn.setPreferredWidth(lastColumn.getPreferredWidth() + available - total);
		}
	}

	// ── The view: filter, sort, page ─────────────────────────────────────────

	/**
	 * Rebuilds the view from the current filter and sort.
	 *
	 * @param keepPage whether to stay on the current page, which only makes sense
	 *                 when the rows themselves did not change
	 */
	private void recomputeView(boolean keepPage) {

		if (data == null) {
			return;
		}

		RowMatcher matcher;
		try {
			matcher = RowMatcher.compile(filter);
			filterBar.setInvalid(null);
		} catch (PatternSyntaxException e) {
			// Half-typed expressions are the normal case here, not an error worth a
			// dialog: mark the field and leave the previous view up.
			filterBar.setInvalid(e.getDescription());
			return;
		}

		long generation = viewGeneration.incrementAndGet();
		viewCancelled.set(true);
		AtomicBoolean cancelled = new AtomicBoolean();
		viewCancelled = cancelled;

		CsvData current = data;
		int column = sortColumn;
		boolean descending = sortDescending;

		if (current.rowCount() <= SYNC_ROWS) {
			int[] computed = CsvIndex.filter(current, matcher, cancelled);
			CsvIndex.sort(computed, current, column, descending);
			applyView(computed, keepPage);
			return;
		}

		paginationBar.setStatus("Filtering…");

		Thread.ofVirtual().name("csv-quickview-view").start(() -> {

			int[] computed = CsvIndex.filter(current, matcher, cancelled);
			if (computed == null || cancelled.get()) {
				return;
			}

			CsvIndex.sort(computed, current, column, descending);

			SwingUtilities.invokeLater(() -> {
				if (generation == viewGeneration.get() && data == current) {
					applyView(computed, keepPage);
				}
			});
		});
	}

	private void applyView(int[] computed, boolean keepPage) {

		view = computed != null ? computed : new int[0];
		if (!keepPage) {
			page = 0;
		}

		refreshPage();
		recomputeHits();
	}

	/** Re-slices the current page and repaints; everything that changes what is on screen ends here. */
	private void refreshPage() {

		int pageSize = pageSize();
		int pageCount = Paging.pageCount(view.length, pageSize);
		page = Paging.clampPage(page, pageCount);

		int first = Paging.firstRow(page, pageSize);
		int end = Paging.endRow(page, pageSize, view.length);

		model.setPage(view, first, end - first);
		restoreSelection();

		paginationBar.update(page, pageCount);
		updateStatus();
	}

	private void goToPage(int requested) {

		int pageCount = Paging.pageCount(view.length, pageSize());
		int target = Paging.clampPage(requested, pageCount);

		if (target != page) {
			page = target;
			refreshPage();
		}
	}

	/** Recomputes the page after a resize, which only matters while the page size is the fitted one. */
	private void refreshFittedPage() {

		if (pageSizeChoice == Paging.FIT && data != null) {
			refreshPage();
		}
	}

	private int pageSize() {
		return Paging.pageSize(pageSizeChoice, viewportHeight(), rowHeight());
	}

	private int viewportHeight() {

		int height = scroll.getViewport().getHeight();
		return height > 0 ? height : ASSUMED_VIEWPORT_HEIGHT;
	}

	private int rowHeight() {

		FontMetrics metrics = table.getFontMetrics(ViewerUi.defaultFont());
		return Math.max(16, metrics.getHeight() + 6);
	}

	private void updateStatus() {

		if (data == null) {
			paginationBar.setStatus(" ");
			return;
		}

		int pageSize = pageSize();
		int first = Paging.firstRow(page, pageSize);
		int end = Paging.endRow(page, pageSize, view.length);

		StringBuilder status = new StringBuilder();
		if (view.length == 0) {
			status.append("No matching rows");
		} else {
			status.append("Rows ").append(ViewerUi.count(first + 1L)).append('–').append(ViewerUi.count(end))
					.append(" of ").append(ViewerUi.count(view.length));
		}

		if (view.length != data.rowCount()) {
			status.append(" (filtered from ").append(ViewerUi.count(data.rowCount())).append(')');
		}

		if (!selectedRows.isEmpty()) {
			status.append("  ·  ").append(ViewerUi.count(selectedRows.size())).append(" selected");
		}

		if (data.isTruncated()) {
			status.append("  ·  first ").append(ViewerUi.count(data.rowCount())).append(" rows of a ")
					.append(ViewerUi.humanSize(data.byteLength())).append(" file");
		}

		status.append("  ·  ").append(data.dialect().delimiterName()).append("-separated");

		paginationBar.setStatus(status.toString());
	}

	// ── Sorting ──────────────────────────────────────────────────────────────

	private void sortByHeaderAt(MouseEvent event) {

		JTableHeader header = table.getTableHeader();

		// A click that lands on a column edge is a resize, not a sort.
		if (header.getResizingColumn() != null || header.getCursor().getType() != Cursor.DEFAULT_CURSOR) {
			return;
		}

		int viewColumn = header.columnAtPoint(event.getPoint());
		if (viewColumn < 0 || data == null) {
			return;
		}

		sortBy(CsvTableModel.dataColumn(table.convertColumnIndexToModel(viewColumn)));
	}

	/**
	 * Cycles a column through ascending, descending and back to file order - the
	 * third click being the only way back to how the file was written.
	 */
	private void sortBy(int dataColumn) {

		if (dataColumn == sortColumn) {
			if (!sortDescending) {
				sortDescending = true;
			} else {
				sortColumn = NO_SORT;
				sortDescending = false;
			}
		} else {
			sortColumn = dataColumn;
			sortDescending = false;
		}

		headerRenderer.setSort(sortColumn, sortDescending);
		table.getTableHeader().repaint();
		recomputeView(false);
	}

	// ── Selection ────────────────────────────────────────────────────────────

	/**
	 * Folds the visible page's selection into the file-wide one.
	 *
	 * <p>Selection is kept as file row indices rather than table rows because a
	 * page is a window: ticking three rows here, paging on and ticking two more
	 * has to export five, and re-sorting must not lose them.
	 */
	private void captureSelection() {

		for (int row = 0; row < model.getRowCount(); row++) {
			int sourceRow = model.sourceRow(row);
			if (sourceRow < 0) {
				continue;
			}
			if (table.isRowSelected(row)) {
				selectedRows.add(sourceRow);
			} else {
				selectedRows.remove(sourceRow);
			}
		}

		updateStatus();
	}

	private void restoreSelection() {

		syncingSelection = true;
		try {
			table.clearSelection();
			for (int row = 0; row < model.getRowCount(); row++) {
				int sourceRow = model.sourceRow(row);
				if (sourceRow >= 0 && selectedRows.contains(sourceRow)) {
					table.addRowSelectionInterval(row, row);
				}
			}
		} finally {
			syncingSelection = false;
		}
	}

	private void selectAllRows() {

		for (int row : view) {
			selectedRows.add(row);
		}

		restoreSelection();
		updateStatus();
	}

	private void clearSelection() {

		selectedRows.clear();
		restoreSelection();
		updateStatus();
	}

	/** The selected rows in view order, or - when nothing is ticked - every row in view. */
	private int[] selectionOrView() {
		return selectedRows.isEmpty() ? view : selectedRowsInViewOrder();
	}

	/**
	 * The ticked rows, ordered the way they are on screen, so an export reads in
	 * the order the user sorted them into rather than the file's.
	 */
	private int[] selectedRowsInViewOrder() {

		int[] rows = new int[selectedRows.size()];
		int found = 0;

		for (int row : view) {
			if (selectedRows.contains(row)) {
				rows[found++] = row;
			}
		}

		return found == rows.length ? rows : Arrays.copyOf(rows, found);
	}

	// ── Search ───────────────────────────────────────────────────────────────

	private void setSearchQuery(String query, boolean regex, boolean caseSensitive) {

		try {
			searchMatcher = TextMatcher.compile(query, regex, caseSensitive);
			findBar.setInvalid(null);
		} catch (PatternSyntaxException e) {
			searchMatcher = null;
			findBar.setInvalid(e.getDescription());
		}

		recomputeHits();
	}

	private void recomputeHits() {

		if (data == null || searchMatcher == null || !findBar.isVisible()) {
			hits = CsvSearch.Hits.NONE;
			currentHit = -1;
			cellRenderer.setHits(hits, -1, -1);
			findBar.setStatus(searchMatcher == null ? " " : "0 / 0");
			table.repaint();
			return;
		}

		long generation = searchGeneration.incrementAndGet();
		searchCancelled.set(true);
		AtomicBoolean cancelled = new AtomicBoolean();
		searchCancelled = cancelled;

		CsvData current = data;
		int[] currentView = view;
		TextMatcher matcher = searchMatcher;

		if (currentView.length <= SYNC_ROWS) {
			applyHits(CsvSearch.find(current, currentView, matcher, cancelled));
			return;
		}

		findBar.setStatus("Searching…");

		Thread.ofVirtual().name("csv-quickview-search").start(() -> {

			CsvSearch.Hits found = CsvSearch.find(current, currentView, matcher, cancelled);
			if (found == null || cancelled.get()) {
				return;
			}

			SwingUtilities.invokeLater(() -> {
				if (generation == searchGeneration.get() && data == current && view == currentView) {
					applyHits(found);
				}
			});
		});
	}

	private void applyHits(CsvSearch.Hits found) {

		hits = found != null ? found : CsvSearch.Hits.NONE;
		currentHit = hits.isEmpty() ? -1 : hits.next(firstVisibleViewRow() - 1, Integer.MAX_VALUE);

		showCurrentHit(currentHit >= 0);
	}

	private void moveToHit(boolean forward) {

		if (hits.isEmpty()) {
			if (!findBar.isVisible()) {
				findBar.open();
			}
			return;
		}

		int viewRow = currentHit >= 0 ? hits.viewRow(currentHit) : firstVisibleViewRow();
		int column = currentHit >= 0 ? hits.column(currentHit) : -1;

		currentHit = forward ? hits.next(viewRow, column) : hits.previous(viewRow, column);
		showCurrentHit(true);
	}

	/**
	 * Puts the current match on screen, turning to its page if needed.
	 *
	 * <p>It deliberately does not select the row: selection is what gets
	 * exported, and pressing Enter in the find bar must not quietly change what
	 * the user is about to export.
	 */
	private void showCurrentHit(boolean scroll) {

		int viewRow = currentHit >= 0 && currentHit < hits.size() ? hits.viewRow(currentHit) : -1;
		int column = viewRow >= 0 ? hits.column(currentHit) : -1;

		cellRenderer.setHits(hits, viewRow, column);
		findBar.setStatus(hits.isEmpty()
				? "0 / 0"
				: (currentHit + 1) + " / " + ViewerUi.count(hits.size()) + (hits.capped() ? "+" : ""));

		if (scroll && viewRow >= 0) {
			int pageSize = pageSize();
			int target = Paging.pageOf(viewRow, pageSize);
			if (target != page) {
				page = target;
				refreshPage();
			}
			int tableRow = viewRow - Paging.firstRow(page, pageSize);
			if (tableRow >= 0 && tableRow < model.getRowCount()) {
				Rectangle cell = table.getCellRect(tableRow, CsvTableModel.tableColumn(Math.max(0, column)), true);
				table.scrollRectToVisible(cell);
			}
		}

		table.repaint();
	}

	private int firstVisibleViewRow() {
		return Paging.firstRow(page, pageSize());
	}

	private void clearSearch() {

		searchGeneration.incrementAndGet();
		searchCancelled.set(true);
		searchMatcher = null;
		hits = CsvSearch.Hits.NONE;
		currentHit = -1;
		cellRenderer.setHits(hits, -1, -1);
		findBar.setStatus(" ");
		table.repaint();
	}

	private void onEscape() {

		if (findBar.isVisible()) {
			findBar.close();
			return;
		}

		if (!selectedRows.isEmpty()) {
			clearSelection();
			return;
		}

		if (!filter.isEmpty()) {
			resetFilters();
		}
	}

	private void resetFilters() {

		filter = FilterSpec.NONE.withRegex(filter.regex()).withCaseSensitive(filter.caseSensitive());
		filterBar.reset();
		columnFilterRow.clear();
		recomputeView(false);
	}

	// ── Export ───────────────────────────────────────────────────────────────

	private JPopupMenu exportMenu() {

		JPopupMenu menu = new JPopupMenu();

		int selected = selectedRows.size();
		boolean filtered = data != null && view.length != data.rowCount();

		JMenuItem copySelected = new JMenuItem("Copy " + ViewerUi.count(selected) + " selected rows");
		copySelected.setEnabled(selected > 0);
		copySelected.addActionListener(e -> copyToClipboard(selectedRowsInViewOrder()));
		menu.add(copySelected);

		JMenuItem copyShown = new JMenuItem(
				(filtered ? "Copy " + ViewerUi.count(view.length) + " filtered rows" : "Copy all rows"));
		copyShown.setEnabled(view.length > 0);
		copyShown.addActionListener(e -> copyToClipboard(view));
		menu.add(copyShown);

		menu.addSeparator();

		JMenuItem exportSelected = new JMenuItem("Export " + ViewerUi.count(selected) + " selected rows to CSV…");
		exportSelected.setEnabled(selected > 0);
		exportSelected.addActionListener(e -> exportToFile(selectedRowsInViewOrder()));
		menu.add(exportSelected);

		JMenuItem exportShown = new JMenuItem(
				(filtered ? "Export " + ViewerUi.count(view.length) + " filtered rows to CSV…" : "Export all rows to CSV…"));
		exportShown.setEnabled(view.length > 0);
		exportShown.addActionListener(e -> exportToFile(view));
		menu.add(exportShown);

		menu.addSeparator();

		JCheckBoxMenuItem includeHeader = new JCheckBoxMenuItem("Include header row", exportIncludesHeader);
		includeHeader.addActionListener(e -> {
			exportIncludesHeader = includeHeader.isSelected();
			store(SETTING_EXPORT_HEADER, exportIncludesHeader);
		});
		menu.add(includeHeader);

		return menu;
	}

	private void showContextMenu(MouseEvent event) {

		if (!event.isPopupTrigger() || data == null) {
			return;
		}

		int row = table.rowAtPoint(event.getPoint());
		if (row >= 0 && !table.isRowSelected(row)) {
			// Right-clicking an unselected row acts on it, the way a file manager does.
			table.setRowSelectionInterval(row, row);
		}

		JPopupMenu menu = exportMenu();
		menu.addSeparator();

		JMenuItem selectAll = new JMenuItem("Select all rows in view");
		selectAll.addActionListener(e -> selectAllRows());
		menu.add(selectAll);

		JMenuItem clear = new JMenuItem("Clear selection");
		clear.setEnabled(!selectedRows.isEmpty());
		clear.addActionListener(e -> clearSelection());
		menu.add(clear);

		menu.addSeparator();
		menu.add(dialectMenu());

		JCheckBoxMenuItem headerRow = new JCheckBoxMenuItem("First row is a header", data.hasHeaderRow());
		headerRow.addActionListener(e -> reload(data.dialect(), headerRow.isSelected()));
		menu.add(headerRow);

		menu.show(table, event.getX(), event.getY());
	}

	/** Lets the user overrule the sniffed separator, which is the one thing a mis-read file needs. */
	private JMenu dialectMenu() {

		JMenu menu = new JMenu("Separator");

		for (char candidate : CsvDialect.CANDIDATES) {
			CsvDialect option = CsvDialect.of(candidate);
			JRadioButtonMenuItem item = new JRadioButtonMenuItem(option.delimiterName(),
					data.dialect().delimiter() == candidate);
			item.addActionListener(e -> reload(option, data.hasHeaderRow()));
			menu.add(item);
		}

		return menu;
	}

	private void reload(CsvDialect dialect, boolean headerRow) {

		NuclrResource item = resource;
		if (item == null) {
			return;
		}

		AtomicBoolean cancelled = new AtomicBoolean();

		Thread.ofVirtual().name("csv-quickview-reload").start(() -> {
			try {
				CsvData reloaded = CsvLoader.load(item, dialect, headerRow, cancelled);
				if (reloaded == null) {
					return;
				}
				SwingUtilities.invokeLater(() -> install(item, fileName, reloaded));
			} catch (Exception e) {
				log.warn("Failed to re-read [{}]: {}", fileName, e.getMessage());
				showMessage("Couldn’t read this file", fileName, e.getMessage(), cancelled);
			}
		});
	}

	private void copyToClipboard(int[] rows) {

		if (data == null || rows == null || rows.length == 0) {
			return;
		}

		if (rows.length > CsvExporter.CLIPBOARD_ROW_LIMIT) {
			JOptionPane.showMessageDialog(this,
					"That is " + ViewerUi.count(rows.length) + " rows — more than the clipboard should carry.\n"
							+ "Export them to a CSV file instead.",
					"Too many rows to copy", JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		String text = CsvExporter.toText(data, rows, exportIncludesHeader, data.dialect().delimiter());
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);

		paginationBar.setStatus("Copied " + ViewerUi.count(rows.length) + " rows to the clipboard");
	}

	private void exportToFile(int[] rows) {

		if (data == null || rows == null || rows.length == 0) {
			return;
		}

		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Export " + ViewerUi.count(rows.length) + " rows");
		chooser.setSelectedFile(new File(defaultExportDirectory(),
				CsvFileSupport.baseName(fileName) + "-export.csv"));

		if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
			return;
		}

		Path file = chooser.getSelectedFile().toPath();

		if (Files.exists(file)) {
			int answer = JOptionPane.showConfirmDialog(this,
					file.getFileName() + " already exists. Replace it?",
					"Replace file", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
			if (answer != JOptionPane.YES_OPTION) {
				return;
			}
		}

		CsvData current = data;
		boolean includeHeader = exportIncludesHeader;
		char delimiter = current.dialect().delimiter();

		Thread.ofVirtual().name("csv-quickview-export").start(() -> {
			try {
				CsvExporter.writeFile(file, current, rows, includeHeader, delimiter);
				SwingUtilities.invokeLater(() -> paginationBar.setStatus(
						"Exported " + ViewerUi.count(rows.length) + " rows to " + file.getFileName()));
			} catch (Exception e) {
				log.warn("Failed to export to [{}]: {}", file, e.getMessage());
				SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
						"Could not write " + file + "\n" + e.getMessage(),
						"Export failed", JOptionPane.ERROR_MESSAGE));
			}
		});
	}

	private File defaultExportDirectory() {

		Path path = resource != null ? resource.getPath() : null;
		Path parent = path != null ? path.getParent() : null;

		return parent != null ? parent.toFile() : new File(System.getProperty("user.home", "."));
	}

	// ── Odds and ends ────────────────────────────────────────────────────────

	private void setColumnFiltersVisible(boolean visible) {

		columnFilterRow.setVisible(visible);
		columnHeader.revalidate();
		columnHeader.repaint();
		scroll.revalidate();

		if (!visible && !filter.columnQueries().isEmpty()) {
			// Hiding the fields must not leave an invisible filter applied.
			columnFilterRow.clear();
			filter = filter.withoutColumnQueries();
			recomputeView(true);
		}
	}

	private void showMessage(String title, String detail, String hint, AtomicBoolean cancelled) {

		SwingUtilities.invokeLater(() -> {
			if (cancelled == null || !cancelled.get()) {
				showMessageNow(title, detail, hint);
			}
		});
	}

	private void showMessageNow(String title, String detail, String hint) {

		messageCard.show(title, detail, hint);
		messageCard.applyTheme(palette);
		cards.show(deck, CARD_MESSAGE);
	}

	private void cancelBackgroundWork() {

		viewGeneration.incrementAndGet();
		searchGeneration.incrementAndGet();
		viewCancelled.set(true);
		searchCancelled.set(true);
	}

	private void store(String key, Object value) {

		if (settings != null) {
			settings.set(SETTINGS_NAMESPACE, key, value);
		}
	}
}
