package net.runelite.client.plugins.microbot.barbarianassault;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.barbarianassault.enums.BaRole;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@PluginDescriptor(
    name = "BA Automator",
    description = "Automates Barbarian Assault minigame roles",
    tags = {"barbarian", "assault", "minigame", "microbot", "ba"},
    enabledByDefault = false
)
@Slf4j
public class BaPlugin extends Plugin {

    @Inject
    private Client client;

    @Inject
    private BaConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private BaOverlay overlay;

    @Inject
    private BaScript script;

    @Getter
    private BaRole currentRole;

    @Getter
    private int currentWave = 0;

    @Getter
    private String currentCall = "";

    @Getter
    private boolean inGame = false;

    @Provides
    BaConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(BaConfig.class);
    }

    @Override
    protected void startUp() throws Exception {
        log.info("BA Automator started");
        overlayManager.add(overlay);
        script.run(config, this);
    }

    @Override
    protected void shutDown() throws Exception {
        log.info("BA Automator stopped");
        overlayManager.remove(overlay);
        script.shutdown();
        resetState();
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event) {
        switch (event.getGroupId()) {
            case InterfaceID.BARBASSAULT_OVER_ATT:
                currentRole = BaRole.ATTACKER;
                inGame = true;
                log.info("Detected role: ATTACKER");
                break;
            case InterfaceID.BARBASSAULT_OVER_DEF:
                currentRole = BaRole.DEFENDER;
                inGame = true;
                log.info("Detected role: DEFENDER");
                break;
            case InterfaceID.BARBASSAULT_OVER_COL:
                currentRole = BaRole.COLLECTOR;
                inGame = true;
                log.info("Detected role: COLLECTOR");
                break;
            case InterfaceID.BARBASSAULT_OVER_HEAL:
                currentRole = BaRole.HEALER;
                inGame = true;
                log.info("Detected role: HEALER");
                break;
            case InterfaceID.BARBASSAULT_WAVECOMPLETE:
                log.info("Wave {} complete", currentWave);
                break;
        }
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event) {
        if (event.getVarbitId() == VarbitID.BARBASSAULT_AREAEXIT_PENDING && event.getValue() == 0) {
            log.info("Exited BA arena");
            resetState();
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        String message = event.getMessage();

        if (message.startsWith("---- Wave:")) {
            String[] parts = message.split(" ");
            if (parts.length >= 3) {
                try {
                    currentWave = Integer.parseInt(parts[2]);
                    log.info("Wave {} started", currentWave);
                } catch (NumberFormatException e) {
                    log.warn("Failed to parse wave number from: {}", message);
                }
            }
        }
    }

    public void setCurrentCall(String call) {
        this.currentCall = call;
    }

    private void resetState() {
        currentRole = null;
        currentWave = 0;
        currentCall = "";
        inGame = false;
    }
}
