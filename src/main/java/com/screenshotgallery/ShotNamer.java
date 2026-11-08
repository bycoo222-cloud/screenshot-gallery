/*
 * Copyright (c) 2026, bycoo222
 * Copyright (c) 2018, Lotto <https://github.com/devLotto> (the screenshot triggers, patterns and names, from
 *   RuneLite's Screenshot plugin)
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

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.ScriptID;
import net.runelite.api.WorldView;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneScapeProfileType;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PlayerLootReceived;
import net.runelite.client.util.Text;

/**
 * What a screenshot RuneLite just took is of — without its file: RuneLite's {@code ScreenshotTaken} also says where the
 * picture was saved, but a Plugin Hub plugin may only touch files through Filepath, and a maintainer flagged reading
 * even that location as text (plugin-hub PR #20316, 2026-11-08). So only the picture itself is used, and it is
 * named from the GAME EVENT that made RuneLite's Screenshot plugin take it: its own triggers, patterns,
 * settings and names, followed here (RuneLite's ScreenshotPlugin, Copyright (c) 2018, Lotto
 * &lt;https://github.com/devLotto&gt;, BSD 2-Clause — read from runelite master 2026-11-08). Each trigger queues the
 * folder and name RuneLite gives that picture; each screenshot that arrives takes the oldest one still within
 * {@link #WINDOW_MS}, else it is a manual one (no folder, no name). Triggers RuneLite would skip (its setting off,
 * a drop under its threshold) queue nothing.
 */
final class ShotNamer
{
	/**
	 * How long after its game event a screenshot can be that event's. RuneLite takes the picture on the next frame (with
	 * the date on it, the frame after the next client tick) and posts it once the PNG is written: well under a second
	 * normally. ASSUMED margin, not measured: 5 s for a slow disk.
	 */
	static final long WINDOW_MS = 5_000L;

	// RuneLite's screenshot folders (ScreenshotPlugin's SD_* names)
	static final String LEVELS = "Levels";
	static final String PETS = "Pets";
	static final String BOSS_KILLS = "Boss Kills";
	static final String DEATHS = "Deaths";
	static final String CLUES = "Clue Scroll Rewards";
	static final String COLLECTION_LOG = "Collection Log";
	static final String CHEST_LOOT = "Chest Loot";
	static final String VALUABLE_DROPS = "Valuable Drops";
	static final String UNTRADEABLE_DROPS = "Untradeable Drops";
	static final String COMBAT_ACHIEVEMENTS = "Combat Achievements";
	static final String KINGDOM = "Kingdom Rewards";
	static final String QUESTS = "Quests";
	static final String DUELS = "Duels";
	static final String WILDY_CHEST = "Wilderness Loot Chest";
	static final String KICKS = "Friends Chat Kicks";
	static final String LEAGUE_TASKS = "League Tasks";
	static final String HIGH_GAMBLES = "BA High Gambles";
	static final String PVP_KILLS = "PvP Kills";

	/** Your own death's picture names ("Death" from a death, "Doom Death" from the Doom of Mokhaiotl's death animation). */
	static final String OWN_DEATH = "Death";
	static final String DOOM_DEATH = "Doom Death";

