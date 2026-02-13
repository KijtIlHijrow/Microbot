package net.runelite.client.plugins.microbot.barbarianassault.roles;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.barbarianassault.BaConfig;
import net.runelite.client.plugins.microbot.barbarianassault.BaPlugin;
import net.runelite.client.plugins.microbot.barbarianassault.enums.BaCall;
import net.runelite.client.plugins.microbot.barbarianassault.enums.BaRole;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;

import javax.inject.Singleton;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
@Singleton
public class HealerScript {

    // Healing vial
    private static final String HEALING_VIAL = "Healing vial";

    // Object IDs
    private static final int NATURAL_SPRING = ObjectID.BARBASSAULT_NATURAL_SPRING;

    // Widget IDs for teammate health
    private static final int[] TEAMMATE_HP_WIDGETS = {
        InterfaceID.BarbassaultOverHeal.BARBASSAULT_HEALER_PLAYER1_HP,
        InterfaceID.BarbassaultOverHeal.BARBASSAULT_HEALER_PLAYER2_HP,
        InterfaceID.BarbassaultOverHeal.BARBASSAULT_HEALER_PLAYER3_HP,
        InterfaceID.BarbassaultOverHeal.BARBASSAULT_HEALER_PLAYER4_HP
    };

    public void execute(BaConfig config, BaPlugin plugin) {
        if (!plugin.isInGame()) return;

        String currentCall = plugin.getCurrentCall();
        BaCall call = BaCall.fromText(currentCall, BaRole.HEALER);

        // Heal teammates if they need it
        if (config.healerHealTeammates()) {
            healTeammatesIfNeeded(config.healerTeammateThreshold());
        }

        // Poison penance healers
        if (config.healerPoisonHealers() && call != null) {
            poisonHealers(call);
        }

        // Refill vials if needed
        refillVialsIfNeeded();
    }

    private void healTeammatesIfNeeded(int threshold) {
        if (Rs2Player.isMoving() || Rs2Player.isAnimating()) return;

        // Check teammate health widgets
        for (int widgetId : TEAMMATE_HP_WIDGETS) {
            Widget hpWidget = Microbot.getClient().getWidget(widgetId);
            if (hpWidget != null && hpWidget.getText() != null) {
                try {
                    String[] hpParts = hpWidget.getText().split(" / ");
                    if (hpParts.length == 2) {
                        int current = Integer.parseInt(hpParts[0]);
                        int max = Integer.parseInt(hpParts[1]);
                        int percentage = (current * 100) / max;

                        if (percentage < threshold) {
                            // Need to heal this teammate
                            if (Rs2Inventory.contains(HEALING_VIAL)) {
                                // Click on the teammate through their widget area
                                log.info("Teammate needs healing: {}%", percentage);
                                // Note: Actually targeting teammates requires clicking on their game model
                                // This is a simplified version
                            }
                        }
                    }
                } catch (NumberFormatException e) {
                    // Ignore parsing errors
                }
            }
        }
    }

    private void poisonHealers(BaCall call) {
        if (Rs2Player.isMoving() || Rs2Player.isAnimating()) return;

        String poisonFood = getPoisonedFoodForCall(call);
        if (poisonFood == null) return;

        // Check if we have the correct poisoned food
        if (!Rs2Inventory.contains(poisonFood)) {
            log.debug("No {} in inventory", poisonFood);
            return;
        }

        // Find penance healer to poison
        Rs2NpcModel healer = Microbot.getRs2NpcCache().query()
            .where(npc -> npc.getName() != null &&
                npc.getName().contains("Penance Healer") &&
                !npc.isDead())
            .nearest();

        if (healer != null) {
            // Use poisoned food on healer
            Rs2Inventory.use(poisonFood);
            sleep(100, 200);
            healer.click("Use");
            sleepUntil(() -> !Rs2Player.isMoving(), 2000);
        }
    }

    private void refillVialsIfNeeded() {
        // Check if we have empty vials that need refilling
        if (!Rs2Inventory.contains("Healing vial(1)") &&
            !Rs2Inventory.contains("Healing vial(2)") &&
            !Rs2Inventory.contains("Healing vial(3)")) {
            return; // Either full or no vials
        }

        // Find natural spring to refill
        Rs2TileObjectModel spring = Microbot.getRs2TileObjectCache().query()
            .withId(NATURAL_SPRING)
            .nearest();

        if (spring != null) {
            log.info("Refilling healing vials at spring");
            spring.click("Take-from");
            sleep(600, 800);
        }
    }

    private String getPoisonedFoodForCall(BaCall call) {
        switch (call) {
            case POISONED_TOFU:
                return "Poisoned tofu";
            case POISONED_WORMS:
                return "Poisoned worms";
            case POISONED_MEAT:
                return "Poisoned meat";
            default:
                return null;
        }
    }
}
