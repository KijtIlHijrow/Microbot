package net.runelite.client.plugins.microbot.barbarianassault.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.runelite.api.gameval.InterfaceID;

@AllArgsConstructor
@Getter
public enum BaRole {
    AUTO("Auto Detect", 0, 0),
    ATTACKER("Attacker", InterfaceID.BarbassaultOverAtt.BARBASSAULT_ATTACKER_HORN_TEXT, InterfaceID.BarbassaultOverAtt.BARBASSAULT_ATTACKER_HORN),
    DEFENDER("Defender", InterfaceID.BarbassaultOverDef.BARBASSAULT_DEFENDER_HORN_TEXT, InterfaceID.BarbassaultOverDef.BARBASSAULT_DEFENDER_HORN),
    COLLECTOR("Collector", InterfaceID.BarbassaultOverCol.BARBASSAULT_COLLECTOR_HORN_TEXT, InterfaceID.BarbassaultOverCol.BARBASSAULT_COLLECTOR_HORN),
    HEALER("Healer", InterfaceID.BarbassaultOverHeal.BARBASSAULT_HEALER_HORN_TEXT, InterfaceID.BarbassaultOverHeal.BARBASSAULT_HEALER_HORN);

    private final String displayName;
    private final int hornTextWidget;
    private final int hornSpriteWidget;

    @Override
    public String toString() {
        return displayName;
    }
}
