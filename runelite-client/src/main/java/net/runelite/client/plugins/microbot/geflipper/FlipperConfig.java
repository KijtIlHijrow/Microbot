package net.runelite.client.plugins.microbot.geflipper;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(value = "Flipper Config")
public interface FlipperConfig extends Config {

    // ── Section: Session Limits ─────────────────────────────────────────

    @ConfigSection(name = "Session Limits", description = "Session duration settings", position = 1)
    String sessionLimitsSection = "sessionLimits";

    @ConfigItem(keyName = "enableSessionLimit", name = "Enable session limit", description = "Stop the script after a session duration", position = 0, section = sessionLimitsSection)
    default boolean enableSessionLimit() {
        return true;
    }

    @Range(min = 30, max = 480)
    @ConfigItem(keyName = "maxSessionMinutes", name = "Max session (min)", description = "Maximum session duration in minutes", position = 1, section = sessionLimitsSection)
    default int maxSessionMinutes() {
        return 150;
    }

    @Range(min = 0, max = 60)
    @ConfigItem(keyName = "sessionLimitVariance", name = "Session variance (min)", description = "Random variance added/subtracted from session duration", position = 2, section = sessionLimitsSection)
    default int sessionLimitVariance() {
        return 30;
    }

    // ── Section: Break Timing ───────────────────────────────────────────

    @ConfigSection(name = "Break Timing", description = "Micro and macro break settings", position = 2)
    String breakTimingSection = "breakTiming";

    @Range(min = 3, max = 30)
    @ConfigItem(keyName = "microBreakMinInterval", name = "Micro break min interval (min)", description = "Minimum minutes between micro breaks", position = 0, section = breakTimingSection)
    default int microBreakMinInterval() {
        return 5;
    }

    @Range(min = 5, max = 60)
    @ConfigItem(keyName = "microBreakMaxInterval", name = "Micro break max interval (min)", description = "Maximum minutes between micro breaks", position = 1, section = breakTimingSection)
    default int microBreakMaxInterval() {
        return 15;
    }

    @Range(min = 5, max = 120)
    @ConfigItem(keyName = "microBreakMinDuration", name = "Micro break min duration (sec)", description = "Minimum micro break duration in seconds", position = 2, section = breakTimingSection)
    default int microBreakMinDuration() {
        return 10;
    }

    @Range(min = 10, max = 300)
    @ConfigItem(keyName = "microBreakMaxDuration", name = "Micro break max duration (sec)", description = "Maximum micro break duration in seconds", position = 3, section = breakTimingSection)
    default int microBreakMaxDuration() {
        return 45;
    }

    @ConfigItem(keyName = "enableMacroBreaks", name = "Enable macro breaks", description = "Take longer breaks periodically", position = 4, section = breakTimingSection)
    default boolean enableMacroBreaks() {
        return true;
    }

    @Range(min = 30, max = 180)
    @ConfigItem(keyName = "macroBreakInterval", name = "Macro break interval (min)", description = "Minutes between macro breaks", position = 5, section = breakTimingSection)
    default int macroBreakInterval() {
        return 60;
    }

    @Range(min = 0, max = 30)
    @ConfigItem(keyName = "macroBreakVariance", name = "Macro break variance (min)", description = "Random variance on macro break interval", position = 6, section = breakTimingSection)
    default int macroBreakVariance() {
        return 15;
    }

    @Range(min = 60, max = 900)
    @ConfigItem(keyName = "macroBreakMinDuration", name = "Macro break min duration (sec)", description = "Minimum macro break duration in seconds", position = 7, section = breakTimingSection)
    default int macroBreakMinDuration() {
        return 120;
    }

    @Range(min = 120, max = 1800)
    @ConfigItem(keyName = "macroBreakMaxDuration", name = "Macro break max duration (sec)", description = "Maximum macro break duration in seconds", position = 8, section = breakTimingSection)
    default int macroBreakMaxDuration() {
        return 600;
    }

    // ── Section: Reaction Speed ─────────────────────────────────────────