	// RuneLite's own patterns and texts (ScreenshotPlugin)
	private static final String COLLECTION_LOG_TEXT = "New item added to your collection log: ";
	private static final String CHEST_LOOTED_MESSAGE = "You find some treasure in the chest!";
	private static final int GAUNTLET_CHEST_REGION = 12127;
	private static final Pattern NUMBER = Pattern.compile("([,0-9]+)");
	private static final Pattern LEVEL_UP = Pattern.compile(".*Your ([a-zA-Z]+) (?:level is|are)? now (\\d+)\\.");
	private static final Pattern LEVEL_UP_MESSAGE = Pattern.compile("Congratulations, you've (just advanced your (?<skill>[a-zA-Z]+) level\\. You are now level (?<level>\\d+)|reached the highest possible (?<skill99>[a-zA-Z]+) level of 99)\\.");
	private static final Pattern BOSS_KILL = Pattern.compile("Your (.+) (?:kill|success) count is: ?<col=[0-9a-f]{6}>([0-9,]+)</col>");
	private static final Pattern VALUABLE_DROP = Pattern.compile(".*Valuable drop: ([^<>]+?\\(((?:\\d+,?)+) coins\\))(?:</col>)?");
	private static final Pattern UNTRADEABLE_DROP = Pattern.compile(".*Untradeable drop: ([^<>]+)(?:</col>)?");
	private static final Pattern DUEL_END = Pattern.compile("You have now (won|lost) ([0-9,]+) duels?\\.");
	private static final Pattern QUEST_1 = Pattern.compile(".+?ve\\.*? (?<verb>been|rebuilt|.+?ed)? ?(?:the )?'?(?<quest>.+?)'?(?: [Qq]uest)?[!.]?$");
	private static final Pattern QUEST_2 = Pattern.compile("'?(?<quest>.+?)'?(?: [Qq]uest)? (?<verb>[a-z]\\w+?ed)?(?: f.*?)?[!.]?$");
	private static final Pattern COMBAT_TASK = Pattern.compile("Congratulations, you've completed an? (?<tier>\\w+) combat task: @.+?@(?<task>(.+))</col>");
	private static final List<String> RFD_TAGS = Arrays.asList("Another Cook", "freed", "defeated", "saved");
	private static final List<String> WORD_QUEST_IN_NAME_TAGS = Arrays.asList("Another Cook", "Doric", "Heroes", "Legends", "Observatory", "Olaf", "Waterfall");
	private static final List<String> PET_MESSAGES = Arrays.asList("You have a funny feeling like you're being followed",
		"You feel something weird sneaking into your backpack", "You have a funny feeling like you would have been followed");
	private static final Pattern HIGH_GAMBLE = Pattern.compile("(?<reward>.+)!<br>High level gamble count: <col=7f0000>(?<gambleCount>.+)</col>");

	/** One picture's place: RuneLite's folder for it (null = none, a manual screenshot) and its name ("" = manual). */
	static final class Name
	{
		final String type;
		final String file;
		/** when its game event happened, epoch ms */
		final long at;

		Name(String type, String file, long at)
		{
			this.type = type;
			this.file = file;
			this.at = at;
		}

		boolean manual()
		{
			return type == null;
		}
	}

	/** RuneLite's Screenshot plugin's settings (config group "screenshot"), read live so the queue follows them. */
	interface Settings
	{
		/** the Screenshot plugin itself is on */
		boolean pluginOn();

		boolean on(String key, boolean byDefault);

		int number(String key, int byDefault);
	}

	/** The settings as RuneLite keeps them (read only; the gallery never writes another plugin's settings). */
	static Settings from(ConfigManager configManager)
	{
		return new Settings()
		{
			@Override
			public boolean pluginOn()
			{
				String v = configManager.getConfiguration("runelite", "screenshotplugin");
				return v == null || Boolean.parseBoolean(v);
			}

			@Override
			public boolean on(String key, boolean byDefault)
			{
				String v = configManager.getConfiguration("screenshot", key);
				return v == null ? byDefault : Boolean.parseBoolean(v);
			}

			@Override
			public int number(String key, int byDefault)
			{
				String v = configManager.getConfiguration("screenshot", key);
				try
				{
					return v == null ? byDefault : Integer.parseInt(v.trim());
				}
				catch (NumberFormatException e)
				{
					return byDefault;
				}
			}
		};
	}

	private final Settings settings;
	private final Deque<Name> pending = new ArrayDeque<>();

	// held until their reward screen opens, as RuneLite does (it names those pictures then)
	private int heldGroup = -1;
	private String heldType;
	private String heldFile;
	private Integer clueNumber;
	private String clueType;
	private String kickName;
	/** a level-up / object box / quest scroll opened: its text is read on the next tick, as RuneLite does */
	private boolean textsNext;

	ShotNamer(Settings settings)
	{
		this.settings = settings;
	}

