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
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.client.util.Filepath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Albums and renamed pictures (user, 2026-11-07: "let me make folders ... let me drag and drop them and right click them.
 * give me a way to add album rename albums ect"). An album is a list of pictures by their key (Shot.key), so a picture
 * can be in several albums, and deleting an album never deletes a picture. Saved as gallery.json in the plugin's folder
 * (Filepath). All methods are synchronized; save() is called off Swing's thread by the plugin.
 */
final class Albums
{
	private static final Logger log = LoggerFactory.getLogger(Albums.class);

	/** what is saved: album name → picture keys (in the order added), picture key → its own title */
	static final class Data
	{
		Map<String, List<String>> albums = new LinkedHashMap<>();
		Map<String, String> titles = new LinkedHashMap<>();
	}

	private Data data = new Data();

	synchronized List<String> names()
	{
		return new ArrayList<>(data.albums.keySet());
	}

	synchronized Set<String> keys(String album)
	{
		List<String> l = data.albums.get(album);
		return l == null ? Collections.emptySet() : new LinkedHashSet<>(l);
	}

	synchronized int size(String album)
	{
		List<String> l = data.albums.get(album);
		return l == null ? 0 : l.size();
	}

	/** false if the name is empty or taken */
	synchronized boolean create(String name)
	{
		name = clean(name);
		if (name.isEmpty() || data.albums.containsKey(name))
		{
			return false;
		}
		data.albums.put(name, new ArrayList<>());
		return true;
	}

	/** keeps the album's place in the order; false if the new name is empty or taken */
	synchronized boolean rename(String from, String to)
	{
		to = clean(to);
		if (to.isEmpty() || !data.albums.containsKey(from) || data.albums.containsKey(to))
		{
			return false;
		}
		Map<String, List<String>> next = new LinkedHashMap<>();
		for (Map.Entry<String, List<String>> e : data.albums.entrySet())
		{
			next.put(e.getKey().equals(from) ? to : e.getKey(), e.getValue());
		}
		data.albums = next;
		return true;
	}

	synchronized boolean delete(String album)
	{
		return data.albums.remove(album) != null;
	}

	/** false if already there (or no such album) */
	synchronized boolean add(String album, String key)
	{
		List<String> l = data.albums.get(album);
		if (l == null || l.contains(key))
		{
			return false;
		}
		l.add(key);
		return true;
	}

	synchronized boolean remove(String album, String key)
	{
		List<String> l = data.albums.get(album);
		return l != null && l.remove(key);
	}

	/** a picture deleted from the gallery leaves every album, and its own title goes */
	synchronized void forget(String key)
	{
		for (List<String> l : data.albums.values())
		{
			l.remove(key);
		}
		data.titles.remove(key);
	}

	/** the picture's own title, or null */
	synchronized String title(String key)
	{
		return data.titles.get(key);
	}

	/** an empty title puts RuneLite's back */
	synchronized void setTitle(String key, String title)
	{
		title = clean(title);
		if (title.isEmpty())
		{
			data.titles.remove(key);
		}
		else
		{
			data.titles.put(key, title);
		}
	}

	private static String clean(String s)
	{
		return s == null ? "" : s.trim().replaceAll("\\s+", " ");
	}

	synchronized void load(Filepath file, Gson gson)
	{
		if (!file.exists())
		{
			return;
		}
		try (Reader r = file.openReader())
		{
			Data d = gson.fromJson(r, Data.class);
			if (d != null)
			{
				data = d;
				if (data.albums == null)
				{
					data.albums = new LinkedHashMap<>();
				}
				if (data.titles == null)
				{
					data.titles = new LinkedHashMap<>();
				}
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Screenshot Gallery: could not read the albums", e);
		}
	}

	/** written to a new file, then moved over the old one, so a crash mid-write never loses the albums */
	void save(Filepath file, Gson gson)
	{
		String json;
		synchronized (this)
		{
			json = gson.toJson(data);
		}
		try
		{
			file.getParent().createDirectories();
			Filepath tmp = file.getParent().joinSegment(file.getFileName() + ".tmp");
			tmp.write(json.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
				StandardOpenOption.WRITE);
			tmp.moveTo(file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Screenshot Gallery: could not save the albums", e);
		}
	}
}
