package net.runelite.client.plugins.microbot.geflipper;

import com.google.inject.Inject;
import com.google.inject.Provides;
import java.awt.AWTException;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.ClientUI;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(name="<html>[<font color=#8A2BE2>\u03a9</font>] Flipper", description="Flipping copilot automation", tags={"flip", "ge", "grand", "exchange", "automation"}, authors={"Choken"}, version="1.1.0", minClientVersion="2.0.7", cardUrl="https://chsami.github.io/Microbot-Hub/FlipperPlugin/assets/card.jpg", iconUrl="https://chsami.github.io/Microbot-Hub/FlipperPlugin/assets/icon.jpg", enabledByDefault=false, isExternal=true)
public class FlipperPlugin
extends Plugin {
    public static final String version = "1.1.0";
    @Inject
    private Client client;
    @Inject
    private FlipperScript flipperScript;
    @Inject
    private FlipperConfig config;
    @Inject
    private FlipperOverlay flipperOverlay;
    @Inject
    private OverlayManager overlayManager;

    @Provides
    FlipperConfig provideConfig(ConfigManager configManager) {
        return (FlipperConfig)configManager.getConfig(FlipperConfig.class);
    }

    protected void startUp() throws AWTException {
        overlayManager.add(flipperOverlay);
        // Disable manual input so physical mouse/keyboard don't interfere
        ClientUI.getClient().setEnabled(false);
        Microbot.getClient().getCanvas().setFocusable(false);
        this.flipperScript.run(config);
    }

    protected void shutDown() {
        overlayManager.remove(flipperOverlay);
        this.flipperScript.state = State.GOING_TO_GE;
        this.flipperScript.shutdown();
        // Re-enable manual input
        ClientUI.getClient().setEnabled(true);
        Microbot.getClient().getCanvas().setFocusable(true);
    }
}
