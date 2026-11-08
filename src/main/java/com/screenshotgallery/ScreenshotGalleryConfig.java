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

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(ScreenshotGalleryConfig.GROUP)
public interface ScreenshotGalleryConfig extends Config
{
	String GROUP = "screenshotgallery";

	enum Size
	{
		LARGE("Big - one per row", 1),
		MEDIUM("Medium - two per row", 2),
		SMALL("Small - three per row", 3);

		private final String label;
		final int perRow;

		Size(String label, int perRow)
		{
			this.label = label;
			this.perRow = perRow;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	@ConfigItem(
		position = 0,
		keyName = "size",
		name = "Picture size",
		description = "How big the pictures are in the sidebar: one per row, two, or three to see more at once. The Big / Medium / Small buttons in the panel set the same"
	)
	default Size size()
	{
		return Size.LARGE;
	}

	@ConfigItem(
		position = 1,
		keyName = "onThisDay",
		name = "On this day",
		description = "When you log in, a chat line about a screenshot you took on this day in an earlier year (once a day)"
	)
	default boolean onThisDay()
	{
		return true;
	}
}
