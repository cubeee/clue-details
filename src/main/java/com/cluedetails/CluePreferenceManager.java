/*
 * Copyright (c) 2024, Zoinkwiz <https://github.com/Zoinkwiz>
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
package com.cluedetails;

import static com.cluedetails.ClueDetailsConfig.CLUE_ITEMS_CONFIG;
import static com.cluedetails.ClueDetailsConfig.CLUE_NAMED_WIDGETS_CONFIG;
import static com.cluedetails.ClueDetailsConfig.CLUE_WIDGETS_CONFIG;

import com.google.gson.reflect.TypeToken;

import java.util.*;
import java.util.stream.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;

@Singleton
public class CluePreferenceManager
{
	private final ClueDetailsPlugin clueDetailsPlugin;
	private final ConfigManager configManager;

	@Inject
	public CluePreferenceManager(ClueDetailsPlugin clueDetailsPlugin, ConfigManager configManager)
	{
		this.clueDetailsPlugin = clueDetailsPlugin;
		this.configManager = configManager;
	}

	public boolean getHighlightPreference(int clueID)
	{
		return Boolean.TRUE.equals(configManager.getConfiguration("clue-details-highlights",
			String.valueOf(clueID), Boolean.class));
	}

	public void saveHighlightPreference(int clueID, boolean newValue)
	{
		configManager.setConfiguration("clue-details-highlights", String.valueOf(clueID), newValue);
	}

	public boolean itemsPreferenceContainsItem(int clueID, int itemID)
	{
		List<Integer> clueItemIds = getItemsPreference(clueID);

		if (clueItemIds != null)
		{
			return getItemsPreference(clueID).contains(itemID);
		}
		return false;
	}

	public List<Integer> getItemsPreference(int clueID)
	{
		String clueItems = configManager.getConfiguration(CLUE_ITEMS_CONFIG, String.valueOf(clueID));

		return clueDetailsPlugin.gson.fromJson(clueItems, new TypeToken<List<Integer>>(){}.getType());
	}

	public void saveItemsPreference(int clueID, List<Integer> newItems)
	{
		if (newItems.isEmpty())
		{
			configManager.unsetConfiguration(CLUE_ITEMS_CONFIG, String.valueOf(clueID));
		}
		else
		{
			String clueItemIdsJson = clueDetailsPlugin.gson.toJson(newItems);
			configManager.setConfiguration(CLUE_ITEMS_CONFIG, String.valueOf(clueID), clueItemIdsJson);
		}
	}

	public boolean widgetsPreferenceContainsWidget(int clueID, WidgetId widgetId)
	{
		List<WidgetId> clueWidgetIds = getWidgetsPreference(clueID);

		if (clueWidgetIds != null)
		{
			return clueWidgetIds.contains(widgetId);
		}
		return false;
	}

	public boolean widgetsPreferenceContainsNamedWidget(int clueID, int parentId, String name)
	{
		Map<Integer, List<String>> namedClueWidgets = getNamedWidgetsPreference(clueID);
		if (namedClueWidgets != null)
		{
			List<String> names = namedClueWidgets.get(parentId);
			if (names != null)
			{
				return names.contains(name);
			}
		}
		return false;
	}

	public List<WidgetId> getWidgetsPreference(int clueID)
	{
		String clueWidgets = configManager.getConfiguration(CLUE_WIDGETS_CONFIG, String.valueOf(clueID));

		return clueDetailsPlugin.gson.fromJson(clueWidgets, new TypeToken<List<WidgetId>>(){}.getType());
	}

	public Map<Integer, List<String>> getNamedWidgetsPreference(int clueID)
	{
		String namedClueWidgets = configManager.getConfiguration(CLUE_NAMED_WIDGETS_CONFIG, String.valueOf(clueID));
		return clueDetailsPlugin.gson.fromJson(namedClueWidgets, new TypeToken<Map<Integer, List<String>>>(){}.getType());
	}

	public void saveWidgetsPreference(int clueID, List<WidgetId> newWidgets)
	{
		if (newWidgets.isEmpty())
		{
			configManager.unsetConfiguration(CLUE_WIDGETS_CONFIG, String.valueOf(clueID));
		}
		else
		{
			List<WidgetId> mappedWidgetIds = newWidgets
				.stream()
				.map(w -> new WidgetId(w.getComponentId(), w.getChildIndex() == null || w.getChildIndex() == -1 ? null : w.getChildIndex()))
				.collect(Collectors.toList());
			String clueWidgetIdsJson = clueDetailsPlugin.gson.toJson(mappedWidgetIds);
			configManager.setConfiguration(CLUE_WIDGETS_CONFIG, String.valueOf(clueID), clueWidgetIdsJson);
		}
	}

	public void saveNamedWidgetsPreference(int clueID, Map<Integer, List<String>> newWidgets)
	{
		if (newWidgets.isEmpty())
		{
			configManager.unsetConfiguration(CLUE_NAMED_WIDGETS_CONFIG, String.valueOf(clueID));
		}
		else
		{
			String clueWidgetIdsJson = clueDetailsPlugin.gson.toJson(newWidgets);
			configManager.setConfiguration(CLUE_NAMED_WIDGETS_CONFIG, String.valueOf(clueID), clueWidgetIdsJson);
		}
	}
}
