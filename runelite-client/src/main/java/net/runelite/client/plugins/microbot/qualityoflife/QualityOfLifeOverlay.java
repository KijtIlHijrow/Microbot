package net.runelite.client.plugins.microbot.qualityoflife;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.ObjectID;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public class QualityOfLifeOverlay extends Overlay {

	private static final Set<Integer> GE_BOOTH_IDS = Set.of(
		ObjectID.GRAND_EXCHANGE_BOOTH,
		ObjectID.GRAND_EXCHANGE_BOOTH_10061,
		ObjectID.GRAND_EXCHANGE_BOOTH_30390
	);

	private final QualityOfLifePlugin plugin;
	private final QualityOfLifeConfig config;

	@Inject
	public QualityOfLifeOverlay(QualityOfLifePlugin plugin, QualityOfLifeConfig config) {
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics) {
		if (!Microbot.isLoggedIn()) return null;
		if (!config.geClerkHighlight()) return null;
		if (!plugin.hasCompletedOffer()) return null;

		Color color = config.geClerkHighlightColor();
		Color borderColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.min(255, color.getAlpha() + 80));

		highlightGeClerks(graphics, color, borderColor);
		highlightGeBooths(graphics, color, borderColor);

		return null;
	}

	private void highlightGeClerks(Graphics2D graphics, Color fill, Color border) {
		for (NPC npc : Microbot.getClient().getTopLevelWorldView().npcs()) {
			if (npc == null) continue;
			String name = npc.getName();
			if (name == null || !name.equals("Grand Exchange Clerk")) continue;

			Shape hull = npc.getConvexHull();
			if (hull == null) continue;

			drawHighlight(graphics, hull, fill, border);
		}
	}

	private void highlightGeBooths(Graphics2D graphics, Color fill, Color border) {
		Scene scene = Microbot.getClient().getTopLevelWorldView().getScene();
		Tile[][][] tiles = scene.getTiles();
		int plane = Microbot.getClient().getTopLevelWorldView().getPlane();

		for (int x = 0; x < tiles[plane].length; x++) {
			for (int y = 0; y < tiles[plane][x].length; y++) {
				Tile tile = tiles[plane][x][y];
				if (tile == null) continue;

				// GameObjects
				for (GameObject obj : tile.getGameObjects()) {
					if (obj != null) tryHighlightObject(graphics, obj, fill, border);
				}

				// WallObjects (GE booths are wall objects)
				WallObject wall = tile.getWallObject();
				if (wall != null) tryHighlightObject(graphics, wall, fill, border);

				// DecorativeObjects
				DecorativeObject deco = tile.getDecorativeObject();
				if (deco != null) tryHighlightObject(graphics, deco, fill, border);
			}
		}
	}

	private void tryHighlightObject(Graphics2D graphics, TileObject obj, Color fill, Color border) {
		if (!GE_BOOTH_IDS.contains(obj.getId())) return;

		Shape clickbox = obj.getClickbox();
		if (clickbox == null) return;

		drawHighlight(graphics, clickbox, fill, border);
	}

	private void drawHighlight(Graphics2D graphics, Shape shape, Color fill, Color border) {
		graphics.setColor(fill);
		graphics.fill(shape);
		graphics.setColor(border);
		graphics.setStroke(new BasicStroke(2));
		graphics.draw(shape);
	}
}
