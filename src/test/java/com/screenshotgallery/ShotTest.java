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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class ShotTest
{
	/** a picture at screenshots/<rel> (the file itself isn't needed: names and times come from the path) */
	private static Shot shot(String rel)
	{
		return Shot.of(null, Arrays.asList(rel.split("/")), 0);
	}

	@Test
	public void readsRuneLitesNames()
	{
		Shot s = shot("Zezima/Levels/Fishing(99) 2026-11-07_12-30-45.png");
		assertEquals("Zezima", s.character);
		assertEquals("Levels", s.type);
		assertEquals("Fishing level 99", s.title());
		assertEquals(LocalDateTime.of(2026, 11, 7, 12, 30, 45), s.local());

		assertEquals("Vorkath kill 1,234", shot("Zezima/Boss Kills/Vorkath(1,234) 2026-01-02_03-04-05.png").title());
		assertEquals("Hard clue 12", shot("Zezima/Clue Scroll Rewards/Hard(12) 2026-01-02_03-04-05.png").title());
		assertEquals("Dragon pickaxe", shot("Zezima/Collection Log/Collection log (Dragon pickaxe) 2026-01-02_03-04-05.png").title());
		assertEquals("Death", shot("Zezima/Deaths/Death 2026-01-02_03-04-05 (2).png").title());
		assertEquals("Pet", shot("Zezima/Pets/Pet 2026-01-02_03-04-05.png").title());

		// the user's real files, 2026-11-07: same-second camera shots get "(1)" with NO space
		Shot dup = shot("bycoo222/2026-11-07_17-08-32(1).png");
		assertEquals("Screenshot", dup.title());
		assertEquals(LocalDateTime.of(2026, 11, 7, 17, 8, 32), dup.local());
		assertEquals("Chambers of Xeric Challenge Mode kill 4",
			shot("bycoo222/Boss Kills/Chambers of Xeric Challenge Mode(4) 2026-11-07_22-33-42.png").title());
	}

	@Test
	public void folders()
	{
		Shot camera = shot("Zezima-Leagues/2026-01-02_03-04-05.png");   // RuneLite's camera button: no type folder
		assertEquals("Zezima-Leagues", camera.character);
		assertEquals(Shot.OTHER, camera.type);
		assertEquals("Screenshot", camera.title());
		Shot login = shot("2026-01-02_03-04-05.png");
		assertEquals("", login.character);
		assertNull(shot("Zezima/notes.txt"));
	}

	@Test
	public void importedTypeFolder()
	{
		// a picked "Levels" folder can't see which character it belongs to: filed under "Imported"
		Shot s = Shot.of(null, Arrays.asList(Library.IMPORTED, "Levels", "Fishing(99) 2026-11-07_12-30-45.jpg"), 0);
		assertEquals("Fishing level 99", s.title());
		assertEquals("Levels", s.type);
	}

	@Test
	public void newScreenshotsFiledAsRuneLiteFilesThem()
	{
		long at = LocalDateTime.of(2026, 11, 7, 12, 30, 45).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
		assertEquals(Arrays.asList("Zezima", "Levels", "Fishing(99) 2026-11-07_12-30-45.png"),
			Library.place("Zezima", new ShotNamer.Name("Levels", "Fishing(99)", at), at));
		assertEquals(Arrays.asList("Zezima", "2026-11-07_12-30-45.png"),
			Library.place("Zezima", new ShotNamer.Name(null, "", at), at));
		assertEquals(Arrays.asList("Fishing(99) 2026-11-07_12-30-45.png"), Library.place(null, new ShotNamer.Name("Levels", "Fishing(99)", at), at));
		// the gallery reads its own copy back the way it always has
		Shot s = shot("Zezima/Levels/Fishing(99) 2026-11-07_12-30-45.png");
		assertEquals("Levels", s.type);
		assertEquals("Fishing(99)", s.name);
	}

	@Test
	public void keysAndAlbums()
	{
		Shot a = shot("Zezima/Pets/Pet 2026-01-02_03-04-05.png");
		assertEquals("Zezima/Pets/Pet 2026-01-02_03-04-05.png", a.key);
		Albums al = new Albums();
		org.junit.Assert.assertTrue(al.create("Pets"));
		org.junit.Assert.assertFalse(al.create("Pets"));            // taken
		org.junit.Assert.assertFalse(al.create("   "));             // empty
		org.junit.Assert.assertTrue(al.create("Best  days"));
		assertEquals(Arrays.asList("Pets", "Best days"), al.names()); // spaces tidied
		org.junit.Assert.assertTrue(al.add("Pets", a.key));
		org.junit.Assert.assertFalse(al.add("Pets", a.key));        // already there
		org.junit.Assert.assertTrue(al.add("Best days", a.key));    // one picture, two albums
		org.junit.Assert.assertTrue(al.rename("Pets", "My pets"));
		assertEquals(Arrays.asList("My pets", "Best days"), al.names()); // keeps its place
		assertEquals(1, al.size("My pets"));
		al.setTitle(a.key, "First pet!");
		assertEquals("First pet!", al.title(a.key));
		org.junit.Assert.assertTrue(al.delete("Best days"));
		assertEquals(1, al.size("My pets"));                         // the picture stays
		al.forget(a.key);                                            // deleted from the gallery
		assertEquals(0, al.size("My pets"));
		assertNull(al.title(a.key));
	}

	@Test
	public void onThisDay()
	{
		List<Shot> all = Arrays.asList(shot("A/Pets/Pet 2025-11-07_10-00-00.png"), shot("A/Pets/Pet 2026-11-07_10-00-00.png"),
			shot("A/Pets/Pet 2024-11-08_10-00-00.png"), shot("A/Levels/Attack(50) 2023-11-07_09-00-00.png"));
		List<Shot> past = GalleryPanel.onThisDay(all, LocalDate.of(2026, 11, 7));
		assertEquals(2, past.size());   // not this year's, not the 8th
	}

	@Test
	public void autoAlbumsOrderAndNames()
	{
		// the user's real folders on 2026-11-07: Boss Kills, Deaths and untyped camera-key shots
		List<String> types = new java.util.ArrayList<>(java.util.Arrays.asList(Shot.OTHER, "Deaths", "Boss Kills", "Duels",
			"Clue Scroll Rewards", "BA High Gambles"));
		types.sort(GalleryPanel::autoOrder);
		assertEquals(java.util.Arrays.asList("Boss Kills", "Clue Scroll Rewards", "Deaths", "BA High Gambles", "Duels", Shot.OTHER),
			types);
		assertEquals("Boss kills", GalleryPanel.autoName("Boss Kills"));
		assertEquals("Clues", GalleryPanel.autoName("Clue Scroll Rewards"));
		assertEquals("Deaths", GalleryPanel.autoName("Deaths"));
	}
}
