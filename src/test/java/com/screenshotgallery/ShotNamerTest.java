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

import java.util.HashMap;
import java.util.Map;
import net.runelite.api.gameval.InterfaceID;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** What a new screenshot is of, from the game event that made RuneLite's Screenshot plugin take it (not its file). */
public class ShotNamerTest
{
	/** RuneLite's Screenshot settings for a test: its defaults, changed per test. */
	static final class Fake implements ShotNamer.Settings
	{
		final Map<String, Boolean> on = new HashMap<>();
		int threshold;
		boolean plugin = true;

		Fake with(String key, boolean value)
		{
			on.put(key, value);
			return this;
		}

		@Override
		public boolean pluginOn()
		{
			return plugin;
		}

		@Override
		public boolean on(String key, boolean byDefault)
		{
			return on.getOrDefault(key, byDefault);
		}

		@Override
		public int number(String key, int byDefault)
		{
			return threshold;
		}
	}

	private static void chat(ShotNamer n, String message, long now)
	{
		n.chat(message, now, true, true, false, 1, false);
	}

	private static void is(ShotNamer.Name name, String type, String file)
	{
		assertEquals(type, name.type);
		assertEquals(file, name.file);
	}

	@Test
	public void bossKillNamedFromItsKillCountMessage()
	{
		ShotNamer n = new ShotNamer(new Fake().with("boss", true));
		chat(n, "Your Vorkath kill count is: <col=ff0000>852</col>.", 1_000);
		is(n.take(1_400), ShotNamer.BOSS_KILLS, "Vorkath(852)");
		// RuneLite's boss setting is off by default: no boss picture is expected, so the next one is a manual screenshot
		ShotNamer off = new ShotNamer(new Fake());
		chat(off, "Your Vorkath kill count is: <col=ff0000>852</col>.", 1_000);
		assertTrue(off.take(1_400).manual());
	}

	@Test
	public void raidNamedWhenItsRewardScreenOpens()
	{
		ShotNamer n = new ShotNamer(new Fake());
		chat(n, "Your completed Chambers of Xeric Challenge Mode count is: <col=ff0000>5</col>.", 1_000);
		assertTrue("nothing until the chest opens", n.take(1_100).manual());
		n.widget(InterfaceID.RAIDS_REWARDS, 9_000);
		is(n.take(9_300), ShotNamer.BOSS_KILLS, "Chambers of Xeric Challenge Mode(5)");
		chat(n, "Your completed Theatre of Blood: Hard Mode count is: <col=ff0000>12</col>.", 20_000);
		n.widget(InterfaceID.TOB_CHESTS, 30_000);
		is(n.take(30_200), ShotNamer.BOSS_KILLS, "Theatre of Blood Hard Mode(12)");
	}

	@Test
	public void levelUpFromItsWindowOrItsChatMessage()
	{
		ShotNamer n = new ShotNamer(new Fake());
		n.widget(InterfaceID.LEVELUP_DISPLAY, 1_000);
		assertTrue(n.wantsTexts());
		n.texts("Congratulations, you just advanced a Fishing level.<br><br>Your Fishing level is now 99.", null, null, 1_600);
		assertFalse(n.wantsTexts());
		is(n.take(2_000), ShotNamer.LEVELS, "Fishing(99)");
		n.chat("Congratulations, you've just advanced your Attack level. You are now level 70.", 5_000, true, true, true, 1, false);
		is(n.take(5_300), ShotNamer.LEVELS, "Attack(70)");
		n.chat("Congratulations, you've reached the highest possible Magic level of 99.", 6_000, true, true, true, 1, false);
		is(n.take(6_300), ShotNamer.LEVELS, "Magic(99)");
	}

	@Test
	public void petCollectionLogClueAndQuest()
	{
		ShotNamer n = new ShotNamer(new Fake());
		chat(n, "<col=ef1020>You have a funny feeling like you're being followed.</col>", 1_000);
		is(n.take(1_200), ShotNamer.PETS, "Pet");
		chat(n, "New item added to your collection log: <col=ef1020>Dragon pickaxe</col>", 2_000);
		is(n.take(2_200), ShotNamer.COLLECTION_LOG, "Collection log (Dragon pickaxe)");
		n.notification("Collection log", "New item:<br><br><col=ffffff>Tanzanite fang</col>", false, 3_000);
		is(n.take(3_200), ShotNamer.COLLECTION_LOG, "Collection log (Tanzanite fang)");
		chat(n, "<col=3300ff>You have completed 28 medium Treasure Trails.</col>", 4_000);
		n.widget(InterfaceID.TRAIL_REWARDSCREEN, 4_500);
		is(n.take(4_700), ShotNamer.CLUES, "Medium(28)");
		n.widget(InterfaceID.QUESTSCROLL, 5_000);
		n.texts(null, null, "You have completed The Corsair Curse!", 5_600);
		is(n.take(5_800), ShotNamer.QUESTS, "Quest(The Corsair Curse)");
	}

	@Test
	public void ownDeathIsNotAFriendsDeath()
	{
		ShotNamer n = new ShotNamer(new Fake().with("playerDeath", true).with("friendDeath", true));
		n.death(false, "Zezima", true, false, true, 1_000);
		n.death(true, "me", false, false, false, 1_000);
		is(n.take(1_300), ShotNamer.DEATHS, "Death Zezima");
		ShotNamer.Name mine = n.take(1_400);
		is(mine, ShotNamer.DEATHS, ShotNamer.OWN_DEATH);
		assertEquals(1_000, mine.at);
		n.death(false, "Faraway", true, false, false, 2_000);
		assertTrue("RuneLite skips a friend off screen", n.take(2_200).manual());
	}

	@Test
	public void queueOrderWindowAndSettings()
	{
		Fake f = new Fake().with("boss", true).with("valuableDrop", true);
		f.threshold = 2_000_000;
		ShotNamer n = new ShotNamer(f);
		chat(n, "<col=ef1020>You have a funny feeling like you're being followed.</col>", 1_000);
		chat(n, "Your Vorkath kill count is: <col=ff0000>853</col>.", 1_000);
		chat(n, "<col=ef1020>Valuable drop: Dragon pickaxe (1,000,000 coins)</col>", 1_000);
		is(n.take(1_300), ShotNamer.PETS, "Pet");
		is(n.take(1_350), ShotNamer.BOSS_KILLS, "Vorkath(853)");
		assertTrue("under the drop threshold: no picture expected", n.take(1_400).manual());
		chat(n, "Your Vorkath kill count is: <col=ff0000>854</col>.", 10_000);
		assertTrue("too long after: a manual screenshot", n.take(10_000 + ShotNamer.WINDOW_MS + 1).manual());
		f.plugin = false;
		chat(n, "Your Vorkath kill count is: <col=ff0000>855</col>.", 20_000);
		assertTrue("Screenshot plugin off", n.take(20_100).manual());
		assertNull(n.take(20_200).type);
	}

	@Test
	public void runeLitesFileName()
	{
		long at = java.time.LocalDateTime.of(2026, 11, 7, 22, 33, 42).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
		assertEquals("Vorkath(852) 2026-11-07_22-33-42", ShotNamer.fileName("Vorkath(852)", at));
		assertEquals("2026-11-07_22-33-42", ShotNamer.fileName("", at));
	}
}
