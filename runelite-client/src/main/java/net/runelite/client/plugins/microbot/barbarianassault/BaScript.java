package net.runelite.client.plugins.microbot.barbarianassault;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.barbarianassault.enums.BaCall;
import net.runelite.client.plugins.microbot.barbarianassault.enums.BaRole;
import net.runelite.client.plugins.microbot.barbarianassault.roles.AttackerScript;
import net.runelite.client.plugins.microbot.barbarianassault.roles.CollectorScript;
import net.runelite.client.plugins.microbot.barbarianassault.roles.DefenderScript;
import net.runelite.client.plugins.microbot.barbarianassault.roles.HealerScript;

import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.TimeUnit;

@Slf4j
@Singleton
public class BaScript extends Script {

    private BaConfig config;
    private BaPlugin plugin;

    @Inject
    private AttackerScript attackerScript;
    @Inject
    private DefenderScript defenderScript;
    @Inject
    private CollectorScript collectorScript;
    @Inject
    private HealerScript healerScript;

    private String lastCall = "";

    public boolean run(BaConfig config, BaPlugin plugin) {
        this.config = config;
        this.plugin = plugin;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (!plugin.isInGame()) return;

                BaRole activeRole = getActiveRole();
                if (activeRole == null || activeRole == BaRole.AUTO) {
                    return;
                }

                // Read current call from horn widget
                updateCurrentCall(activeRole);

                // Execute role-specific logic
                switch (activeRole) {
                    case ATTACKER:
                        attackerScript.execute(config, plugin);
                        break;
                    case DEFENDER:
                        defenderScript.execute(config, plugin);
                        break;
                    case COLLECTOR:
                        collectorScript.execute(config, plugin);
                        break;
                    case HEALER:
                        healerScript.execute(config, plugin);
                        break;
                }

            } catch (Exception e) {
                log.error("Error in BA script: ", e);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);

        return true;
    }

    private BaRole getActiveRole() {
        BaRole configRole = config.selectedRole();
        if (configRole == BaRole.AUTO) {
            return plugin.getCurrentRole();
        }
        return configRole;
    }

    private void updateCurrentCall(BaRole role) {
        if (role == null || role.getHornTextWidget() == 0) return;

        Widget hornWidget = Microbot.getClient().getWidget(role.getHornTextWidget());
        if (hornWidget != null && hornWidget.getText() != null) {
            String callText = hornWidget.getText();
            if (!callText.equals(lastCall)) {
                lastCall = callText;
                plugin.setCurrentCall(callText);
                log.info("Call changed to: {}", callText);

                // Invoke horn action directly (no mouse movement) to call teammates instantly
                if (config.callOnHorn() && role.getHornSpriteWidget() != 0) {
                    Rs2Widget.clickWidgetFast(role.getHornSpriteWidget(), 1);
                    log.info("Clicked horn for call: {}", callText);
                }
            }
        }
    }

    @Override
    public void shutdown() {
        super.shutdown();
        lastCall = "";
    }
}
