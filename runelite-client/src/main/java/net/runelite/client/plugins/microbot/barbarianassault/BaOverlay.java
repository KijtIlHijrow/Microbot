package net.runelite.client.plugins.microbot.barbarianassault;

import net.runelite.client.plugins.microbot.barbarianassault.enums.BaRole;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class BaOverlay extends OverlayPanel {

    private final BaPlugin plugin;
    private final BaConfig config;

    @Inject
    public BaOverlay(BaPlugin plugin, BaConfig config) {
        super(plugin);
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.getChildren().clear();

            panelComponent.getChildren().add(TitleComponent.builder()
                .text("BA Automator")
                .color(Color.CYAN)
                .build());

            // Status
            String status = plugin.isInGame() ? "IN GAME" : "WAITING";
            Color statusColor = plugin.isInGame() ? Color.GREEN : Color.ORANGE;
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Status:")
                .right(status)
                .rightColor(statusColor)
                .build());

            // Wave
            if (plugin.isInGame()) {
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("Wave:")
                    .right(String.valueOf(plugin.getCurrentWave()))
                    .build());
            }

            // Role
            BaRole role = plugin.getCurrentRole();
            String roleText = role != null ? role.getDisplayName() : "None";
            Color roleColor = getRoleColor(role);
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Role:")
                .right(roleText)
                .rightColor(roleColor)
                .build());

            // Current call
            String call = plugin.getCurrentCall();
            if (call != null && !call.isEmpty()) {
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("Call:")
                    .right(call)
                    .rightColor(Color.YELLOW)
                    .build());
            }

            // Config role (if different from detected)
            BaRole configRole = config.selectedRole();
            if (configRole != BaRole.AUTO) {
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("Playing as:")
                    .right(configRole.getDisplayName())
                    .rightColor(getRoleColor(configRole))
                    .build());
            }

        } catch (Exception e) {
            // Silently ignore render errors
        }

        return super.render(graphics);
    }

    private Color getRoleColor(BaRole role) {
        if (role == null) return Color.GRAY;
        switch (role) {
            case ATTACKER:
                return Color.RED;
            case DEFENDER:
                return Color.BLUE;
            case COLLECTOR:
                return Color.YELLOW;
            case HEALER:
                return Color.GREEN;
            default:
                return Color.WHITE;
        }
    }
}
