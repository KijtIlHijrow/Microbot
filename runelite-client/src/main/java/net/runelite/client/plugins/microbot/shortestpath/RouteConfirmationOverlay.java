package net.runelite.client.plugins.microbot.shortestpath;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;
import java.util.List;

public class RouteConfirmationOverlay extends OverlayPanel {

    private static final Color COLOR_TITLE = Color.YELLOW;
    private static final Color COLOR_TRANSPORT = Color.CYAN;
    private static final Color COLOR_WALK = Color.WHITE;
    private static final Color COLOR_HOTKEY = new Color(255, 176, 46);
    private static final Color COLOR_BACKGROUND = new Color(0, 0, 0, 180);

    private volatile RouteConfirmation routeAnalysis;

    @Inject
    RouteConfirmationOverlay(ShortestPathPlugin plugin) {
        setPosition(OverlayPosition.CANVAS_TOP_RIGHT);
        setNaughty();
    }

    public void setRouteAnalysis(RouteConfirmation analysis) {
        this.routeAnalysis = analysis;
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        RouteConfirmation analysis = this.routeAnalysis;
        if (analysis == null || !analysis.isPending()) {
            return null;
        }

        List<RouteSegment> segments = analysis.getSegments();
        if (segments == null || segments.isEmpty()) {
            return null;
        }

        panelComponent.setBackgroundColor(COLOR_BACKGROUND);
        panelComponent.setPreferredSize(new Dimension(220, 0));

        // Title
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Route Preview")
                .color(COLOR_TITLE)
                .build());

        // Segment rows
        int totalSteps = 0;
        int transportCount = 0;

        for (RouteSegment segment : segments) {
            totalSteps += segment.getSteps();
            if (segment.isTransport()) {
                transportCount++;
            }

            Color lineColor = segment.isTransport() ? COLOR_TRANSPORT : COLOR_WALK;
            String stepLabel = segment.getSteps() == 1 ? "1 step" : segment.getSteps() + " steps";

            panelComponent.getChildren().add(LineComponent.builder()
                    .left(segment.getDescription())
                    .leftColor(lineColor)
                    .right(stepLabel)
                    .rightColor(lineColor)
                    .build());
        }

        // Blank line via empty component
        panelComponent.getChildren().add(LineComponent.builder()
                .left("")
                .build());

        // Total
        String totalLabel = "Total: " + totalSteps + " steps";
        if (transportCount > 0) {
            totalLabel += " (" + transportCount + " transport" + (transportCount > 1 ? "s" : "") + ")";
        }
        panelComponent.getChildren().add(LineComponent.builder()
                .left(totalLabel)
                .leftColor(Color.WHITE)
                .build());

        // Missing items section
        List<RouteConfirmation.MissingItemInfo> missingItems = analysis.getMissingItems();
        if (missingItems != null && !missingItems.isEmpty()) {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("")
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Could be faster with:")
                    .leftColor(COLOR_HOTKEY)
                    .build());

            int shown = 0;
            for (RouteConfirmation.MissingItemInfo item : missingItems) {
                if (shown >= 5) break;
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("  " + item.getName())
                        .leftColor(COLOR_HOTKEY)
                        .right("x" + item.getQuantity())
                        .rightColor(COLOR_HOTKEY)
                        .build());
                shown++;
            }
        }

        // Blank line
        panelComponent.getChildren().add(LineComponent.builder()
                .left("")
                .build());

        // Hotkey legend
        panelComponent.getChildren().add(LineComponent.builder()
                .left("[Enter] Go")
                .leftColor(COLOR_HOTKEY)
                .build());

        if (analysis.isHasTransports()) {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("[W] Walk only")
                    .leftColor(COLOR_HOTKEY)
                    .build());
        }

        panelComponent.getChildren().add(LineComponent.builder()
                .left("[Esc] Cancel")
                .leftColor(COLOR_HOTKEY)
                .build());

        return super.render(graphics);
    }
}
