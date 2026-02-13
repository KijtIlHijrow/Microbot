package net.runelite.client.plugins.microbot.geflipper;

import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;
import java.time.Duration;

public class FlipperOverlay extends OverlayPanel {

    private final FlipperScript flipperScript;
    private final FlipperConfig config;

    @Inject
    FlipperOverlay(FlipperPlugin plugin, FlipperScript flipperScript, FlipperConfig config) {
        super(plugin);
        this.flipperScript = flipperScript;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ALWAYS_ON_TOP);
        setPreferredLocation(new Point(config.overlayX(), config.overlayY()));
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        // Update position from config each frame so changes take effect live
        setPreferredLocation(new Point(config.overlayX(), config.overlayY()));

        panelComponent.getChildren().clear();
        panelComponent.setPreferredSize(new Dimension(190, 0));

        // Title with state-dependent color
        Color titleColor = getTitleColor();
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Flipper v1.1.0")
                .color(titleColor)
                .build());

        panelComponent.getChildren().add(LineComponent.builder().build());

        // State
        panelComponent.getChildren().add(LineComponent.builder()
                .left("State:")
                .right(flipperScript.state.name().replace("_", " "))
                .rightColor(getStateColor(flipperScript.state))
                .build());

        // Status
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Status:")
                .right(flipperScript.status)
                .rightColor(getStatusColor())
                .build());

        // Break info
        if (flipperScript.state == State.ON_BREAK) {
            String breakType = flipperScript.isMacroBreak ? "Macro" : "Micro";
            long remaining = Math.max(0, (flipperScript.breakEndTime - System.currentTimeMillis()) / 1000L);
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Break:")
                    .right(breakType + " - " + formatTime(remaining))
                    .rightColor(Color.ORANGE)
                    .build());
        } else {
            // Next micro break
            long secsUntilMicro = flipperScript.getSecondsUntilMicroBreak();
            if (secsUntilMicro >= 0) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Micro break:")
                        .right(formatTime(secsUntilMicro))
                        .build());
            }

            // Next macro break
            long secsUntilMacro = flipperScript.getSecondsUntilMacroBreak();
            if (secsUntilMacro >= 0) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Macro break:")
                        .right(formatTime(secsUntilMacro))
                        .build());
            }
        }

        // Session time remaining
        long secsUntilEnd = flipperScript.getSecondsUntilSessionEnd();
        if (secsUntilEnd >= 0) {
            boolean urgent = secsUntilEnd < 300; // <5 min
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Session left:")
                    .right(formatTime(secsUntilEnd))
                    .rightColor(urgent ? Color.RED : Color.WHITE)
                    .build());
        }

        // Actions performed
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Actions:")
                .right(String.valueOf(flipperScript.actionCount))
                .build());

        // Current copilot suggestion
        String suggestion = flipperScript.getCurrentSuggestionType();
        if (suggestion != null) {
            Color suggColor = "wait".equals(suggestion) ? Color.GRAY : Color.CYAN;
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Copilot:")
                    .right(suggestion)
                    .rightColor(suggColor)
                    .build());
        }

        // Runtime
        if (flipperScript.startTime > 0L) {
            long runtime = (System.currentTimeMillis() - flipperScript.startTime) / 1000L;
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Runtime:")
                    .right(formatTime(runtime))
                    .build());
        }

        return super.render(graphics);
    }

    private Color getTitleColor() {
        switch (flipperScript.state) {
            case ON_BREAK:
                return Color.ORANGE;
            case SESSION_ENDING:
                return Color.RED;
            default:
                return Color.GREEN;
        }
    }

    private Color getStatusColor() {
        switch (flipperScript.state) {
            case ON_BREAK:
                return Color.ORANGE;
            case SESSION_ENDING:
                return Color.RED;
            default:
                return Color.WHITE;
        }
    }

    private String formatTime(long totalSeconds) {
        Duration d = Duration.ofSeconds(totalSeconds);
        long hours = d.toHours();
        long minutes = d.toMinutesPart();
        long seconds = d.toSecondsPart();
        if (hours > 0) {
            return String.format("%dh %02dm", hours, minutes);
        }
        return String.format("%dm %02ds", minutes, seconds);
    }

    private Color getStateColor(State state) {
        switch (state) {
            case GOING_TO_GE:
                return Color.YELLOW;
            case GETTING_COINS:
                return Color.CYAN;
            case MONITORING_COPILOT:
                return Color.GREEN;
            case ON_BREAK:
                return Color.ORANGE;
            case SESSION_ENDING:
                return Color.RED;
            default:
                return Color.WHITE;
        }
    }
}
