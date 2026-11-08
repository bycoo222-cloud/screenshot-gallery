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

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;

/**
 * A FlowLayout that wraps onto new lines AND reports the height of all its lines (Swing's own FlowLayout reports one
 * line, so chips past the panel's width were cut off). Used for the album chips.
 */
final class WrapLayout extends FlowLayout
{
	WrapLayout(int align, int hgap, int vgap)
	{
		super(align, hgap, vgap);
	}

	@Override
	public Dimension preferredLayoutSize(Container target)
	{
		return size(target, true);
	}

	@Override
	public Dimension minimumLayoutSize(Container target)
	{
		Dimension d = size(target, false);
		d.width -= getHgap() + 1;
		return d;
	}

	private Dimension size(Container target, boolean preferred)
	{
		synchronized (target.getTreeLock())
		{
			int width = target.getWidth() > 0 ? target.getWidth() : GalleryPanel.WIDTH;
			Insets in = target.getInsets();
			int max = width - (in.left + in.right + getHgap() * 2);
			int w = 0, h = 0, rowW = 0, rowH = 0;
			for (Component c : target.getComponents())
			{
				if (!c.isVisible())
				{
					continue;
				}
				Dimension d = preferred ? c.getPreferredSize() : c.getMinimumSize();
				if (rowW + d.width > max && rowW > 0)
				{
					w = Math.max(w, rowW);
					h += rowH + getVgap();
					rowW = 0;
					rowH = 0;
				}
				rowW += d.width + getHgap();
				rowH = Math.max(rowH, d.height);
			}
			w = Math.max(w, rowW);
			h += rowH;
			return new Dimension(w + in.left + in.right + getHgap() * 2, h + in.top + in.bottom + getVgap() * 2);
		}
	}
}
