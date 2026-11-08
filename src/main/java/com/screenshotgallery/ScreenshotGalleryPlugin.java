/*
 * Copyright (c) 2026, bycoo222
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.screenshotgallery;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ScreenshotTaken;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.Filepath;
import net.runelite.client.util.ImageUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
	name = "Screenshot Gallery",
	internalName = "screenshot-gallery",   // getPluginDirectory() needs it (RuneLite: "internalName must be set")
	description = "Browse the screenshots RuneLite takes for you - levels, pets, drops, deaths, clues, boss kills - by type and date",
	tags = {"screenshot", "screenshots", "gallery", "album", "photos", "pictures", "memories"}
)
public class ScreenshotGalleryPlugin extends Plugin
{
	private static final Logger log = LoggerFactory.getLogger(ScreenshotGalleryPlugin.class);
	/** the date (yyyy-MM-dd) the "on this day" chat line was last shown: once a day */
	private static final String SHOWN_ON = "onThisDayShown";

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ScreenshotGalleryConfig config;

	@Inject
	private Gson gson;

	/** albums and renamed pictures (gallery.json in the plugin's folder) */
	private final Albums albums = new Albums();
	private Filepath albumsFile;

	private ExecutorService worker;
	private Thumbs thumbs;
	private Library library;
	private GalleryPanel panel;
	private NavigationButton navButton;
	private ViewerWindow viewer;
	private BufferedImage icon;
	private final AtomicBoolean scanQueued = new AtomicBoolean();
	private volatile boolean importing;
	private volatile boolean stopping;

	@Override
	protected void startUp() throws IOException
	{
		// the gallery's copies live in the plugin's own folder (the only place a Plugin Hub plugin may write)
		library = new Library(getPluginDirectory().joinSegment("shots"));
		albumsFile = getPluginDirectory().joinSegment("gallery.json");
		stopping = false;
		// the plugin's own thread: reading and writing pictures never waits on, or holds up, the game or the sidebar
		worker = Executors.newSingleThreadExecutor(r ->
		{
			Thread t = new Thread(r, "screenshot-gallery");
			t.setDaemon(true);
			return t;
		});
		thumbs = new Thumbs(worker);
		panel = new GalleryPanel(this, thumbs, config);
		icon = ImageUtil.loadImageResource(ScreenshotGalleryPlugin.class, "gallery.png");
		navButton = NavigationButton.builder().tooltip("Screenshot Gallery").icon(icon).priority(10).panel(panel).build();
		clientToolbar.addNavigation(navButton);
		Filepath f = albumsFile;
		worker.execute(() -> albums.load(f, gson));   // before the first read of the gallery (same thread, in order)
		rescan();
	}

	@Override
	protected void shutDown()
	{
		// safe after a startUp that stopped part way (RuneLite stops a plugin whose start failed)
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
		}
		if (viewer != null)
		{
			ViewerWindow v = viewer;
			SwingUtilities.invokeLater(v::dispose);
			viewer = null;
		}
		stopping = true;        // an import stops at its next picture
		if (worker != null)
		{
			worker.shutdown();
		}
		if (thumbs != null)
		{
			thumbs.clear();
		}
		panel = null;
	}

	@Provides
	ScreenshotGalleryConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ScreenshotGalleryConfig.class);
	}

	/** Reads the gallery again (once, however often it's asked while a read is waiting). */
	void rescan()
	{
		if (worker == null || worker.isShutdown() || !scanQueued.compareAndSet(false, true))
		{
			return;
		}
		worker.execute(() ->
		{
			scanQueued.set(false);
			List<Shot> shots = library.scan();
			GalleryPanel p = panel;
			if (p != null)
			{
				SwingUtilities.invokeLater(() -> p.setShots(shots));
			}
		});
	}

	/** RuneLite saved a screenshot: the gallery keeps its own copy of it. */
	@Subscribe
	public void onScreenshotTaken(ScreenshotTaken e)
	{
		if (worker == null || worker.isShutdown() || e.getPath() == null)
		{
			return;
		}
		// where RuneLite put it, as text only: the gallery files its copy under the same character and type
		String savedAt = e.getPath().toString();
		BufferedImage img = e.getScreenshot();
		worker.execute(() ->
		{
			try
			{
				library.keep(savedAt, img);
			}
			catch (IOException | RuntimeException ex)
			{
				log.warn("Screenshot Gallery: could not keep a screenshot", ex);
			}
		});
		rescan();
	}

	/** Import: the player picks RuneLite's screenshots folder (or one character's, or one type's); copies come in. */
	void importOld()
	{
		if (importing || worker == null || worker.isShutdown() || panel == null)
		{
			return;
		}
		// hidden folders shown (.runelite starts with a dot) and, outside Windows, .runelite already picked: the chooser opens
		// in the home folder, so Open is the only click (the same as Death Log's import, user-checked 2026-11-08)
		Filepath.Chooser chooser = new Filepath.Chooser().setIsOpen().setAcceptsDirectories().setFileHidingEnabled(false)
			.setDialogTitle("Screenshot Gallery: pick your .runelite folder, then click Open");
		if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows"))
		{
			chooser.setFileName(".runelite");
		}
		List<Filepath> picked = chooser.showDialog(panel);
		if (picked == null || picked.isEmpty())
		{
			return;
		}
		Filepath dir = picked.get(0);
		importing = true;
		GalleryPanel p = panel;
		p.status("Importing...");
		worker.execute(() ->
		{
			String done;
			try
			{
				int n = library.importFrom(dir, added ->
				{
					if (added % 10 == 0)
					{
						SwingUtilities.invokeLater(() -> p.status("Importing... " + added + " so far"));
					}
				}, () -> stopping);
				done = n == 0 ? "Nothing new to import from there" : n == 1 ? "Imported 1 screenshot" : "Imported " + n + " screenshots";
			}
			catch (IOException | RuntimeException ex)
			{
				log.warn("Screenshot Gallery: import failed", ex);
				done = "Couldn't read that folder: pick your .runelite folder";
			}
			finally
			{
				importing = false;
			}
			String text = done;
			SwingUtilities.invokeLater(() -> p.status(text));
			rescan();
		});
	}

	/** Removes the gallery's copy (RuneLite's own picture stays). */
	void delete(Shot s)
	{
		if (worker == null || worker.isShutdown())
		{
			return;
		}
		worker.execute(() ->
		{
			try
			{
				library.delete(s);
				albums.forget(s.key);
				albums.save(albumsFile, gson);
			}
			catch (IOException | RuntimeException ex)
			{
				log.warn("Screenshot Gallery: could not delete {}", s.file, ex);
			}
		});
		rescan();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		if (ScreenshotGalleryConfig.GROUP.equals(e.getGroup()) && "size".equals(e.getKey()) && panel != null)
		{
			GalleryPanel p = panel;
			SwingUtilities.invokeLater(p::relayout);
		}
	}

	/** On logging in, once a day: a chat line about a screenshot from this day in an earlier year. */
	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		if (e.getGameState() != GameState.LOGGED_IN || !config.onThisDay() || worker == null || worker.isShutdown())
		{
			return;
		}
		String today = LocalDate.now().toString();
		if (today.equals(configManager.getConfiguration(ScreenshotGalleryConfig.GROUP, SHOWN_ON)))
		{
			return;
		}
		worker.execute(() ->
		{
			List<Shot> past = GalleryPanel.onThisDay(library.scan(), LocalDate.now());
			if (past.isEmpty())
			{
				return;
			}
			configManager.setConfiguration(ScreenshotGalleryConfig.GROUP, SHOWN_ON, today);
			Shot s = past.get(0);
			int years = LocalDate.now().getYear() - s.local().getYear();
			ChatMessageBuilder b = new ChatMessageBuilder()
				.append(ChatColorType.HIGHLIGHT).append("On this day " + (years == 1 ? "1 year ago" : years + " years ago") + ": ")
				.append(ChatColorType.NORMAL).append(s.title())
				.append(past.size() > 1 ? " and " + (past.size() - 1) + " more. " : ". ")
				.append("Open Screenshot Gallery to see " + (past.size() > 1 ? "them." : "it."));
			chatMessageManager.queue(QueuedMessage.builder().type(ChatMessageType.CONSOLE).runeLiteFormattedMessage(b.build()).build());
		});
	}

	/** The big viewer, opened on {@code list[index]} (Swing's thread). */
	void view(List<Shot> list, int index)
	{
		if (list.isEmpty())
		{
			return;
		}
		if (viewer == null)
		{
			viewer = new ViewerWindow(this, thumbs, SwingUtilities.getWindowAncestor(panel), icon);   // the client's window
		}
		viewer.show(new ArrayList<>(list), index);
	}

	// ---- albums, titles and the picture size: the panel's actions (Swing's thread) ----

	Albums albums()
	{
		return albums;
	}

	/** RuneLite's title, or the one the player gave it */
	String titleOf(Shot s)
	{
		String t = albums.title(s.key);
		return t != null ? t : s.title();
	}

	boolean createAlbum(String name)
	{
		return changed(albums.create(name));
	}

	boolean renameAlbum(String from, String to)
	{
		return changed(albums.rename(from, to));
	}

	boolean deleteAlbum(String name)
	{
		return changed(albums.delete(name));
	}

	boolean addToAlbum(String album, String key)
	{
		return changed(albums.add(album, key));
	}

	boolean removeFromAlbum(String album, String key)
	{
		return changed(albums.remove(album, key));
	}

	void setTitle(String key, String title)
	{
		albums.setTitle(key, title);
		changed(true);
	}

	void setSize(ScreenshotGalleryConfig.Size size)
	{
		configManager.setConfiguration(ScreenshotGalleryConfig.GROUP, "size", size);
	}

	/** after an album change: saved on the plugin's thread, the panel drawn again */
	private boolean changed(boolean did)
	{
		if (!did)
		{
			return false;
		}
		Filepath f = albumsFile;
		if (f != null && worker != null && !worker.isShutdown())
		{
			worker.execute(() -> albums.save(f, gson));
		}
		GalleryPanel p = panel;
		if (p != null)
		{
			SwingUtilities.invokeLater(p::refresh);
		}
		return true;
	}

	/** For the offline preview: the gallery reading a given folder. */
	void previewLibrary(Library l)
	{
		library = l;
	}
}