    @ConfigSection(name = "Reaction Speed", description = "How fast the script reacts to events", position = 3)
    String reactionSpeedSection = "reactionSpeed";

    @Range(min = 300, max = 2000)
    @ConfigItem(keyName = "baseReactionMean", name = "Base reaction mean (ms)", description = "Mean reaction time in milliseconds", position = 0, section = reactionSpeedSection)
    default int baseReactionMean() {
        return 850;
    }

    @Range(min = 50, max = 500)
    @ConfigItem(keyName = "baseReactionStddev", name = "Reaction std dev (ms)", description = "Standard deviation for reaction time", position = 1, section = reactionSpeedSection)
    default int baseReactionStddev() {
        return 175;
    }

    @ConfigItem(keyName = "sequentialSpeedup", name = "Sequential speedup", description = "React faster when doing consecutive actions", position = 2, section = reactionSpeedSection)
    default boolean sequentialSpeedup() {
        return true;
    }

    @ConfigItem(keyName = "wakeUpDelay", name = "Wake-up delay", description = "React slower after idle periods or breaks", position = 3, section = reactionSpeedSection)
    default boolean wakeUpDelay() {
        return true;
    }

    @ConfigItem(keyName = "enableFatigueScaling", name = "Enable fatigue scaling", description = "Reactions get slower over time using MouseFatigue", position = 4, section = reactionSpeedSection)
    default boolean enableFatigueScaling() {
        return true;
    }

    // ── Section: Idle Behavior ──────────────────────────────────────────

    @ConfigSection(name = "Idle Behavior", description = "What to do when waiting", position = 4)
    String idleBehaviorSection = "idleBehavior";

    @ConfigItem(keyName = "enableMouseDrift", name = "Enable mouse drift", description = "Randomly move mouse while idle", position = 0, section = idleBehaviorSection)
    default boolean enableMouseDrift() {
        return true;
    }

    @Range(min = 3, max = 30)
    @ConfigItem(keyName = "mouseDriftMinSeconds", name = "Mouse drift min (sec)", description = "Minimum seconds between mouse drifts", position = 1, section = idleBehaviorSection)
    default int mouseDriftMinSeconds() {
        return 5;
    }

    @Range(min = 10, max = 60)
    @ConfigItem(keyName = "mouseDriftMaxSeconds", name = "Mouse drift max (sec)", description = "Maximum seconds between mouse drifts", position = 2, section = idleBehaviorSection)
    default int mouseDriftMaxSeconds() {
        return 20;
    }

    @ConfigItem(keyName = "enableMouseOffScreen", name = "Enable mouse off-screen", description = "Move mouse off-screen after prolonged idle", position = 3, section = idleBehaviorSection)
    default boolean enableMouseOffScreen() {
        return true;
    }

    @Range(min = 10, max = 120)
    @ConfigItem(keyName = "mouseOffScreenMinSeconds", name = "Off-screen min (sec)", description = "Minimum seconds before moving mouse off-screen", position = 4, section = idleBehaviorSection)
    default int mouseOffScreenMinSeconds() {
        return 15;
    }

    @Range(min = 20, max = 180)
    @ConfigItem(keyName = "mouseOffScreenMaxSeconds", name = "Off-screen max (sec)", description = "Maximum seconds before moving mouse off-screen", position = 5, section = idleBehaviorSection)
    default int mouseOffScreenMaxSeconds() {
        return 45;
    }

    @ConfigItem(keyName = "enableVariableIdleReset", name = "Variable idle reset", description = "Randomize the idle reset threshold each cycle", position = 6, section = idleBehaviorSection)
    default boolean enableVariableIdleReset() {
        return true;
    }

    @Range(min = 10, max = 100)
    @ConfigItem(keyName = "idleResetChance", name = "Idle reset chance (%)", description = "Chance to close/reopen GE on idle reset", position = 7, section = idleBehaviorSection)
    default int idleResetChance() {
        return 65;
    }

    // ── Section: Camera & Tabs ──────────────────────────────────────────

