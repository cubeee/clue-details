/*
 * Copyright (c) 2025, cubeee <https://www.github.com/cubeee>
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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.*;

@Singleton
public class ClueDetailsWidgetsOverlay extends OverlayPanel
{
	private final Client client;
	private final ClueDetailsConfig config;
	private final ConfigManager configManager;

	private final ClueInventoryManager clueInventoryManager;
	private final CluePreferenceManager cluePreferenceManager;

	private final Cache<WidgetId, Color> clueWidgetColorCache;
	private final Cache<NamedWidgetId, Color> clueNamedWidgetColorCache;

	private long lastUpdate = Integer.MAX_VALUE;

	@Inject
	public ClueDetailsWidgetsOverlay(Client client, ClueDetailsConfig config, ConfigManager configManager,
	                                 ClueInventoryManager clueInventoryManager, CluePreferenceManager cluePreferenceManager)
	{
		setPriority(PRIORITY_HIGHEST);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setDragTargetable(false);
		setPosition(OverlayPosition.DYNAMIC);

		this.client = client;
		this.config = config;
		this.configManager = configManager;
		this.clueInventoryManager = clueInventoryManager;
		this.cluePreferenceManager = cluePreferenceManager;

		this.clueWidgetColorCache = CacheBuilder.newBuilder()
			.concurrencyLevel(1)
			.maximumSize(100)
			.build();
		this.clueNamedWidgetColorCache = CacheBuilder.newBuilder()
			.concurrencyLevel(1)
			.maximumSize(100)
			.build();
	}

	public void cacheInventoryCluesWidgetColors()
	{
		Color defaultHighlightColor = config.widgetHighlightColor();
		Map<WidgetId, Color> clueWidgetColors = new HashMap<>();
		Map<NamedWidgetId, Color> clueNamedWidgetColors = new HashMap<>();
		if (config.highlightInventoryClueWidgets())
		{
			for (Integer itemID : clueInventoryManager.getCluesInInventory())
			{
				if (itemID == null) continue;
				ClueInstance instance = clueInventoryManager.getClueByClueItemId(itemID);
				if (instance == null)
				{
					continue;
				}
				for (int clueId : instance.getClueIds())
				{
					Clues clue = Clues.forClueIdFiltered(clueId);
					if (clue == null || !clue.isEnabled(config))
					{
						continue;
					}
					Color clueColor = clue.getDetailColor(configManager);
					Color widgetColor = config.colorInventoryClueWidgets()
						? new Color(clueColor.getRed(), clueColor.getGreen(), clueColor.getBlue(), defaultHighlightColor.getAlpha())
						: defaultHighlightColor;
					List<WidgetId> widgetsPreference = cluePreferenceManager.getWidgetsPreference(clueId);
					if (widgetsPreference != null)
					{
						for (WidgetId widgetId : widgetsPreference)
						{
							clueWidgetColors.put(widgetId, widgetColor);
						}
					}
					Map<Integer, List<String>> namedWidgetsPreference = cluePreferenceManager.getNamedWidgetsPreference(clueId);
					if (namedWidgetsPreference != null)
					{
						namedWidgetsPreference.forEach((parentId, names) ->
						{
							NamedWidgetId namedWidgetId = new NamedWidgetId(parentId, names);
							clueNamedWidgetColors.put(namedWidgetId, widgetColor);
                        });
					}
				}
			}
		}
		clueWidgetColorCache.invalidateAll();
		clueWidgetColorCache.putAll(clueWidgetColors);
		clueNamedWidgetColorCache.invalidateAll();
		clueNamedWidgetColorCache.putAll(clueNamedWidgetColors);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		long lastInventoryUpdate = clueInventoryManager.getLastInventoryUpdate();
		if (lastUpdate != lastInventoryUpdate)
		{
			lastUpdate = lastInventoryUpdate;
			cacheInventoryCluesWidgetColors();
		}

		boolean hasWidgetRecolors = clueWidgetColorCache.size() > 0 || clueNamedWidgetColorCache.size() > 0;

		if (config.highlightInventoryClueWidgets() && hasWidgetRecolors)
		{
			for (Map.Entry<NamedWidgetId, Color> entry : clueNamedWidgetColorCache.asMap().entrySet())
			{
				NamedWidgetId namedWidgetId = entry.getKey();
				Color widgetColor = entry.getValue();

				Widget parentWidget = client.getWidget(namedWidgetId.getParentId());
				if (parentWidget == null || parentWidget.isHidden())
				{
					continue;
				}

				Widget[] children = parentWidget.getStaticChildren();
				if (children == null)
				{
					continue;
				}

				// TODO: cache spellbook widget ids on demand and use it here.
				//  constantly looking by name is giga slow
				for (Widget childWidget : children)
				{
					if (childWidget == null)
					{
						continue;
					}

					if (!namedWidgetId.getNames().contains(childWidget.getName()))
					{
						continue;
					}

					if (childWidget.isHidden())
					{
						continue;
					}

					graphics.setColor(widgetColor);
					graphics.setClip(childWidget.getBounds());
					graphics.fill(childWidget.getBounds());
				}
			}

			for (Map.Entry<WidgetId, Color> entry : clueWidgetColorCache.asMap().entrySet())
			{
				WidgetId widgetId = entry.getKey();
				int componentId = widgetId.getComponentId();
				Integer childIndex = widgetId.getChildIndex();
				Color widgetColor = entry.getValue();

				Widget widget = client.getWidget(componentId);
				Widget parentWidget = null;

				if (widget != null && childIndex != null && childIndex != -1)
				{
					Widget[] children = widget.getChildren();
					if (children != null && childIndex < children.length)
					{
						parentWidget = widget.getParent();
						widget = children[childIndex];
					}
				}

				if (widget == null || widget.isHidden())
				{
					continue;
				}

				// parent is set earlier for child widgets but if not, use the normal widget's parent
				if (parentWidget == null)
				{
                    parentWidget = widget.getParent();
				}

				graphics.setColor(widgetColor);

				// Highlight as much of the widget as is visible of it inside its parent
				if (parentWidget != null)
				{
					graphics.setClip(parentWidget.getBounds());
				}
				graphics.fill(widget.getBounds());
			}
		}
		return super.render(graphics);
	}
}
