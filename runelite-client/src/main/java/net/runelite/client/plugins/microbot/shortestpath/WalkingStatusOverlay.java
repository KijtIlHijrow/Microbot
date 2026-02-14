package net.runelite.client.plugins.microbot.shortestpath;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.Pathfinder;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;
import java.util.List;

public class WalkingStatusOverlay extends OverlayPanel {

    private static final Color COLOR_WALKING = new Color(0, 200, 80);
    private static final Color COLOR_CALCULATING = Color.YELLOW;
    private static final Color COLOR_BG = new Color(30, 30, 30, 200);
    private static final Color COLOR_TRANSPORT = Color.CYAN;
    private static final Color COLOR_WALK_SEG = Color.GRAY;
    private static final Color COLOR_CURRENT = new Color(0, 255, 130);
    private static final Color COLOR_TELEPORT_ITEM = new Color(255, 176, 46);

    @Inject
    WalkingStatusOverlay(ShortestPathPlugin plugin) {
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        Pathfinder pathfinder = ShortestPathPlugin.getPathfinder();
        WorldPoint target = Rs2Walker.getCurrentTarget();

        // Only show when walker is active
        boolean hasPath = pathfinder != null && pathfinder.isDone() && pathfinder.getPath() != null && !pathfinder.getPath().isEmpty();
        boolean hasTarget = target != null;

        if (!hasPath && !hasTarget) {
            return null;
        }

        panelComponent.setBackgroundColor(COLOR_BG);
        panelComponent.setPreferredSize(new Dimension(180, 0));

        boolean calculating = pathfinder != null && !pathfinder.isDone();

        // Title
        panelComponent.getChildren().add(TitleComponent.builder()
                .text(calculating ? "Calculating..." : "Walking")
                .color(calculating ? COLOR_CALCULATING : COLOR_WALKING)
                .build());

        // Target location
        WorldPoint dest = target;
        if (dest == null && pathfinder != null && pathfinder.getPath() != null && !pathfinder.getPath().isEmpty()) {
            List<WorldPoint> path = pathfinder.getPath();
            dest = path.get(path.size() - 1);
        }

        if (dest != null) {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("To:")
                    .leftColor(Color.LIGHT_GRAY)
                    .right(dest.getX() + ", " + dest.getY())
                    .rightColor(Color.WHITE)
                    .build());
        }

        // Remaining tiles
        int tilesRemaining = -1;
        int closestIndex = -1;
        int totalPathSize = 0;
        if (hasPath && pathfinder.isDone()) {
            WorldPoint playerLoc = Rs2Player.getWorldLocation();
            if (playerLoc != null) {
                List<WorldPoint> path = pathfinder.getPath();
                totalPathSize = path.size();
                closestIndex = findClosestIndex(playerLoc, path);
                tilesRemaining = path.size() - closestIndex;

                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Tiles left:")
                        .leftColor(Color.LIGHT_GRAY)
                        .right(String.valueOf(tilesRemaining))
                        .rightColor(Color.WHITE)
                        .build());
            }
        }

        // Route segments
        List<RouteSegment> segments = Rs2Walker.getActiveRouteSegments();
        if (segments != null && !segments.isEmpty()) {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("")
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Route:")
                    .leftColor(Color.LIGHT_GRAY)
                    .build());

            // Determine which segment the player is currently in
            int currentSegment = getCurrentSegmentIndex(segments, closestIndex, totalPathSize);

            int shown = 0;
            for (int i = 0; i < segments.size() && shown < 8; i++) {
                RouteSegment seg = segments.get(i);

                boolean isCurrent = (i == currentSegment);
                boolean isTransport = seg.isTransport();

                Color lineColor;
                if (isCurrent) {
                    lineColor = COLOR_CURRENT;
                } else if (isTransport && seg.getTransportType() == TransportType.TELEPORTATION_ITEM) {
                    lineColor = COLOR_TELEPORT_ITEM;
                } else if (isTransport) {
                    lineColor = COLOR_TRANSPORT;
                } else {
                    lineColor = COLOR_WALK_SEG;
                }

                String prefix = isCurrent ? "> " : "  ";
                String label = seg.getDescription();
                String right = isTransport ? "" : seg.getSteps() + " tiles";

                panelComponent.getChildren().add(LineComponent.builder()
                        .left(prefix + label)
                        .leftColor(lineColor)
                        .right(right)
                        .rightColor(lineColor)
                        .build());
                shown++;
            }

            if (segments.size() > 8) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("  +" + (segments.size() - 8) + " more...")
                        .leftColor(Color.GRAY)
                        .build());
            }
        }

        return super.render(graphics);
    }

    private int getCurrentSegmentIndex(List<RouteSegment> segments, int closestPathIndex, int totalPathSize) {
        if (closestPathIndex < 0 || totalPathSize <= 0 || segments == null || segments.isEmpty()) {
            return 0;
        }

        // Walk through segments, summing up their steps to find which segment
        // contains the current path index
        int accumulated = 0;
        for (int i = 0; i < segments.size(); i++) {
            accumulated += segments.get(i).getSteps();
            if (closestPathIndex < accumulated) {
                return i;
            }
        }
        return segments.size() - 1;
    }

    private int findClosestIndex(WorldPoint player, List<WorldPoint> path) {
        int best = 0;
        int bestDist = Integer.MAX_VALUE;
        for (int i = 0; i < path.size(); i++) {
            int d = player.distanceTo(path.get(i));
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }
}
