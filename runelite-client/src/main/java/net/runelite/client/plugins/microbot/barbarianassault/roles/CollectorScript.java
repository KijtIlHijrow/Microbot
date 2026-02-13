package net.runelite.client.plugins.microbot.barbarianassault.roles;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.barbarianassault.BaConfig;
import net.runelite.client.plugins.microbot.barbarianassault.BaPlugin;
import net.runelite.client.plugins.microbot.barbarianassault.enums.BaCall;
import net.runelite.client.plugins.microbot.barbarianassault.enums.BaRole;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;

import javax.inject.Singleton;
import java.util.Arrays;
import java.util.List;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
@Singleton
public class CollectorScript {

    // Egg names for ground items
    private static final String RED_EGG_NAME = "Red egg";
    private static final String GREEN_EGG_NAME = "Green egg";
    private static final String BLUE_EGG_NAME = "Blue egg";
    private static final String YELLOW_EGG_NAME = "Yellow egg";

    private static final List<String> ALL_EGGS = Arrays.asList(
        RED_EGG_NAME, GREEN_EGG_NAME, BLUE_EGG_NAME, YELLOW_EGG_NAME
    );

    // Object IDs
    private static final int EGG_HOPPER = ObjectID.BARBASSAULT_EGG_HOPPER;

    public void execute(BaConfig config, BaPlugin plugin) {
        if (!plugin.isInGame()) return;

        String currentCall = plugin.getCurrentCall();
        BaCall call = BaCall.fromText(currentCall, BaRole.COLLECTOR);

        // Pick up correct eggs from the ground
        pickupCorrectEggs(call);

        // Convert wrong eggs at hopper
        if (config.collectorConvertEggs()) {
            convertWrongEggs(call);
        }

        // Load eggs into cannon
        if (config.collectorLoadCannon()) {
            loadCannon();
        }
    }

    private void pickupCorrectEggs(BaCall call) {
        if (Rs2Player.isMoving() || Rs2Player.isAnimating()) return;
        if (Rs2Inventory.isFull()) return;

        String correctEgg = getEggNameForCall(call);
        if (correctEgg == null) return;

        // Try to pick up the correct egg color
        boolean picked = Rs2GroundItem.loot(correctEgg, 20);
        if (picked) {
            sleepUntil(() -> !Rs2Player.isMoving(), 2000);
        }
    }

    private void convertWrongEggs(BaCall call) {
        String correctEgg = getEggNameForCall(call);
        if (correctEgg == null) return;

        // Check for wrong-colored eggs in inventory
        for (String eggName : ALL_EGGS) {
            if (!eggName.equals(correctEgg) && Rs2Inventory.contains(eggName)) {
                // Convert at hopper
                Rs2TileObjectModel hopper = Microbot.getRs2TileObjectCache().query()
                    .withId(EGG_HOPPER)
                    .nearest();

                if (hopper != null) {
                    log.info("Converting {} at hopper", eggName);
                    Rs2Inventory.use(eggName);
                    sleep(100, 200);
                    hopper.click("Use");
                    sleepUntil(() -> !Rs2Inventory.contains(eggName), 2000);
                    return;
                }
            }
        }
    }

    private void loadCannon() {
        // Check if we have omega eggs or other eggs to load
        if (!Rs2Inventory.contains("Omega egg") &&
            !Rs2Inventory.contains("Red egg") &&
            !Rs2Inventory.contains("Green egg") &&
            !Rs2Inventory.contains("Blue egg")) {
            return;
        }

        // Find cannon and load
        Rs2TileObjectModel cannon = Microbot.getRs2TileObjectCache().query()
            .withName("Egg launcher")
            .nearest();

        if (cannon != null) {
            cannon.click("Load");
            sleep(600, 800);
        }
    }

    private String getEggNameForCall(BaCall call) {
        if (call == null) return null;
        switch (call) {
            case RED_EGG:
                return RED_EGG_NAME;
            case GREEN_EGG:
                return GREEN_EGG_NAME;
            case BLUE_EGG:
                return BLUE_EGG_NAME;
            default:
                return null;
        }
    }
}
