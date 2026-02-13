package net.runelite.client.plugins.microbot.barbarianassault;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.barbarianassault.enums.BaRole;

@ConfigGroup("baAutomator")
public interface BaConfig extends Config {

    // ==================== SECTIONS ====================

    @ConfigSection(
        name = "General",
        description = "General settings",
        position = 0
    )
    String generalSection = "general";

    @ConfigSection(
        name = "Attacker",
        description = "Attacker role settings",
        position = 1,
        closedByDefault = true
    )
    String attackerSection = "attacker";

    @ConfigSection(
        name = "Defender",
        description = "Defender role settings",
        position = 2,
        closedByDefault = true
    )
    String defenderSection = "defender";

    @ConfigSection(
        name = "Collector",
        description = "Collector role settings",
        position = 3,
        closedByDefault = true
    )
    String collectorSection = "collector";

    @ConfigSection(
        name = "Healer",
        description = "Healer role settings",
        position = 4,
        closedByDefault = true
    )
    String healerSection = "healer";

    // ==================== GENERAL ====================

    @ConfigItem(
        keyName = "selectedRole",
        name = "Role to Play",
        description = "Select which role to automate (or AUTO to detect)",
        position = 0,
        section = generalSection
    )
    default BaRole selectedRole() {
        return BaRole.AUTO;
    }

    @ConfigItem(
        keyName = "callOnHorn",
        name = "Call on Horn",
        description = "Automatically call on horn when call changes",
        position = 1,
        section = generalSection
    )
    default boolean callOnHorn() {
        return true;
    }

    @ConfigItem(
        keyName = "autoStartWave",
        name = "Auto Start Wave",
        description = "Automatically click to start the next wave",
        position = 2,
        section = generalSection
    )
    default boolean autoStartWave() {
        return false;
    }

    // ==================== ATTACKER ====================

    @ConfigItem(
        keyName = "attackerAutoStyle",
        name = "Auto Switch Style",
        description = "Automatically switch attack style based on call",
        position = 0,
        section = attackerSection
    )
    default boolean attackerAutoStyle() {
        return true;
    }

    @ConfigItem(
        keyName = "attackerPrioritizeRangers",
        name = "Prioritize Rangers",
        description = "Attack rangers before fighters",
        position = 1,
        section = attackerSection
    )
    default boolean attackerPrioritizeRangers() {
        return true;
    }

    // ==================== DEFENDER ====================

    @ConfigItem(
        keyName = "defenderAutoRepair",
        name = "Auto Repair Trap",
        description = "Automatically repair broken traps",
        position = 0,
        section = defenderSection
    )
    default boolean defenderAutoRepair() {
        return true;
    }

    @ConfigItem(
        keyName = "defenderDropFood",
        name = "Drop Food",
        description = "Drop food to lure runners",
        position = 1,
        section = defenderSection
    )
    default boolean defenderDropFood() {
        return true;
    }

    // ==================== COLLECTOR ====================

    @ConfigItem(
        keyName = "collectorConvertEggs",
        name = "Convert Wrong Eggs",
        description = "Convert wrong-colored eggs at the hopper",
        position = 0,
        section = collectorSection
    )
    default boolean collectorConvertEggs() {
        return true;
    }

    @ConfigItem(
        keyName = "collectorLoadCannon",
        name = "Load Cannon",
        description = "Load eggs into the cannon",
        position = 1,
        section = collectorSection
    )
    default boolean collectorLoadCannon() {
        return true;
    }

    // ==================== HEALER ====================

    @ConfigItem(
        keyName = "healerHealTeammates",
        name = "Heal Teammates",
        description = "Use vials to heal teammates when low HP",
        position = 0,
        section = healerSection
    )
    default boolean healerHealTeammates() {
        return true;
    }

    @ConfigItem(
        keyName = "healerTeammateThreshold",
        name = "Heal at HP %",
        description = "Heal teammates when below this HP percentage",
        position = 1,
        section = healerSection
    )
    default int healerTeammateThreshold() {
        return 50;
    }

    @ConfigItem(
        keyName = "healerPoisonHealers",
        name = "Poison Healers",
        description = "Use poisoned food on penance healers",
        position = 2,
        section = healerSection
    )
    default boolean healerPoisonHealers() {
        return true;
    }
}
