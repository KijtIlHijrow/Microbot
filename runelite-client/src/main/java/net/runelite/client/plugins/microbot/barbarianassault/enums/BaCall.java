package net.runelite.client.plugins.microbot.barbarianassault.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.runelite.api.gameval.ItemID;

@AllArgsConstructor
@Getter
public enum BaCall {
    // Attacker calls (attack styles)
    ACCURATE("Accurate", BaRole.ATTACKER),
    AGGRESSIVE("Aggressive", BaRole.ATTACKER),
    CONTROLLED("Controlled", BaRole.ATTACKER),
    DEFENSIVE("Defensive", BaRole.ATTACKER),

    // Defender calls (food types)
    TOFU("Tofu", BaRole.DEFENDER),
    CRACKERS("Crackers", BaRole.DEFENDER),
    WORMS("Worms", BaRole.DEFENDER),

    // Collector calls (egg colors) - also used for converting
    RED_EGG("Red egg", BaRole.COLLECTOR),
    GREEN_EGG("Green egg", BaRole.COLLECTOR),
    BLUE_EGG("Blue egg", BaRole.COLLECTOR),

    // Healer calls (poisoned food types)
    POISONED_TOFU("Pois. Tofu", BaRole.HEALER),
    POISONED_WORMS("Pois. Worms", BaRole.HEALER),
    POISONED_MEAT("Pois. Meat", BaRole.HEALER);

    private final String displayName;
    private final BaRole role;

    public static BaCall fromText(String text, BaRole role) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        String normalized = text.toLowerCase().trim();

        switch (role) {
            case ATTACKER:
                if (normalized.contains("accurate")) return ACCURATE;
                if (normalized.contains("aggressive")) return AGGRESSIVE;
                if (normalized.contains("controlled")) return CONTROLLED;
                if (normalized.contains("defensive")) return DEFENSIVE;
                break;
            case DEFENDER:
                if (normalized.contains("tofu")) return TOFU;
                if (normalized.contains("cracker")) return CRACKERS;
                if (normalized.contains("worm")) return WORMS;
                break;
            case COLLECTOR:
                if (normalized.contains("red")) return RED_EGG;
                if (normalized.contains("green")) return GREEN_EGG;
                if (normalized.contains("blue")) return BLUE_EGG;
                break;
            case HEALER:
                if (normalized.contains("tofu")) return POISONED_TOFU;
                if (normalized.contains("worm")) return POISONED_WORMS;
                if (normalized.contains("meat")) return POISONED_MEAT;
                break;
        }
        return null;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
