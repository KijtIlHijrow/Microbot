package net.runelite.client.plugins.microbot.barbarianassault.roles;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.VarPlayer;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.barbarianassault.BaConfig;
import net.runelite.client.plugins.microbot.barbarianassault.BaPlugin;
import net.runelite.client.plugins.microbot.barbarianassault.enums.BaCall;
import net.runelite.client.plugins.microbot.barbarianassault.enums.BaRole;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import javax.inject.Singleton;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
@Singleton
public class AttackerScript {

    // Attack style indices (for varbit comparison)
    private static final int STYLE_ACCURATE = 0;
    private static final int STYLE_AGGRESSIVE = 1;
    private static final int STYLE_CONTROLLED = 2;
    private static final int STYLE_DEFENSIVE = 3;

    public void execute(BaConfig config, BaPlugin plugin) {
        if (!plugin.isInGame()) return;

        String currentCall = plugin.getCurrentCall();
        BaCall call = BaCall.fromText(currentCall, BaRole.ATTACKER);

        // Switch attack style based on call
        if (config.attackerAutoStyle() && call != null) {
            switchAttackStyle(call);
        }

        // Attack penance creatures
        if (!Rs2Player.isInteracting()) {
            Rs2NpcModel target = findTarget(config.attackerPrioritizeRangers());
            if (target != null) {
                target.click("Attack");
                sleepUntil(() -> Rs2Player.isInteracting(), 2000);
            }
        }
    }

    private void switchAttackStyle(BaCall call) {
        int desiredStyle = getStyleIndexForCall(call);
        int currentStyle = Microbot.getClient().getVarpValue(VarPlayer.ATTACK_STYLE);

        if (currentStyle != desiredStyle) {
            log.info("Switching attack style to: {}", call.getDisplayName());
            WidgetInfo styleWidget = getWidgetForCall(call);
            if (styleWidget != null) {
                Rs2Combat.setAttackStyle(styleWidget);
                sleep(100, 200);
            }
        }
    }

    private int getStyleIndexForCall(BaCall call) {
        switch (call) {
            case ACCURATE:
                return STYLE_ACCURATE;
            case AGGRESSIVE:
                return STYLE_AGGRESSIVE;
            case CONTROLLED:
                return STYLE_CONTROLLED;
            case DEFENSIVE:
                return STYLE_DEFENSIVE;
            default:
                return STYLE_ACCURATE;
        }
    }

    private WidgetInfo getWidgetForCall(BaCall call) {
        switch (call) {
            case ACCURATE:
                return WidgetInfo.COMBAT_STYLE_ONE;
            case AGGRESSIVE:
                return WidgetInfo.COMBAT_STYLE_TWO;
            case CONTROLLED:
                return WidgetInfo.COMBAT_STYLE_THREE;
            case DEFENSIVE:
                return WidgetInfo.COMBAT_STYLE_FOUR;
            default:
                return WidgetInfo.COMBAT_STYLE_ONE;
        }
    }

    private Rs2NpcModel findTarget(boolean prioritizeRangers) {
        // First try to find the prioritized type
        if (prioritizeRangers) {
            Rs2NpcModel ranger = Microbot.getRs2NpcCache().query()
                .where(npc -> npc.getName() != null &&
                    npc.getName().contains("Penance Ranger") &&
                    !npc.isDead() &&
                    !npc.isInteracting())
                .nearest();

            if (ranger != null) return ranger;

            // Fall back to fighters
            return Microbot.getRs2NpcCache().query()
                .where(npc -> npc.getName() != null &&
                    npc.getName().contains("Penance Fighter") &&
                    !npc.isDead() &&
                    !npc.isInteracting())
                .nearest();
        } else {
            // Fighters first
            Rs2NpcModel fighter = Microbot.getRs2NpcCache().query()
                .where(npc -> npc.getName() != null &&
                    npc.getName().contains("Penance Fighter") &&
                    !npc.isDead() &&
                    !npc.isInteracting())
                .nearest();

            if (fighter != null) return fighter;

            return Microbot.getRs2NpcCache().query()
                .where(npc -> npc.getName() != null &&
                    npc.getName().contains("Penance Ranger") &&
                    !npc.isDead() &&
                    !npc.isInteracting())
                .nearest();
        }
    }
}