	/** A screenshot arrived (any thread): what it is of. Every screenshot must call this once, so the queue stays in step. */
	synchronized Name take(long now)
	{
		while (!pending.isEmpty() && now - pending.peekFirst().at > WINDOW_MS)
		{
			pending.pollFirst();
		}
		Name n = pending.pollFirst();
		return n != null ? n : new Name(null, "", now);
	}

	private synchronized void add(String type, String file, long now)
	{
		if (settings.pluginOn())
		{
			pending.addLast(new Name(type, file, now));
		}
	}

	private boolean on(String key, boolean byDefault)
	{
		return settings.on(key, byDefault);
	}

	/** RuneLite's file name for a picture taken at {@code at}: "Vorkath(852) 2026-11-07_22-33-42", a manual one the time only. */
	static String fileName(String file, long at)
	{
		String time = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date(at));
		return file == null || file.isEmpty() ? time : file + " " + time;
	}

	// ---- RuneLite's triggers (ScreenshotPlugin), each as plain values so they can be tested ----

	/**
	 * A game, spam, trade or friends-chat notification message ({@code message} with its colour tags). The varbits as
	 * RuneLite reads them: {@code collectionChat} = the collection log's new-item chat message is on,
	 * {@code taskChat} = combat tasks are announced in the chat, {@code levelChat} = level-ups come as a chat message.
	 */
	void chat(String message, long now, boolean collectionChat, boolean taskChat, boolean levelChat, int region,
		boolean inGauntlet)
	{
		if (message.contains("You have completed") && message.contains("Treasure"))
		{
			Matcher m = NUMBER.matcher(Text.removeTags(message));
			if (m.find())
			{
				try
				{
					clueType = message.substring(message.lastIndexOf(m.group()) + m.group().length() + 1, message.indexOf("Treasure") - 1);
					clueNumber = Integer.valueOf(m.group().replace(",", ""));
				}
				catch (RuntimeException e)
				{
					// RuneLite's own parse fails here too: no clue picture
				}
				return;
			}
		}
		if (count(message, "Your Barrows chest count is", InterfaceID.BARROWS_REWARD, BOSS_KILLS, "Barrows")
			|| count(message, "Your completed Chambers of Xeric count is:", InterfaceID.RAIDS_REWARDS, BOSS_KILLS, "Chambers of Xeric")
			|| count(message, "Your completed Chambers of Xeric Challenge Mode count is:", InterfaceID.RAIDS_REWARDS, BOSS_KILLS,
				"Chambers of Xeric Challenge Mode")
			|| count(message, "Your completed Theatre of Blood", InterfaceID.TOB_CHESTS, BOSS_KILLS, message.contains("Hard Mode")
				? "Theatre of Blood Hard Mode" : message.contains("Story Mode") ? "Theatre of Blood Story Mode" : "Theatre of Blood")
			|| count(message, "Your completed Tombs of Amascut", InterfaceID.TOA_CHESTS, BOSS_KILLS, message.contains("Expert Mode")
				? "Tombs of Amascut Expert Mode" : message.contains("Entry Mode") ? "Tombs of Amascut Entry Mode" : "Tombs of Amascut")
			|| count(message, "Your completed Fractured Archive count is", InterfaceID.TFA_CHESTS, BOSS_KILLS, "The Fractured Archive")
			|| count(message, "Your Lunar Chest count is", InterfaceID.PMOON_REWARD, BOSS_KILLS, "Moons of Peril"))
		{
			return;
		}
		if (message.contains("Search the chest nearby to retrieve your earned rewards!"))
		{
			hold(InterfaceID.COLOSSEUM_REWARD_CHEST_2, CHEST_LOOT, "Fortis Colosseum Chest");
			return;
		}
		if (message.equals("Your request to kick/ban this user was successful.") && on("ccKick", false) && kickName != null)
		{
			add(KICKS, "Kick " + kickName, now);
			kickName = null;
		}
		if (PET_MESSAGES.stream().anyMatch(message::contains) && on("pets", true))
		{
			add(PETS, "Pet", now);
		}
		if (on("boss", false))
		{
			Matcher m = BOSS_KILL.matcher(message);
			if (m.find())
			{
				add(BOSS_KILLS, Text.removeTags(m.group(1)) + "(" + m.group(2).replace(",", "") + ")", now);
			}
		}
		if (message.equals(CHEST_LOOTED_MESSAGE) && on("rewards", true) && region == GAUNTLET_CHEST_REGION)
		{
			add(CHEST_LOOT, "The Gauntlet", now);
		}
		if (on("valuableDrop", false))
		{
			Matcher m = VALUABLE_DROP.matcher(message);
			if (m.matches())
			{
				try
				{
					if (Integer.parseInt(m.group(2).replace(",", "")) >= settings.number("valuableDropThreshold", 0))
					{
						add(VALUABLE_DROPS, "Valuable drop " + m.group(1), now);
					}
				}
				catch (NumberFormatException e)
				{
					// over an int: RuneLite's own parse fails too
				}
			}
		}
		if (on("untradeableDrop", false) && !inGauntlet)
		{
			Matcher m = UNTRADEABLE_DROP.matcher(message);
			if (m.matches())
			{
				add(UNTRADEABLE_DROPS, "Untradeable drop " + m.group(1), now);
			}
		}
		if (on("duels", false))
		{
			Matcher m = DUEL_END.matcher(message);
			if (m.find())
			{
				add(DUELS, "Duel " + m.group(1) + " (" + m.group(2).replace(",", "") + ")", now);
			}
		}
		if (message.startsWith(COLLECTION_LOG_TEXT) && collectionChat && on("collectionLogEntries", true))
		{
			add(COLLECTION_LOG, "Collection log (" + Text.removeTags(message).substring(COLLECTION_LOG_TEXT.length()) + ")", now);
		}
		if (message.contains("combat task") && on("combatAchievements", true) && taskChat)
		{
			Matcher m = COMBAT_TASK.matcher(message);
			if (m.find())
			{
				add(COMBAT_ACHIEVEMENTS, "Combat task (" + m.group("task").replaceAll("[:?]", "") + ")", now);
			}
		}
		if (levelChat && on("levels", true))
		{
			Matcher m = LEVEL_UP_MESSAGE.matcher(message);
			if (m.matches())
			{
				String skill = m.group("skill") != null ? m.group("skill") : m.group("skill99");
				String level = m.group("level") != null ? m.group("level") : "99";
				add(LEVELS, Character.toUpperCase(skill.charAt(0)) + skill.substring(1) + "(" + level + ")", now);
			}
		}
	}

	/** A "count is" message for a reward screen: the picture is named when that screen opens. */
	private boolean count(String message, String start, int group, String type, String what)
	{
		if (!message.startsWith(start))
		{
			return false;
		}
		Matcher m = NUMBER.matcher(Text.removeTags(message));
		if (!m.find())
		{
			return false;
		}
		hold(group, type, what + "(" + m.group().replace(",", "") + ")");
		return true;
	}

	private void hold(int group, String type, String file)
	{
		heldGroup = group;
		heldType = type;
		heldFile = file;
	}

	/** An interface opened (its group id). */
	void widget(int group, long now)
	{
		switch (group)
		{
			case InterfaceID.QUESTSCROLL:
			case InterfaceID.TRAIL_REWARDSCREEN:
			case InterfaceID.RAIDS_REWARDS:
			case InterfaceID.TOB_CHESTS:
			case InterfaceID.TOA_CHESTS:
			case InterfaceID.TFA_CHESTS:
			case InterfaceID.BARROWS_REWARD:
			case InterfaceID.PMOON_REWARD:
			case InterfaceID.COLOSSEUM_REWARD_CHEST_2:
				if (!on("rewards", true))
				{
					return;
				}
				break;
			case InterfaceID.LEVELUP_DISPLAY:
				if (!on("levels", true))
				{
					return;
				}
				break;
			case InterfaceID.OBJECTBOX:
				if (!(on("levels", true) || on("baHighGamble", false)))
				{
					return;
				}
				break;
			case InterfaceID.MISC_COLLECTION:
				if (!on("kingdom", true))
				{
					return;
				}
				break;
			case InterfaceID.WILDY_LOOT_CHEST:
				if (!on("wildernessLootChest", true))
				{
					return;
				}
				break;
			default:
				return;
		}
		switch (group)
		{
			case InterfaceID.MISC_COLLECTION:
				add(KINGDOM, "Kingdom " + LocalDate.now(), now);
				break;
			case InterfaceID.RAIDS_REWARDS:
			case InterfaceID.TOB_CHESTS:
			case InterfaceID.TOA_CHESTS:
			case InterfaceID.TFA_CHESTS:
			case InterfaceID.BARROWS_REWARD:
			case InterfaceID.PMOON_REWARD:
			case InterfaceID.COLOSSEUM_REWARD_CHEST_2:
				if (heldGroup == group)
				{
					add(heldType, heldFile, now);
					heldGroup = -1;
					heldType = null;
					heldFile = null;
				}
				break;
			case InterfaceID.LEVELUP_DISPLAY:
			case InterfaceID.OBJECTBOX:
			case InterfaceID.QUESTSCROLL:
				textsNext = true;   // the text is set after the interface loads: read on the next tick
				break;
			case InterfaceID.TRAIL_REWARDSCREEN:
				if (clueType != null && clueNumber != null && !clueType.isEmpty())
				{
					add(CLUES, Character.toUpperCase(clueType.charAt(0)) + clueType.substring(1) + "(" + clueNumber + ")", now);
					clueType = null;
					clueNumber = null;
				}
				break;
			case InterfaceID.WILDY_LOOT_CHEST:
				add(WILDY_CHEST, "Loot key", now);
				break;
			default:
				break;
		}
	}

	/** Whether the next tick should read the level-up / object box / quest texts. */
	boolean wantsTexts()
	{
		return textsNext;
	}

	/** The tick after a level-up / object box / quest scroll opened: their texts (null = that interface isn't there). */
	void texts(String levelUp, String objectBox, String questTitle, long now)
	{
		if (!textsNext)
		{
			return;
		}
		textsNext = false;
		String type = null;
		String file = null;
		if (levelUp != null)
		{
			file = levelUp(levelUp);
			type = LEVELS;
		}
		else if (objectBox != null)
		{
			if (Text.removeTags(objectBox).contains("High level gamble"))
			{
				if (on("baHighGamble", false))
				{
					Matcher m = HIGH_GAMBLE.matcher(objectBox);
					file = m.find() ? "High Gamble(" + m.group("gambleCount") + ")" : "High Gamble(count not found)";
					type = HIGH_GAMBLES;
				}
			}
			else if (on("levels", true))
			{
				file = levelUp(objectBox);
				type = LEVELS;
			}
		}
		else if (questTitle != null)
		{
			file = quest(questTitle);
			type = QUESTS;
		}
		if (file != null)
		{
			add(type, file, now);
		}
	}

	private static String levelUp(String text)
	{
		Matcher m = LEVEL_UP.matcher(text);
		return m.matches() ? m.group(1) + "(" + m.group(2) + ")" : null;
	}

	/** RuneLite's quest name: "Quest(The Corsair Curse)". */
	static String quest(String text)
	{
		Matcher m1 = QUEST_1.matcher(text);
		Matcher m2 = QUEST_2.matcher(text);
		Matcher m = m1.matches() ? m1 : m2;
		if (!m.matches())
		{
			return "Quest(quest not found)";
		}
		String quest = m.group("quest");
		String verb = m.group("verb") != null ? m.group("verb") : "";
		if (verb.contains("kind of"))
		{
			quest += " partial completion";
		}
		else if (verb.contains("completely"))
		{
			quest += " II";
		}
		String all = quest + verb;
		if (RFD_TAGS.stream().anyMatch(all::contains))
		{
			quest = "Recipe for Disaster - " + quest;
		}
		String q = quest;
		if (WORD_QUEST_IN_NAME_TAGS.stream().anyMatch(q::contains))
		{
			quest += " Quest";
		}
		return "Quest(" + quest + ")";
	}

	/**
	 * A player died. {@code me} = you; else their name, whether they are a friend or in your friends chat, in your clan,
	 * and drawn on screen (RuneLite skips players off screen).
	 */
	void death(boolean me, String name, boolean friend, boolean clan, boolean onScreen, long now)
	{
		if (me)
		{
			if (on("playerDeath", false))
			{
				add(DEATHS, OWN_DEATH, now);
			}
		}
		else if (onScreen && ((friend && on("friendDeath", false)) || (clan && on("clanDeath", false))))
		{
			add(DEATHS, "Death " + name, now);
		}
	}

	/** Your death animation at the Doom of Mokhaiotl. */
	void doomDeath(long now)
	{
		if (on("playerDeath", false))
		{
			add(DEATHS, DOOM_DEATH, now);
		}
	}

	/** You killed a player and got their loot. */
	void pvpKill(String name, long now)
	{
		if (on("kills", false))
		{
			add(PVP_KILLS, "Kill " + name, now);
		}
	}

	/** The player a friends-chat kick is about to remove. */
	void kickTarget(String name)
	{
		kickName = name;
	}

	/** A pop-up notification: its title and text, and whether combat tasks pop up (CA_TASK_POPUP == 0). */
	void notification(String top, String bottom, boolean taskPopup, long now)
	{
		if (top == null || bottom == null)
		{
			return;
		}
		String plain = Text.removeTags(bottom);
		if (top.equalsIgnoreCase("Collection log") && on("collectionLogEntries", true) && plain.length() >= "New item:".length())
		{
			add(COLLECTION_LOG, "Collection log (" + plain.substring("New item:".length()) + ")", now);
		}
		String[] s = bottom.split("<.*?>");
		if (top.equalsIgnoreCase("Combat Task Completed!") && on("combatAchievements", true) && taskPopup && s.length > 1)
		{
			add(COMBAT_ACHIEVEMENTS, "Combat task (" + s[1].replaceAll("[:?]", "") + ")", now);
		}
		if (top.equalsIgnoreCase("League Task Complete!") && on("screenshotLeagueTasks", true) && s.length > 1)
		{
			add(LEAGUE_TASKS, "League task (" + s[1].replaceAll("[:?]", "") + ")", now);
		}
	}

	/** The Doom of Mokhaiotl's loot was claimed. */
	void domLoot(long now)
	{
		if (on("rewards", true))
		{
			add(CHEST_LOOT, "Doom of Mokhaiotl", now);
		}
	}

	// ---- the game events themselves (client thread), registered on RuneLite's event bus while the plugin runs ----

	/** Feeds a {@link ShotNamer} from the game, as RuneLite's Screenshot plugin hears it; and knows the character's folder. */
	static final class Watch
	{
		private static final int GAUNTLET_REGION = 7512;
		private static final int CORRUPTED_GAUNTLET_REGION = 7768;

		private final Client client;
		private final ShotNamer namer;
		private boolean notificationStarted;
		/** RuneLite's folder for the logged-in character ("Zezima", "Zezima-Deadman"); null = no character */
		private volatile String character;

		Watch(Client client, ShotNamer namer)
		{
			this.client = client;
			this.namer = namer;
		}

		/** The character folder RuneLite files the screenshot under (any thread); null = none. */
		String character()
		{
			return character;
		}

		@Subscribe
		public void onGameTick(GameTick t)
		{
			Player me = client.getLocalPlayer();
			if (me != null && me.getName() != null)
			{
				RuneScapeProfileType type = RuneScapeProfileType.getCurrent(client);
				character = me.getName() + (type != RuneScapeProfileType.STANDARD ? "-" + Text.titleCase(type) : "");
			}
			if (namer.wantsTexts())
			{
				namer.texts(text(InterfaceID.LevelupDisplay.TEXT2), text(InterfaceID.Objectbox.TEXT),
					text(InterfaceID.Questscroll.QUEST_TITLE), System.currentTimeMillis());
			}
		}

		private String text(int component)
		{
			Widget w = client.getWidget(component);
			return w == null ? null : w.getText();
		}

		@Subscribe
		public void onChatMessage(ChatMessage e)
		{
			ChatMessageType type = e.getType();
			if (type != ChatMessageType.GAMEMESSAGE && type != ChatMessageType.SPAM && type != ChatMessageType.TRADE
				&& type != ChatMessageType.FRIENDSCHATNOTIFICATION)
			{
				return;
			}
			Player me = client.getLocalPlayer();
			int region = me == null ? -1 : me.getWorldLocation().getRegionID();
			WorldView view = client.getTopLevelWorldView();
			int[] regions = view == null ? null : view.getMapRegions();
			boolean gauntlet = view != null && view.isInstance() && regions != null && regions.length > 0
				&& (regions[0] == GAUNTLET_REGION || regions[0] == CORRUPTED_GAUNTLET_REGION);
			namer.chat(e.getMessage(), System.currentTimeMillis(), client.getVarbitValue(VarbitID.OPTION_COLLECTION_NEW_ITEM) == 1,
				client.getVarbitValue(VarbitID.CA_TASK_POPUP) == 1, client.getVarbitValue(VarbitID.OPTION_LEVEL_UP_MESSAGE_DISABLED) == 1,
				region, gauntlet);
		}

		@Subscribe
		public void onWidgetLoaded(WidgetLoaded e)
		{
			namer.widget(e.getGroupId(), System.currentTimeMillis());
		}

		@Subscribe
		public void onActorDeath(ActorDeath e)
		{
			Actor a = e.getActor();
			if (!(a instanceof Player))
			{
				return;
			}
			Player p = (Player) a;
			boolean me = p == client.getLocalPlayer();
			namer.death(me, p.getName(), !me && (p.isFriendsChatMember() || p.isFriend()), !me && p.isClanMember(),
				!me && p.getCanvasTilePoly() != null, System.currentTimeMillis());
		}

		@Subscribe
		public void onAnimationChanged(AnimationChanged e)
		{
			Actor a = e.getActor();
			if (a == client.getLocalPlayer() && a.getAnimation() == AnimationID.HUMAN_DOOM_SCORPION_01_PLAYER_DEATH_01)
			{
				namer.doomDeath(System.currentTimeMillis());
			}
		}

		@Subscribe
		public void onPlayerLootReceived(PlayerLootReceived e)
		{
			namer.pvpKill(e.getPlayer().getName(), System.currentTimeMillis());
		}

		@Subscribe
		public void onScriptCallbackEvent(ScriptCallbackEvent e)
		{
			if ("confirmFriendsChatKick".equals(e.getEventName()))
			{
				Object[] stack = client.getObjectStack();
				int size = client.getObjectStackSize();
				if (size > 0 && stack[size - 1] instanceof String)
				{
					namer.kickTarget((String) stack[size - 1]);
				}
			}
		}

		@Subscribe
		public void onScriptPreFired(ScriptPreFired e)
		{
			if (e.getScriptId() == ScriptID.NOTIFICATION_START)
			{
				notificationStarted = true;
			}
			else if (e.getScriptId() == ScriptID.NOTIFICATION_DELAY && notificationStarted)
			{
				namer.notification(client.getVarcStrValue(VarClientID.NOTIFICATION_TITLE),
					client.getVarcStrValue(VarClientID.NOTIFICATION_MAIN), client.getVarbitValue(VarbitID.CA_TASK_POPUP) == 0,
					System.currentTimeMillis());
				notificationStarted = false;
			}
		}

		@Subscribe
		public void onScriptPostFired(ScriptPostFired e)
		{
			if (e.getScriptId() == ScriptID.DOM_LOOT_CLAIM)
			{
				namer.domLoot(System.currentTimeMillis());
			}
		}
	}
}