    @ConfigSection(name = "Camera & Tabs", description = "Camera fidget and tab checking", position = 5)
    String cameraTabsSection = "cameraTabs";

    @ConfigItem(keyName = "enableCameraFidget", name = "Enable camera fidget", description = "Occasionally rotate the camera randomly", position = 0, section = cameraTabsSection)
    default boolean enableCameraFidget() {
        return true;
    }

    @Range(min = 1, max = 10)
    @ConfigItem(keyName = "cameraFidgetMinMinutes", name = "Camera fidget min (min)", description = "Minimum minutes between camera fidgets", position = 1, section = cameraTabsSection)
    default int cameraFidgetMinMinutes() {
        return 1;
    }

    @Range(min = 2, max = 20)
    @ConfigItem(keyName = "cameraFidgetMaxMinutes", name = "Camera fidget max (min)", description = "Maximum minutes between camera fidgets", position = 2, section = cameraTabsSection)
    default int cameraFidgetMaxMinutes() {
        return 5;
    }

    @ConfigItem(keyName = "enableRandomTabChecks", name = "Enable tab checks", description = "Randomly open tabs while idle", position = 3, section = cameraTabsSection)
    default boolean enableRandomTabChecks() {
        return true;
    }

    @Range(min = 1, max = 25)
    @ConfigItem(keyName = "randomTabCheckChance", name = "Tab check chance (%)", description = "Chance per idle cycle to check a random tab", position = 4, section = cameraTabsSection)
    default int randomTabCheckChance() {
        return 5;
    }

    // ── Section: Position Jitter ────────────────────────────────────────

    @ConfigSection(name = "Position Jitter", description = "Occasionally walk a few tiles", position = 6)
    String positionJitterSection = "positionJitter";

    @ConfigItem(keyName = "enablePositionJitter", name = "Enable position jitter", description = "Occasionally walk 1-2 tiles and return", position = 0, section = positionJitterSection)
    default boolean enablePositionJitter() {
        return false;
    }

    @Range(min = 1, max = 20)
    @ConfigItem(keyName = "positionJitterChance", name = "Jitter chance (%)", description = "Chance per idle cycle to jitter position", position = 1, section = positionJitterSection)
    default int positionJitterChance() {
        return 3;
    }

    // ── Section: Advanced ───────────────────────────────────────────────

    @ConfigSection(name = "Advanced", description = "Advanced anti-detection settings", position = 7)
    String advancedSection = "advanced";

    @ConfigItem(keyName = "enablePreClickHover", name = "Pre-click hover", description = "Hover before clicking highlighted widgets", position = 1, section = advancedSection)
    default boolean enablePreClickHover() {
        return true;
    }

    @ConfigItem(keyName = "enableMisclicks", name = "Enable misclicks", description = "Occasionally misclick before the real click", position = 2, section = advancedSection)
    default boolean enableMisclicks() {
        return false;
    }

    @Range(min = 1, max = 10)
    @ConfigItem(keyName = "misclickChance", name = "Misclick chance (%)", description = "Chance of a misclick before the real click", position = 3, section = advancedSection)
    default int misclickChance() {
        return 2;
    }

    @ConfigItem(keyName = "enableFittsLaw", name = "Enable Fitts's Law", description = "Scale reaction delay by distance to target", position = 4, section = advancedSection)
    default boolean enableFittsLaw() {
        return true;
    }

    // ── Section: Overlay ────────────────────────────────────────────────

    @ConfigSection(name = "Overlay", description = "Overlay position on screen", position = 8)
    String overlaySection = "overlay";

    @Range(min = 0, max = 800)
    @ConfigItem(keyName = "overlayX", name = "Overlay X", description = "Horizontal position of the overlay (0 = left edge)", position = 0, section = overlaySection)
    default int overlayX() {
        return 553;
    }

    @Range(min = 0, max = 600)
    @ConfigItem(keyName = "overlayY", name = "Overlay Y", description = "Vertical position of the overlay (0 = top edge)", position = 1, section = overlaySection)
    default int overlayY() {
        return 208;
    }
}
