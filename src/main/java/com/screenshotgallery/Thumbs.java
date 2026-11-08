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

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.SwingUtilities;
import net.runelite.client.util.Filepath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Small copies of the screenshots for the sidebar, made one at a time on the plugin's own thread (never the game's or
 * Swing's) and kept for the most recent 160 pictures. A screenshot is decoded at a fraction of its size (ImageIO
 * sub-sampling) — a 1920-wide PNG read at every 4th pixel is a 16th of the work and memory — then smoothed down.
 */
final class Thumbs
{
	private static final Logger log = LoggerFactory.getLogger(Thumbs.class);
	private static final int KEEP = 160;

	private final ExecutorService worker;
	private final Map<String, BufferedImage> cache = new LinkedHashMap<String, BufferedImage>(64, 0.75f, true)
	{
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest)
		{
			return size() > KEEP;
		}
	};
	/** bumped when the sidebar shows a new list: pictures asked for by an older list are skipped */
	private volatile int generation;

	Thumbs(ExecutorService worker)
	{
		this.worker = worker;
	}

	int newGeneration()
	{
		return ++generation;
	}

	/** The picture {@code width} wide, handed to {@code done} on Swing's thread (null if it can't be read). */
	void get(Shot shot, int width, int gen, Consumer<BufferedImage> done)
	{
		Filepath file = shot.file;
		String key = file + "@" + width + "@" + shot.time;
		BufferedImage hit;
		synchronized (cache)
		{
			hit = cache.get(key);
		}
		if (hit != null)
		{
			done.accept(hit);
			return;
		}
		worker.execute(() ->
		{
			if (gen != generation)
			{
				return;   // the list it was for is gone
			}
			BufferedImage img = read(file, width);
			if (img != null)
			{
				synchronized (cache)
				{
					cache.put(key, img);
				}
			}
			SwingUtilities.invokeLater(() -> done.accept(img));
		});
	}

	/** The whole picture, for the viewer, on the plugin's thread; {@code done} on Swing's thread. */
	void full(Filepath file, Consumer<BufferedImage> done)
	{
		worker.execute(() ->
		{
			BufferedImage img = null;
			try (InputStream raw = file.openInputStream())
			{
				img = ImageIO.read(raw);
			}
			catch (Exception e)
			{
				log.debug("Screenshot Gallery: can't read {}", file, e);
			}
			BufferedImage out = img;
			SwingUtilities.invokeLater(() -> done.accept(out));
		});
	}

	void clear()
	{
		synchronized (cache)
		{
			cache.clear();
		}
	}

	static BufferedImage read(Filepath file, int width)
	{
		try (InputStream raw = file.openInputStream(); ImageInputStream in = ImageIO.createImageInputStream(raw))
		{
			if (in == null)
			{
				return null;
			}
			Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
			if (!readers.hasNext())
			{
				return null;
			}
			ImageReader reader = readers.next();
			try
			{
				reader.setInput(in, true, true);
				int w = reader.getWidth(0);
				ImageReadParam param = reader.getDefaultReadParam();
				int step = Math.max(1, w / (width * 2));   // read at about twice the size, then smooth down
				param.setSourceSubsampling(step, step, 0, 0);
				return scale(reader.read(0, param), width);
			}
			finally
			{
				reader.dispose();
			}
		}
		catch (Exception e)
		{
			log.debug("Screenshot Gallery: can't read {}", file, e);
			return null;
		}
	}

	static BufferedImage scale(BufferedImage src, int width)
	{
		if (src == null || src.getWidth() <= 0)
		{
			return null;
		}
		int height = Math.max(1, (int) Math.round(src.getHeight() * (double) width / src.getWidth()));
		BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.drawImage(src, 0, 0, width, height, null);
		g.dispose();
		return out;
	}
}
