/*
 * Copyright (c) 2021, Adam <Adam@sigterm.info>
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
import static com.cluedetails.ClueDetailsConfig.CLUE_WIDGETS_CONFIG;

import com.google.common.base.Strings;
import com.google.common.util.concurrent.Runnables;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import java.awt.Color;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.plugins.grounditems.GroundItemsConfig;
import net.runelite.client.plugins.inventorytags.InventoryTagsConfig;

@Slf4j
public class ClueDetailsSharingManager
{
	private final ClueDetailsPlugin plugin;
	private final ClueDetailsConfig config;
	private final ChatboxPanelManager chatboxPanelManager;
	private final Gson gson;

	private final ConfigManager configManager;

	@Inject
	private ClueDetailsSharingManager(ClueDetailsPlugin plugin, ClueDetailsConfig config, ChatboxPanelManager chatboxPanelManager,
										Gson gson, ConfigManager configManager)
	{
		this.plugin = plugin;
		this.config = config;
		this.chatboxPanelManager = chatboxPanelManager;
		this.gson = gson;
		this.configManager = configManager;
	}

	public void resetClueDetails(boolean resetText, boolean resetColors, boolean resetItems, boolean resetWidgets)
	{
		List<Clues> filteredClues = Clues.CLUES.stream()
			.filter(config.filterListByTier())
			.filter(config.filterListByRegion())
			.collect(Collectors.toList());

		for (Clues clue : filteredClues)
		{
			int id = clue.getClueID();
			if (resetText) configManager.unsetConfiguration("clue-details-text", String.valueOf(id));
			if (resetColors) configManager.unsetConfiguration("clue-details-color", String.valueOf(id));
			if (resetItems) configManager.unsetConfiguration(CLUE_ITEMS_CONFIG, String.valueOf(id));
			if (resetWidgets) configManager.unsetConfiguration(CLUE_WIDGETS_CONFIG, String.valueOf(id));
			// TODO: reset named widgets
		}
	}

	public void exportClueDetails(boolean exportText, boolean exportColors, boolean exportItems, boolean exportWidgets)
	{
		List<ClueIdToDetails> clueIdToDetailsList = new ArrayList<>();

		List<Clues> filteredClues = Clues.CLUES.stream()
			.filter(config.filterListByTier())
			.filter(config.filterListByRegion())
			.collect(Collectors.toList());

		for (Clues clue : filteredClues)
		{
			int id = clue.getClueID();
			String clueText = exportText ? configManager.getConfiguration("clue-details-text", String.valueOf(id)) : null;
			String clueColor = exportColors ? configManager.getConfiguration("clue-details-color", String.valueOf(id)) : null;
			String clueItems = exportItems ? configManager.getConfiguration(CLUE_ITEMS_CONFIG, String.valueOf(id)) : null;
			String clueWidgets = exportWidgets ? configManager.getConfiguration(CLUE_WIDGETS_CONFIG, String.valueOf(id)) : null;

			// Try to export text, color, and items. Export where valid configurations are returned
			List<Integer> loadedClueItemsData = clueItems != null
					? gson.fromJson(clueItems, new TypeToken<List<Integer>>(){}.getType())
					: null;

			List<WidgetId> loadedClueWidgetsData = clueWidgets != null
					? gson.fromJson(clueWidgets, new TypeToken<List<WidgetId>>(){}.getType())
					: null;

			// TODO: load named widgets

			Color exportedColor = clueColor != null ? Color.decode(clueColor) : null;

			ClueIdToDetails clueDetails = new ClueIdToDetails(id, clueText, exportedColor, loadedClueItemsData, loadedClueWidgetsData);
			if (clueText != null || exportedColor != null || loadedClueItemsData != null || loadedClueWidgetsData != null)
			{
				clueIdToDetailsList.add(clueDetails);
			}
		}

		if (clueIdToDetailsList.isEmpty())
		{
			sendChatMessage("You have no updated clue details to export.");
			return;
		}

		final String exportDump = gson.toJson(clueIdToDetailsList);

		final String sortedExportDump = sortJsonArrayById(gson, exportDump);

		log.debug("Exported clue details: {}", sortedExportDump);

		Toolkit.getDefaultToolkit()
			.getSystemClipboard()
			.setContents(new StringSelection(sortedExportDump), null);
		sendChatMessage(clueIdToDetailsList.size() + " clue details were copied to your clipboard.");
	}

	public static String sortJsonArrayById(Gson gson, String jsonString)
	{
		try
		{
			JsonArray jsonArray = gson.fromJson(jsonString, JsonArray.class);
			List<JsonObject> jsonList = new ArrayList<>();

			// Convert JsonArray to a List of JsonObjects
			for (JsonElement element : jsonArray)
			{
				if (element.isJsonObject())
				{
					jsonList.add(element.getAsJsonObject());
				}
			}

			// Sort the list based on the "id" key
			jsonList.sort(Comparator.comparingInt(obj -> obj.get("id").getAsInt()));

			// Convert the sorted list back to a JsonArray
			JsonArray sortedJsonArray = new JsonArray();
			for (JsonObject jsonObject : jsonList)
			{
				sortedJsonArray.add(jsonObject);
			}

			return gson.toJson(sortedJsonArray);
		}
		catch (Exception e)
		{
			log.error("Error processing JSON export.", e);
			return null;
		}
	}

	public void promptForImport()
	{
		final String clipboardText;
		try
		{
			clipboardText = Toolkit.getDefaultToolkit()
				.getSystemClipboard()
				.getData(DataFlavor.stringFlavor)
				.toString();
		}
		catch (IOException | UnsupportedFlavorException ex)
		{
			sendChatMessage("Unable to read system clipboard.");
			log.warn("error reading clipboard", ex);
			return;
		}

		log.debug("Clipboard contents: {}", clipboardText);
		if (Strings.isNullOrEmpty(clipboardText))
		{
			sendChatMessage("You do not have any clue details copied in your clipboard.");
			return;
		}

		List<ClueIdToDetails> importClueDetails;
		try
		{
			// CHECKSTYLE:OFF
			importClueDetails = gson.fromJson(clipboardText, new TypeToken<List<ClueIdToDetails>>(){}.getType());
			// CHECKSTYLE:ON
		}
		catch (JsonSyntaxException e)
		{
			log.debug("Malformed JSON for clipboard import", e);
			sendChatMessage("You do not have any clue details copied in your clipboard.");
			return;
		}
		catch (NumberFormatException e)
		{
			log.debug("Malformed JSON for clipboard import", e);
			sendChatMessage("Your clue details color is not properly formatted.");
			return;
		}

		if (importClueDetails.isEmpty())
		{
			sendChatMessage("You do not have any clue details copied in your clipboard.");
			return;
		}

		chatboxPanelManager.openTextMenuInput("Are you sure you want to import " + importClueDetails.size() + " clue details?")
			.option("Yes", () -> importClueDetails(importClueDetails))
			.option("No", Runnables.doNothing())
			.build();
	}

	private void importClueDetails(Collection<ClueIdToDetails> importPoints)
	{
		for (ClueIdToDetails importPoint : importPoints)
		{
			if (importPoint.text != null)
			{
				configManager.setConfiguration("clue-details-text", String.valueOf(importPoint.id), importPoint.text);
			}
			if (importPoint.color != null)
			{
				// Default color is white, so white is used to unset configurations
				if (ClueIdToDetails.equalRGB(importPoint.color, Color.WHITE))
				{
					configManager.unsetConfiguration("clue-details-color", String.valueOf(importPoint.id));

					// Reset Ground Items and Inventory Tags
					// Beginner & master clues are not supported by these plugins
					if (importPoint.id >= 2677)
					{
						if (config.colorGroundItems())
						{
							configManager.unsetConfiguration(GroundItemsConfig.GROUP, "highlight_" + importPoint.id);
						}
						if (config.colorInventoryTags())
						{
							configManager.unsetConfiguration(InventoryTagsConfig.GROUP, "tag_" + importPoint.id);
						}
					}
				}
				else
				{
					configManager.setConfiguration("clue-details-color", String.valueOf(importPoint.id), importPoint.color);

					// Apply color to Ground Items and Inventory Tags
					// Beginner & master clues are not supported by these plugins
					if (importPoint.id >= 2677)
					{
						// Ensure ARGB format
						Color color = Color.decode(configManager.getConfiguration("clue-details-color", String.valueOf(importPoint.id)));

						if (config.colorGroundItems())
						{
							configManager.setConfiguration(GroundItemsConfig.GROUP, "highlight_" + importPoint.id, color);
						}
						if (config.colorInventoryTags())
						{
							configManager.setConfiguration(InventoryTagsConfig.GROUP, "tag_" + importPoint.id,
								gson.toJson(Map.of("color", color)));
						}
					}
				}
			}
			if (importPoint.itemIds != null)
			{
				if (importPoint.itemIds.isEmpty())
				{
					configManager.unsetConfiguration(CLUE_ITEMS_CONFIG, String.valueOf(importPoint.id));
				}
				else
				{
					configManager.setConfiguration(CLUE_ITEMS_CONFIG, String.valueOf(importPoint.id), importPoint.itemIds);
				}
			}
			if (importPoint.widgetIds != null)
			{
				if (importPoint.widgetIds.isEmpty())
				{
					configManager.unsetConfiguration(CLUE_WIDGETS_CONFIG, String.valueOf(importPoint.id));
				}
				else
				{
					configManager.setConfiguration(CLUE_WIDGETS_CONFIG, String.valueOf(importPoint.id), gson.toJson(importPoint.widgetIds));
				}
			}
			// TODO: named widgets
		}

		sendChatMessage(importPoints.size() + " clue details were imported from the clipboard.");
		plugin.getPanel().refresh();
	}

	private void sendChatMessage(final String message)
	{
		plugin.getChatMessageManager().queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(message)
			.build());
	}
}
