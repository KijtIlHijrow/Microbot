package net.runelite.client.plugins.microbot.barbarianassault.roles;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
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
public class DefenderScript {

    // Trap object IDs
    private static final int TRAP = ObjectID.BARBASSAULT_TRAP;
    private static final int TRAP_1USE = ObjectID.BARBASSAULT_TRAP_1USE;
    private static final int TRAP_BROKEN = ObjectID.BARBASSAULT_TRAP_BROKEN;

    public void execute(BaConfig config, BaPlugin plugin) {
        if (!plugin.isInGame()) return;

        String currentCall = plugin.getCurrentCall();
        BaCall call = BaCall.fromText(currentCall, BaRole.DEFENDER);

        // Repair broken trap if enabled
        if (config.defenderAutoRepair()) {
            if (repairTrapIfNeeded()) {
                return; // Wait for repair to complete
            }
        }

        // Drop correct food to lure runners
        if (config.defenderDropFood() && call != null) {
            dropFoodForCall(call);
        }
    }

    private boolean repairTrapIfNeeded() {
        Rs2TileObjectModel brokenTrap = Microbot.getRs2TileObjectCache().query()
            .withId(TRAP_BROKEN)
            .nearest();

        if (brokenTrap != null) {
            log.info("Repairing broken trap");

            // Check if we have logs and hammer
            if (!Rs2Inventory.contains("Logs") || !Rs2Inventory.contains("Hammer")) {
                log.warn("Missing logs or hammer to repair trap");
                return false;
            }

            brokenTrap.click("Repair");
            sleepUntil(() -> Microbot.getRs2TileObjectCache().query()
                .withId(TRAP_BROKEN)
                .nearest() == null, 3000);
            return true;
        }
        return false;
    }

    private void dropFoodForCall(BaCall call) {
        String foodName = getFoodNameForCall(call);
        if (foodName == null) return;

        // Check if we have the food
        if (!Rs2Inventory.contains(foodName)) {
            log.debug("No {} in inventory to drop", foodName);
            return;
        }

        // Find nearby runners to lure
        Rs2NpcModel runner = Microbot.getRs2NpcCache().query()
            .where(npc -> npc.getName() != null &&
                npc.getName().contains("Penance Runner") &&
                !npc.isDead())
            .nearest();

        if (runner != null) {
            // Find trap location to drop food near it
            Rs2TileObjectModel trap = Microbot.getRs2TileObjectCache().query()
                .withId(TRAP)
                .nearest();

            if (trap == null) {
                trap = Microbot.getRs2TileObjectCache().query()
                    .withId(TRAP_1USE)
                    .nearest();
            }

            if (trap != null) {
                // Drop food near trap
                WorldPoint trapLocation = trap.getWorldLocation();
                WorldPoint playerLocation = Rs2Player.getWorldLocation();

                // Only drop if we're near the trap
                if (playerLocation.distanceTo(trapLocation) <= 5) {
                    Rs2Inventory.drop(foodName);
                    sleep(100, 200);
                }
            }
        }
    }

    private String getFoodNameForCall(BaCall call) {
        switch (call) {
            case TOFU:
                return "Tofu";
            case CRACKERS:
                return "Crackers";
            case WORMS:
                return "Worms";
            default:
                return null;
        }
    }
}
