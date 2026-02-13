package net.runelite.client.plugins.microbot.geflipper;

import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;

@Singleton
public class FlipperScript extends Script {
    private static final Logger log = LoggerFactory.getLogger(FlipperScript.class);
    private final WorldArea grandExchangeArea = new WorldArea(3136, 3465, 61, 54, 0);

    State state = State.GOING_TO_GE;
    private FlipperConfig config;
    private Plugin flippingCopilot;
    private Object suggestionManager;
    private Object highlightController;

    // Action timing
    private long lastActionTime = 0L;
    private long actionCooldown = 1500L;
    private long idleSince = 0L;
    private long currentIdleResetThreshold = 45000L;
    private int actionsSinceIdle = 0;
    private long offerScreenStuckSince = 0L;
    private long currentStuckThreshold = 2000L;
    private long breakWaitThreshold = 7000L; // 6-8s of continuous wait before allowing breaks
    private long lastGeOpenAttempt = 0L;
    private int geOpenAttempts = 0;
    private int geExchangeNotFoundCount = 0; // consecutive right-click failures (clerk obstructed)
    private static final int GE_BACK_BUTTON_WIDGET_ID = 30474244;
    private static final int COLLECT_ALL_BUTTON = 30474246;

    // Break system
    long startTime = 0L;
    private long nextMicroBreakTime = 0L;
    private long nextMacroBreakTime = 0L;
    long breakEndTime = 0L;
    boolean isMacroBreak = false;
    private long sessionEndTime = 0L;

    // Dynamic reaction speed — warm-up then fatigue curve, reset on breaks
    private long phaseStartTime = 0L;

    // Idle behavior
    private long lastMouseDriftTime = 0L;
    private long nextCameraFidgetTime = 0L;
    private boolean waitingOffScreen = false;
    private int tabOutExitEdge = -1; // -1=not yet chosen, 0=bottom, 1=top, 2=left, 3=right
    private int tabOutExitX = 0;     // exact exit position for consistent re-entry
    private int tabOutExitY = 0;
    private long tabOutDelayMs = 0L; // "noticing" delay before tabbing out

    // Disconnect tracking
    private long disconnectedSince = 0L;

    // GE chatbox input edge detection (matches QoL plugin pattern)
    private boolean chatboxInputWasOpen = false;

    // Post-input promise chain: after e+Enter, block highlight clicks until the game and
    // copilot have processed the input. Two-phase: (1) wait for chatbox to close (Enter
    // accepted), then (2) wait for copilot highlights to mutate from the snapshot.
    private boolean awaitingInputProcessing = false;
    private long awaitingInputSince = 0L;
    private long inputChatboxClosedAt = 0L;
    private Set<Integer> preInputHighlightIds = null;

    // General post-click dedup: after clicking ANY copilot highlight or performing an abort,
    // snapshot the highlight IDs and block further clicks until copilot updates them.
    // When mutation is detected, enter a "reaction phase" — wait a small human reaction
    // delay before allowing the next click, replacing fixed cooldowns with event-driven timing.
    private Set<Integer> postClickHighlightSnapshot = null;
    private long postClickTime = 0L;
    private long highlightMutationDetectedAt = 0L;
    private long postClickReactionDelay = 0L;

    // Sub-screen → overview settling: prevent immediately clicking stale highlights
    private boolean wasOnSubScreen = false;
    private long subScreenCloseTime = 0L;
    private long currentSettleDelay = 750L;

    // Copilot health check
    private int initCheckCounter = 0;

    // Cached reflection Field objects (avoid getDeclaredField on every 250ms tick)
    private Field cachedSuggestionField;
    private Field cachedSuggestionTypeField;
    private Field cachedHighlightOverlaysField;
    private Field cachedWidgetField;
    // Flags to log reflection errors once, then stop retrying until copilot is re-initialized
    private boolean suggestionFieldFailed = false;
    private boolean suggestionTypeFieldFailed = false;
    private boolean highlightOverlaysFieldFailed = false;
    private boolean widgetFieldFailed = false;

    // Stats
    int actionCount = 0;

    String status = "Starting...";

    private int[] grandExchangeSlotIds = new int[]{
            30474247, 30474248, 30474249, 30474250,
            30474251, 30474252, 30474253, 30474254
    };

    private static final InterfaceTab[] RANDOM_TABS = {
            InterfaceTab.SKILLS, InterfaceTab.EQUIPMENT, InterfaceTab.FRIENDS,
            InterfaceTab.QUESTS, InterfaceTab.PRAYER, InterfaceTab.COMBAT
    };

    // ── Entry point ─────────────────────────────────────────────────────

    public boolean run(FlipperConfig config) {
        this.config = config;
        setupAntiban();
        this.startTime = System.currentTimeMillis();
        this.phaseStartTime = this.startTime;
        rollNextMicroBreak();
        rollNextMacroBreak();
        computeSessionEndTime();
        rollIdleResetThreshold();
        rollStuckThreshold();
        rollBreakWaitThreshold();
        rollNextCameraFidget();

        this.mainScheduledFuture = this.scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return;
                if (!Microbot.isLoggedIn()) {
                    if (disconnectedSince == 0L) {
                        disconnectedSince = System.currentTimeMillis();
                        log.info("Disconnected, waiting for reconnect...");
                    } else if (System.currentTimeMillis() - disconnectedSince > 120_000L) {
                        log.info("Disconnected for over 2 minutes, shutting down.");
                        shutdown();
                    }
                    return;
                }
                if (disconnectedSince > 0L) {
                    log.info("Reconnected after {}s.", (System.currentTimeMillis() - disconnectedSince) / 1000L);
                    disconnectedSince = 0L;
                    invalidateCopilotReferences();
                    this.state = State.GOING_TO_GE;
                }
                if (!this.initialize()) {
                    log.warn("FlipperScript initialization failed. Ensure Flipping Copilot is installed and enabled.");
                    return;
                }
                // Session limit — checked in ALL states so it triggers even during breaks or banking
                if (this.state != State.SESSION_ENDING && config.enableSessionLimit()
                        && sessionEndTime > 0 && System.currentTimeMillis() >= sessionEndTime) {
                    log.info("Session limit reached, ending session.");
                    this.state = State.SESSION_ENDING;
                }
                switch (this.state) {
                    case GOING_TO_GE:
                        handleGoingToGe();
                        break;
                    case GETTING_COINS:
                        handleGettingCoins();
                        break;
                    case MONITORING_COPILOT:
                        handleMonitoringCopilot();
                        break;
                    case ON_BREAK:
                        handleOnBreak();
                        break;
                    case SESSION_ENDING:
                        handleSessionEnding();
                        break;
                }
            } catch (Exception ex) {
                log.error("Error in FlipperScript: {} - ", ex.getMessage(), ex);
            }
        }, 0L, 250L, TimeUnit.MILLISECONDS);
        return true;
    }

    // ── State handlers ──────────────────────────────────────────────────

    private void handleGoingToGe() {
        this.status = "Walking to GE";
        boolean atGe = this.grandExchangeArea.contains(
                Microbot.getClient().getLocalPlayer().getWorldLocation());

        // If we have coins, skip banking entirely
        if (Rs2Inventory.contains(995)) {
            if (atGe) {
                this.state = State.MONITORING_COPILOT;
            } else if (!Rs2Player.isMoving()) {
                Rs2GrandExchange.walkToGrandExchange();
            }
            return;
        }

        // No coins — walk to GE first, then bank
        if (!atGe) {
            if (!Rs2Player.isMoving()) {
                Rs2GrandExchange.walkToGrandExchange();
            }
        }
        this.state = State.GETTING_COINS;
    }

    private void handleGettingCoins() {
        this.status = "Getting coins";
        if (Rs2Inventory.contains(995)) {
            this.state = State.MONITORING_COPILOT;
            return;
        }
        // Don't try to bank until we've actually arrived at the GE
        if (!this.grandExchangeArea.contains(
                Microbot.getClient().getLocalPlayer().getWorldLocation())) {
            if (!Rs2Player.isMoving()) {
                Rs2GrandExchange.walkToGrandExchange();
            }
            return;
        }
        if (Rs2Bank.openBank()) {
            FlipperScript.sleep(300, 600);
            Rs2Bank.depositAll();
            FlipperScript.sleepUntil(Rs2Inventory::isEmpty);
            FlipperScript.sleep(200, 500);
            Rs2Bank.withdrawAll(995);
            Rs2Inventory.waitForInventoryChanges(5000);
            FlipperScript.sleep(250, 450);
            Rs2Bank.closeBank();
            FlipperScript.sleepUntil(() -> !Rs2Bank.isOpen());
            this.state = State.MONITORING_COPILOT;
        }
    }

    private void handleMonitoringCopilot() {
        long now = System.currentTimeMillis();

        // Safety: if we've wandered away from the GE, go back
        if (!this.grandExchangeArea.contains(
                Microbot.getClient().getLocalPlayer().getWorldLocation())) {
            log.info("No longer at GE, returning.");
            this.state = State.GOING_TO_GE;
            return;
        }

        // If we're tabbed out, check copilot state BEFORE any action handlers run.
        // Without this guard, checkAndClickHighlightedWidgets() would move the mouse
        // from (-50,-50) to a widget position while still "alt-tabbed", causing a visible blink.
        if (this.waitingOffScreen) {
            Object earlyCheck = this.getSuggestion(this.suggestionManager);
            String earlyType = this.getSuggestionType(earlyCheck);
            boolean stillWaiting = Objects.equals(earlyType, "wait");

            if (stillWaiting) {
                // Still waiting — check breaks, but only after waiting long enough
                // to confirm we're truly idle (not just a brief gap between copilot actions)
                boolean waitedLongEnough = this.idleSince > 0 && (now - this.idleSince) >= breakWaitThreshold;
                if (waitedLongEnough && config.enableMacroBreaks() && nextMacroBreakTime > 0 && now >= nextMacroBreakTime) {
                    long duration = rollMacroBreakDuration();
                    this.breakEndTime = now + duration;
                    this.isMacroBreak = true;
                    this.state = State.ON_BREAK;
                    this.status = "Macro break (" + (duration / 1000L) + "s)";
                    log.info("Starting macro break for {}s.", duration / 1000L);
                    return;
                }
                if (nextMicroBreakTime > 0 && now >= nextMicroBreakTime) {
                    long duration = rollMicroBreakDuration();
                    this.breakEndTime = now + duration;
                    this.isMacroBreak = false;
                    this.state = State.ON_BREAK;
                    this.status = "Micro break (" + (duration / 1000L) + "s)";
                    log.info("Starting micro break for {}s.", duration / 1000L);
                    return;
                }
                this.status = "Waiting: tabbed out";
                return;
            }

            // Copilot changed from "wait" to something else — tab in FIRST
            simulateTabIn();
            this.idleSince = 0L;
        }

        // GE Auto Copilot: edge-detect chatbox input (e.g. "Enter price:") and press e+Enter.
        // Checked early so it fires on sub-screens where the chatbox input actually appears.
        if (this.checkAndTriggerCopilotInput()) {
            this.idleSince = 0L;
            this.actionsSinceIdle++;
            return;
        }

        // Detect GE sub-screens (buy setup, offer status, etc.) by checking back button visibility.
        // isOfferScreenOpen() only detects the offer status screen (child 23), NOT the buy setup screen.
        // The back button (child 4) is visible on ALL sub-screens, so we use it as the detector.
        boolean onGeSubScreen = Rs2Widget.isWidgetVisible(GE_BACK_BUTTON_WIDGET_ID);

        if (onGeSubScreen) {
            wasOnSubScreen = true;
            // Check if copilot is highlighting the back button — if so, click it
            boolean backButtonHighlighted = false;
            try {
                List<Widget> highlighted = this.getHighlightWidgets(this.highlightController);
                if (highlighted != null) {
                    backButtonHighlighted = highlighted.stream()
                            .filter(Objects::nonNull)
                            .anyMatch(w -> w.getId() == GE_BACK_BUTTON_WIDGET_ID);
                }
            } catch (Exception e) {
                log.debug("Error checking back button highlight: {}", e.getMessage());
            }

            if (backButtonHighlighted) {
                long cooldownLeft = (this.lastActionTime + this.actionCooldown) - now;
                if (cooldownLeft > 0) return; // still reacting to previous action

                log.info("Copilot highlighted back button, clicking it.");
                this.status = "Copilot: back to overview";
                this.offerScreenStuckSince = 0L;
                long delay = getReactionDelay(actionsSinceIdle > 1, actionsSinceIdle == 0);
                sleep((int) delay);
                // Pre-click hover on the back button before clicking
                Widget backBtn = Rs2Widget.getWidget(GE_BACK_BUTTON_WIDGET_ID);
                preClickHover(backBtn);
                Rs2GrandExchange.backToOverview();
                this.lastActionTime = System.currentTimeMillis();
                this.actionCooldown = getReactionDelay(true, false);
                this.actionsSinceIdle++;
                return;
            }

            // Check if copilot has other highlights on this sub-screen
            boolean hasHighlightsHere = this.getWidgetFromOverlay(this.highlightController, "") != null;
            if (hasHighlightsHere) {
                this.status = "Offer screen: following copilot";
                this.offerScreenStuckSince = 0L;
                // Fall through to checkAndClickHighlightedWidgets below
            } else {
                // No highlights on sub-screen — check if copilot wants collect (happens on overview)
                Object subSuggestion = this.getSuggestion(this.suggestionManager);
                String subType = this.getSuggestionType(subSuggestion);
                if ("collect".equals(subType)) {
                    log.info("Copilot suggests collect — returning to overview from sub-screen.");
                    this.status = "Returning for collect";
                    this.offerScreenStuckSince = 0L;
                    Widget collectBackBtn = Rs2Widget.getWidget(GE_BACK_BUTTON_WIDGET_ID);
                    preClickHover(collectBackBtn);
                    Rs2GrandExchange.backToOverview();
                    return;
                }

                // No highlights, not collecting — start/check stuck timer
                if (this.offerScreenStuckSince == 0L) {
                    this.offerScreenStuckSince = now;
                    this.status = "Offer screen: waiting for copilot";
                } else if (now - this.offerScreenStuckSince >= currentStuckThreshold) {
                    log.info("Stuck on GE sub-screen for {}ms with no highlights, pressing back.",
                            now - this.offerScreenStuckSince);
                    this.status = "Returning to overview";
                    this.offerScreenStuckSince = 0L;
                    Widget stuckBackBtn = Rs2Widget.getWidget(GE_BACK_BUTTON_WIDGET_ID);
                    preClickHover(stuckBackBtn);
                    Rs2GrandExchange.backToOverview();
                    rollStuckThreshold();
                    return;
                }
                return; // Wait for copilot or stuck timer
            }
        } else {
            this.offerScreenStuckSince = 0L;

            // Detect sub-screen → overview transition: enforce a settling delay so copilot
            // can update its suggestions before we click on any stale highlights
            if (wasOnSubScreen) {
                wasOnSubScreen = false;
                subScreenCloseTime = now;
                // Roll a settle delay: gaussian ~750ms, clamped 650-1100ms (min > 1 game tick)
                long settle = Rs2Random.randomGaussian(750.0, 150.0);
                currentSettleDelay = Math.max(650L, Math.min(1100L, settle));
                log.info("Returned to GE overview, settling {}ms before next action.", currentSettleDelay);
            }
        }

        // Safety: if the bank is open (e.g. accidental click), close it first
        if (Rs2Bank.isOpen()) {
            log.info("Bank is open unexpectedly, closing it to resume GE.");
            this.status = "Closing bank";
            Rs2Bank.closeBank();
            FlipperScript.sleepUntil(() -> !Rs2Bank.isOpen(), 3000);
            return;
        }

        // Misclick recovery: if we're in a dialogue (e.g. "Talk to" instead of "Exchange"),
        // dismiss it quickly — like a human who immediately realises they misclicked
        if (!Rs2GrandExchange.isOpen() && !onGeSubScreen && Rs2Dialogue.isInDialogue()) {
            log.info("[misclick] Dialogue open but GE is not — dismissing with Escape");
            this.status = "Misclick: closing dialogue";
            // Small "oh no" reaction delay before pressing Escape
            int reactionMs = (int) Rs2Random.randomGaussian(280.0, 60.0);
            reactionMs = Math.max(120, Math.min(500, reactionMs));
            FlipperScript.sleep(reactionMs);
            Rs2Keyboard.keyPress(java.awt.event.KeyEvent.VK_ESCAPE);
            FlipperScript.sleepUntil(() -> !Rs2Dialogue.isInDialogue(), 2000);
            // Reset the GE open cooldown so we can retry immediately
            this.lastGeOpenAttempt = 0L;
            return;
        }

        // Only try to open GE if we're not on any GE screen at all
        if (!Rs2GrandExchange.isOpen() && !onGeSubScreen) {
            openExchangeHumanlike();
            return;
        }

        // Show cooldown status if we're still waiting after an action
        long cooldownRemaining = (this.lastActionTime + this.actionCooldown) - now;
        if (cooldownRemaining > 0 && !"Starting...".equals(this.status)) {
            // Keep the last action status while in cooldown, just append reaction time
            if (!this.status.contains("(reacting)")) {
                this.status = this.status + " (reacting)";
            }
        } else if (cooldownRemaining <= 0) {
            this.status = "Monitoring";
        }

        if (this.checkAndAbortIfNeeded()) {
            this.idleSince = 0L;
            this.actionsSinceIdle++;
            return;
        }
        if (this.checkAndCollectCompletedOffers()) {
            this.idleSince = 0L;
            this.actionsSinceIdle++;
            return;
        }
        // After returning from a sub-screen (offer confirm, etc.) wait for copilot to update
        if (subScreenCloseTime > 0 && (now - subScreenCloseTime) < currentSettleDelay) {
            this.status = "Overview settling";
            return;
        }
        subScreenCloseTime = 0L;

        this.checkAndClickHighlightedWidgets();

        boolean hasHighlights = this.getWidgetFromOverlay(this.highlightController, "") != null;
        Object currentSuggestion = this.getSuggestion(this.suggestionManager);
        String suggestionType = this.getSuggestionType(currentSuggestion);
        boolean isWaiting = Objects.equals(suggestionType, "wait");
        boolean hasActiveSuggestion = suggestionType != null && !isWaiting;
        log.debug("[monitor] suggestion='{}', highlights={}, onSubScreen={}, awaitingInput={}",
                suggestionType, hasHighlights, onGeSubScreen, awaitingInputProcessing);

        if (hasHighlights || hasActiveSuggestion) {
            // Copilot wants us to do something (buy, sell, abort, etc.) — stay focused, no fidgets
            this.idleSince = 0L;
            if (!hasHighlights && hasActiveSuggestion) {
                this.status = "Copilot: " + suggestionType + " (awaiting highlight)";
            }
        } else if (isWaiting) {
            // Copilot says wait — simulate alt-tab after a short "noticing" delay
            // (Break checks + already-tabbed-out path handled by the guard at the top)
            if (this.idleSince == 0L) {
                this.idleSince = now;
                // Roll a delay before tabbing out — human wouldn't alt-tab instantly
                this.tabOutDelayMs = (long) Math.max(1500, Math.min(4000,
                        Rs2Random.randomGaussian(2750.0, 600.0)));
            }
            // Tab out after the noticing delay
            if (!this.waitingOffScreen && config.enableMouseOffScreen()
                    && (now - this.idleSince) >= this.tabOutDelayMs) {
                simulateTabOut();
            }
            // Check breaks — only after 6-8s of continuous waiting (avoid breaking mid-flip-sequence)
            long waitDuration = this.idleSince > 0 ? (now - this.idleSince) : 0L;
            boolean waitedLongEnough = waitDuration >= breakWaitThreshold;
            if (waitedLongEnough && config.enableMacroBreaks() && nextMacroBreakTime > 0 && now >= nextMacroBreakTime) {
                long duration = rollMacroBreakDuration();
                this.breakEndTime = now + duration;
                this.isMacroBreak = true;
                this.state = State.ON_BREAK;
                this.status = "Macro break (" + (duration / 1000L) + "s)";
                log.info("Starting macro break for {}s.", duration / 1000L);
                return;
            }
            if (waitedLongEnough && nextMicroBreakTime > 0 && now >= nextMicroBreakTime) {
                long duration = rollMicroBreakDuration();
                this.breakEndTime = now + duration;
                this.isMacroBreak = false;
                this.state = State.ON_BREAK;
                this.status = "Micro break (" + (duration / 1000L) + "s)";
                log.info("Starting micro break for {}s.", duration / 1000L);
                return;
            }
            this.status = this.waitingOffScreen ? "Waiting: tabbed out" : "Waiting";
        } else {
            // No copilot suggestion at all — full idle behaviors
            if (this.idleSince == 0L) {
                this.idleSince = now;
                this.status = "Idle";
            }

            long idleDuration = now - this.idleSince;

            // Idle mouse behavior (normal frequency)
            handleIdleMouse(now);

            // Camera fidget
            maybeFidgetCamera(now);

            // Tab checks
            maybeCheckRandomTab();

            // Position jitter
            maybePositionJitter();

            // Idle reset threshold reached — do something human-like
            if (idleDuration >= currentIdleResetThreshold) {
                log.info("Idle for {}ms (suggestion type: '{}'), performing idle action.",
                        currentIdleResetThreshold, suggestionType);

                // 20% chance: full GE close/reopen (a real person rarely does this)
                // 30% chance: camera nudge
                // 30% chance: random mouse move
                // 20% chance: just wait (do nothing)
                int roll = (int) (Math.random() * 100);
                if (roll < 20 && Rs2Random.dicePercentage(config.idleResetChance())) {
                    this.status = "Idle: reopening GE";
                    Rs2GrandExchange.closeExchange();
                    FlipperScript.sleepUntil(() -> !Rs2GrandExchange.isOpen());
                    FlipperScript.sleep(600, 1000);
                } else if (roll < 50) {
                    this.status = "Idle: camera nudge";
                    try {
                        int currentAngle = Rs2Camera.getAngle();
                        int offset = Rs2Random.randomGaussian(0, 15.0);
                        offset = Math.max(-40, Math.min(40, offset));
                        Rs2Camera.setAngle((currentAngle + offset + 360) % 360, 10);
                    } catch (Exception e) {
                        log.debug("Idle camera nudge failed: {}", e.getMessage());
                    }
                } else if (roll < 80) {
                    this.status = "Idle: mouse wander";
                    Rs2Antiban.moveMouseRandomly();
                } else {
                    this.status = "Idle: pausing";
                    FlipperScript.sleep(400, 800);
                }

                this.idleSince = 0L;
                this.actionsSinceIdle = 0;
                rollIdleResetThreshold();
            }
        }
    }

    private void handleOnBreak() {
        long now = System.currentTimeMillis();
        if (now >= this.breakEndTime) {
            // Break ended — tab back in if needed
            if (this.waitingOffScreen) {
                simulateTabIn();
            }
            if (isMacroBreak) {
                rollNextMacroBreak();
                // Full refresh — back to warm-up phase (like starting fresh)
                this.phaseStartTime = System.currentTimeMillis();
            } else {
                // Partial refresh — skip first 5 min of warm-up (small speed bump)
                this.phaseStartTime = System.currentTimeMillis() - 5 * 60_000L;
            }
            rollNextMicroBreak();
            rollBreakWaitThreshold();
            this.breakEndTime = 0L;
            this.isMacroBreak = false;
            this.actionsSinceIdle = 0;
            this.state = State.MONITORING_COPILOT;
            this.status = "Monitoring";
            log.info("Break ended, resuming.");
            return;
        }

        long remaining = (this.breakEndTime - now) / 1000L;
        String breakType = isMacroBreak ? "Macro" : "Micro";
        this.status = breakType + " break (" + remaining + "s left)";

        // Simulate being tabbed out during breaks (a real person wouldn't stare at the screen)
        if (!this.waitingOffScreen && config.enableMouseOffScreen()) {
            if (Rs2GrandExchange.isOpen()) {
                Rs2GrandExchange.closeExchange();
                FlipperScript.sleepUntil(() -> !Rs2GrandExchange.isOpen());
                FlipperScript.sleep(300, 600);
            }
            simulateTabOut();
        }
    }

    private void handleSessionEnding() {
        this.status = "Session ending...";
        log.info("Session ending, closing GE and logging out.");
        if (this.waitingOffScreen) {
            simulateTabIn();
        }
        if (Rs2GrandExchange.isOpen()) {
            Rs2GrandExchange.closeExchange();
            FlipperScript.sleepUntil(() -> !Rs2GrandExchange.isOpen());
            FlipperScript.sleep(500, 1000);
        }
        Rs2Player.logout();
        FlipperScript.sleep(2000, 3000);
        shutdown();
    }

    // ── Human-like GE open ─────────────────────────────────────────────

    private void openExchangeHumanlike() {
        if (Rs2GrandExchange.isOpen()) {
            this.geOpenAttempts = 0;
            return;
        }

        // Don't spam clerk clicks — wait at least 3s between attempts
        long now = System.currentTimeMillis();
        if (now - lastGeOpenAttempt < 3000) return;
        lastGeOpenAttempt = now;
        geOpenAttempts++;

        // Recovery: if we've been failing for a while, walk to GE center and retry
        if (geOpenAttempts >= 10) {
            log.warn("Failed to open GE after {} attempts, walking to GE center.", geOpenAttempts);
            this.status = "Recovery: repositioning at GE";
            WorldPoint geCenter = new WorldPoint(3165, 3487, 0);
            Rs2Walker.walkTo(geCenter, 1);
            FlipperScript.sleep(2000, 3000);
            // Aggressive camera rotation to get a fresh angle
            try {
                int newAngle = Rs2Random.between(0, 359);
                Rs2Camera.setAngle(newAngle, 20);
                FlipperScript.sleep(600, 1000);
            } catch (Exception e) {
                log.debug("Recovery camera rotation failed: {}", e.getMessage());
            }
            geOpenAttempts = 0;
            return;
        }

        // Find all GE clerks
        List<Rs2NpcModel> clerks = Rs2Npc.getNpcs("Grand Exchange Clerk", true)
                .collect(Collectors.toList());
        if (clerks.isEmpty()) {
            this.status = "Looking for GE clerk";
            return;
        }

        // Get all nearby NPCs that could visually obstruct a clerk
        List<Rs2NpcModel> allNpcs = Rs2Npc.getNpcs(npc -> {
            String name = npc.getName();
            return name != null && !name.equals("Grand Exchange Clerk");
        }).collect(Collectors.toList());

        // For each clerk, check if they have a valid on-screen clickbox
        // AND that no other NPC's clickbox significantly overlaps theirs.
        // Prefer the NEAREST unobstructed clerk (not the largest clickbox).
        WorldPoint playerPos = Rs2Player.getWorldLocation();
        Rs2NpcModel clearClerk = null;
        int clearClerkDist = Integer.MAX_VALUE;

        for (Rs2NpcModel clerk : clerks) {
            Rectangle clerkBox = Rs2UiHelper.getActorClickbox(clerk);
            // Skip clerks with no valid clickbox (default = full canvas = not renderable)
            if (clerkBox.getWidth() >= Microbot.getClient().getCanvasWidth()) continue;
            int clerkArea = (int) (clerkBox.getWidth() * clerkBox.getHeight());
            if (clerkArea < 200) continue; // Too small = heavily occluded by geometry

            // Check if any other NPC's clickbox center falls inside this clerk's clickbox
            boolean obstructed = false;
            for (Rs2NpcModel other : allNpcs) {
                Rectangle otherBox = Rs2UiHelper.getActorClickbox(other);
                if (otherBox.getWidth() >= Microbot.getClient().getCanvasWidth()) continue;

                // Check if the other NPC's clickbox center is inside the clerk's clickbox
                int otherCenterX = (int) otherBox.getCenterX();
                int otherCenterY = (int) otherBox.getCenterY();
                if (clerkBox.contains(otherCenterX, otherCenterY)) {
                    log.info("GE clerk at {} obstructed by {} (clickbox overlap).",
                            clerk.getWorldLocation(), other.getName());
                    obstructed = true;
                    break;
                }

                // Also check if clerk's center falls inside the other NPC's clickbox
                int clerkCenterX = (int) clerkBox.getCenterX();
                int clerkCenterY = (int) clerkBox.getCenterY();
                if (otherBox.contains(clerkCenterX, clerkCenterY)) {
                    log.info("GE clerk at {} behind {} (clerk center inside other clickbox).",
                            clerk.getWorldLocation(), other.getName());
                    obstructed = true;
                    break;
                }
            }

            if (!obstructed) {
                int dist = clerk.getWorldLocation().distanceTo(playerPos);
                if (dist < clearClerkDist) {
                    clearClerk = clerk;
                    clearClerkDist = dist;
                }
            }
        }

        if (clearClerk != null) {
            // Found a visible, unobstructed clerk — turn camera and right-click → Exchange
            // Right-clicking ensures we always select "Exchange" and never "Bank"
            this.status = "Opening GE";
            Rs2Camera.turnTo(clearClerk, 25);
            FlipperScript.sleep(300, 500);

            // Get fresh clickbox after camera turn and right-click it
            Rectangle clickbox = Rs2UiHelper.getActorClickbox(clearClerk);
            if (clickbox.getWidth() < Microbot.getClient().getCanvasWidth()) {
                net.runelite.api.Point clickPoint = Rs2UiHelper.getClickingPoint(clickbox, true);
                Microbot.naturalMouse.moveTo(clickPoint.getX(), clickPoint.getY());
                FlipperScript.sleep(50, 150);
                Microbot.getMouse().click(clickPoint, true); // right-click

                // Wait for context menu to appear
                if (!FlipperScript.sleepUntil(() -> Microbot.getClient().isMenuOpen(), 2000)) {
                    log.warn("Context menu did not open after right-clicking GE clerk");
                    return;
                }

                // Short delay before selecting — human reads the menu
                int menuReadDelay = Rs2Random.randomGaussian(250.0, 50.0);
                FlipperScript.sleep(Math.max(150, Math.min(400, menuReadDelay)));

                if (!clickMenuOption("Exchange")) {
                    log.warn("Could not find 'Exchange' in context menu — clerk obstructed by another NPC.");
                    // Close menu by clicking elsewhere
                    Microbot.getMouse().click(new net.runelite.api.Point(10, 10), false);
                    FlipperScript.sleep(200, 400);
                    geExchangeNotFoundCount++;

                    // Immediately rotate camera to get a clear angle on a clerk
                    this.status = "Clerk obstructed, rotating camera";
                    int curAngle = Rs2Camera.getAngle();
                    // Rotate more aggressively on repeated failures
                    int minRot = 30 + geExchangeNotFoundCount * 20;
                    int rot = Rs2Random.randomGaussian(0, 45.0);
                    rot = rot >= 0 ? Math.max(minRot, rot) : Math.min(-minRot, rot);
                    Rs2Camera.setAngle((curAngle + rot + 360) % 360, 20);
                    FlipperScript.sleep(600, 1000);
                    return;
                }
                geExchangeNotFoundCount = 0; // success — reset failure counter

                FlipperScript.sleepUntil(Rs2GrandExchange::isOpen, 5000);
            }
            return;
        }

        // No clear clerk found — rotate camera to try to find a clear angle
        Rs2NpcModel nearest = clerks.stream()
                .min(Comparator.comparingInt(c -> c.getWorldLocation()
                        .distanceTo(Rs2Player.getWorldLocation())))
                .orElse(null);
        if (nearest == null) return;

        this.status = "Adjusting camera for GE clerk";
        log.info("No unobstructed GE clerk found, rotating camera.");
        // Rotate to face the nearest clerk with some random offset to get a different angle
        int currentAngle = Rs2Camera.getAngle();
        int offset = Rs2Random.randomGaussian(0, 45.0);
        offset = Math.max(-90, Math.min(90, offset));
        if (Math.abs(offset) < 20) offset = offset >= 0 ? 30 : -30; // Ensure meaningful rotation
        Rs2Camera.setAngle((currentAngle + offset + 360) % 360, 20);
        FlipperScript.sleep(600, 1000);
        // Next loop iteration will retry with the new camera angle
    }

    // ── Antiban setup ───────────────────────────────────────────────────

    private void setupAntiban() {
        Rs2AntibanSettings.simulateFatigue = true;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.moveMouseOffScreen = false;   // We handle this ourselves
        Rs2AntibanSettings.moveMouseRandomly = false;    // We handle this ourselves
        Rs2AntibanSettings.takeMicroBreaks = false;      // We manage our own breaks
        Rs2AntibanSettings.actionCooldownChance = 0.0;   // We manage our own cooldowns
        Rs2AntibanSettings.usePlayStyle = false;
        Rs2AntibanSettings.behavioralVariability = false;
        Rs2AntibanSettings.nonLinearIntervals = false;
        Rs2AntibanSettings.profileSwitching = false;
        Rs2AntibanSettings.contextualVariability = false;
        Rs2AntibanSettings.simulateAttentionSpan = false;
        Rs2Antiban.setActivityIntensity(ActivityIntensity.LOW);
    }

    // ── Reaction delay system ───────────────────────────────────────────

    /**
     * Dynamic session speed multiplier — models warm-up and fatigue:
     *   0-10 min after start/break: 1.25x → 1.0x (warming up, getting into the groove)
     *  10-90 min: 1.0x → 1.35x (gradually fatiguing)
     *  90+ min: plateau at 1.35x
     */
    private double getSessionSpeedMultiplier() {
        if (phaseStartTime <= 0L) return 1.0;
        double minutesSincePhase = (System.currentTimeMillis() - phaseStartTime) / 60_000.0;

        if (minutesSincePhase < 10.0) {
            // Warm-up: starting sluggish, getting faster
            return 1.25 - 0.25 * (minutesSincePhase / 10.0);
        }
        // Fatigue: gradually slowing down
        double fatigueMinutes = minutesSincePhase - 10.0;
        return 1.0 + Math.min(0.35, 0.35 * (fatigueMinutes / 80.0));
    }

    private long getReactionDelay(boolean isSequential, boolean isAfterIdle) {
        // After idle/break: sluggish wake-up
        if (isAfterIdle && config.wakeUpDelay()) {
            long delay = Rs2Random.randomGaussian(1800.0, 300.0);
            return Math.max(1400L, Math.min(2500L, delay));
        }

        double sessionMultiplier = getSessionSpeedMultiplier();

        // Sequential: faster reactions (session speed at half strength — muscle memory)
        if (isSequential && config.sequentialSpeedup()) {
            long delay = Rs2Random.randomGaussian(550.0, 100.0);
            delay = Math.max(300L, Math.min(900L, delay));
            double seqMultiplier = 1.0 + (sessionMultiplier - 1.0) * 0.5;
            return (long) (delay * seqMultiplier);
        }

        // Normal: fatigue-adjusted + session speed curve
        double mean = config.baseReactionMean();
        double stddev = config.baseReactionStddev();

        if (config.enableFatigueScaling()) {
            int fatigueAdjusted = Rs2Antiban.mouseFatigue.calculateBaseTimeWithNoise(
                    (int) mean, (int) (mean * 2.5));
            mean = fatigueAdjusted;
        }

        mean *= sessionMultiplier;

        long delay = Rs2Random.randomGaussian(mean, stddev);
        return Math.max(500L, Math.min(2000L, delay));
    }

    private long applyFittsLaw(long baseDelay, Rectangle targetBounds) {
        if (!config.enableFittsLaw() || targetBounds == null) return baseDelay;
        try {
            Point mousePos = MouseInfo.getPointerInfo().getLocation();
            double targetCenterX = targetBounds.getCenterX();
            double targetCenterY = targetBounds.getCenterY();
            double distance = Math.sqrt(
                    Math.pow(mousePos.x - targetCenterX, 2) +
                    Math.pow(mousePos.y - targetCenterY, 2));
            double targetWidth = Math.max(targetBounds.getWidth(), 10);

            // Fitts's index of difficulty: ID = log2(2D/W)
            double id = Math.log(2.0 * distance / targetWidth + 1.0) / Math.log(2.0);
            // Scale: small distance = 0.8x, large distance = 1.5x
            double scale = 0.8 + (id / 10.0) * 0.7;
            scale = Math.max(0.8, Math.min(1.5, scale));

            return (long) (baseDelay * scale);
        } catch (Exception e) {
            return baseDelay;
        }
    }

    // ── Break timing ────────────────────────────────────────────────────

    private void rollNextMicroBreak() {
        long minMs = config.microBreakMinInterval() * 60_000L;
        long maxMs = config.microBreakMaxInterval() * 60_000L;
        double mean = (minMs + maxMs) / 2.0;
        double stddev = (maxMs - minMs) / 4.0;
        long delay = Rs2Random.randomGaussian(mean, stddev);
        delay = Math.max(minMs, Math.min(maxMs, delay));
        this.nextMicroBreakTime = System.currentTimeMillis() + delay;
        log.info("Next micro break in {}s.", delay / 1000L);
    }

    private long rollMicroBreakDuration() {
        long minMs = config.microBreakMinDuration() * 1000L;
        long maxMs = config.microBreakMaxDuration() * 1000L;
        double mean = (minMs + maxMs) / 2.0;
        double stddev = (maxMs - minMs) / 4.0;
        long duration = Rs2Random.randomGaussian(mean, stddev);
        return Math.max(minMs, Math.min(maxMs, duration));
    }

    private void rollNextMacroBreak() {
        if (!config.enableMacroBreaks()) {
            this.nextMacroBreakTime = 0L;
            return;
        }
        long baseMs = config.macroBreakInterval() * 60_000L;
        long varianceMs = config.macroBreakVariance() * 60_000L;
        long delay = Rs2Random.randomGaussian(baseMs, varianceMs / 2.0);
        delay = Math.max(baseMs - varianceMs, Math.min(baseMs + varianceMs, delay));
        this.nextMacroBreakTime = System.currentTimeMillis() + delay;
        log.info("Next macro break in {}s.", delay / 1000L);
    }

    private long rollMacroBreakDuration() {
        long minMs = config.macroBreakMinDuration() * 1000L;
        long maxMs = config.macroBreakMaxDuration() * 1000L;
        double mean = (minMs + maxMs) / 2.0;
        double stddev = (maxMs - minMs) / 4.0;
        long duration = Rs2Random.randomGaussian(mean, stddev);
        return Math.max(minMs, Math.min(maxMs, duration));
    }

    private void computeSessionEndTime() {
        if (!config.enableSessionLimit()) {
            this.sessionEndTime = 0L;
            return;
        }
        long baseMs = config.maxSessionMinutes() * 60_000L;
        long varianceMs = config.sessionLimitVariance() * 60_000L;
        long offset = Rs2Random.randomGaussian(0, varianceMs / 2.0);
        offset = Math.max(-varianceMs, Math.min(varianceMs, offset));
        this.sessionEndTime = System.currentTimeMillis() + baseMs + offset;
        log.info("Session will end in ~{}min.", (baseMs + offset) / 60_000L);
    }

    // ── Tab-out / tab-in simulation ────────────────────────────────────

    /**
     * Simulates alt-tabbing away from the game:
     * 1. Natural mouse movement toward a consistent canvas edge (chosen once per session)
     * 2. MOUSE_EXITED event (tells client the cursor left)
     * 3. FOCUS_LOST event (tells client the window lost focus)
     * 4. Hide overlay cursor far off-canvas
     */
    private void simulateTabOut() {
        java.awt.Canvas canvas = Microbot.getClient().getCanvas();
        int w = canvas.getWidth();
        int h = canvas.getHeight();

        // Pick exit edge ONCE per session — humans consistently alt-tab the same direction
        if (tabOutExitEdge < 0) {
            double roll = Math.random();
            if (roll < 0.50) {
                tabOutExitEdge = 0; // bottom (taskbar — most common)
            } else if (roll < 0.75) {
                tabOutExitEdge = 3; // right
            } else if (roll < 0.90) {
                tabOutExitEdge = 2; // left
            } else {
                tabOutExitEdge = 1; // top
            }
        }

        // Compute exit position with small variance from last exit (or fresh if first time)
        int exitX, exitY;
        switch (tabOutExitEdge) {
            case 0: // bottom
                exitX = tabOutExitX > 0
                        ? tabOutExitX + Rs2Random.randomGaussian(0.0, 15.0)
                        : (int) Rs2Random.randomGaussian(w / 2.0, w / 4.0);
                exitX = Math.max(50, Math.min(w - 50, exitX));
                exitY = h + 1;
                break;
            case 1: // top
                exitX = tabOutExitX > 0
                        ? tabOutExitX + Rs2Random.randomGaussian(0.0, 15.0)
                        : (int) Rs2Random.randomGaussian(w / 2.0, w / 4.0);
                exitX = Math.max(50, Math.min(w - 50, exitX));
                exitY = -1;
                break;
            case 2: // left
                exitX = -1;
                exitY = tabOutExitY > 0
                        ? tabOutExitY + Rs2Random.randomGaussian(0.0, 15.0)
                        : (int) Rs2Random.randomGaussian(h / 2.0, h / 4.0);
                exitY = Math.max(50, Math.min(h - 50, exitY));
                break;
            default: // right
                exitX = w + 1;
                exitY = tabOutExitY > 0
                        ? tabOutExitY + Rs2Random.randomGaussian(0.0, 15.0)
                        : (int) Rs2Random.randomGaussian(h / 2.0, h / 4.0);
                exitY = Math.max(50, Math.min(h - 50, exitY));
                break;
        }

        // Store exit position for consistent re-entry
        this.tabOutExitX = exitX;
        this.tabOutExitY = exitY;

        // Naturally move cursor toward the edge (visible animation, like a human moving to taskbar)
        Microbot.naturalMouse.moveTo(exitX, exitY);

        // Dispatch MOUSE_EXITED — tells the OSRS client the cursor has left the canvas
        java.awt.event.MouseEvent exitEvent = new java.awt.event.MouseEvent(
                canvas, java.awt.event.MouseEvent.MOUSE_EXITED,
                System.currentTimeMillis(), 0, exitX, exitY, 0, false);
        exitEvent.setSource("Microbot");
        canvas.dispatchEvent(exitEvent);

        // Clear the mouse trail first to prevent a visible line from edge to (-50,-50)
        Microbot.getMouse().getPoints().clear();

        // Hide overlay cursor far off-canvas (30px crosshair fully invisible)
        Microbot.getMouse().move(new net.runelite.api.Point(-50, -50));

        // Small delay between mouse leaving and window losing focus (realistic)
        FlipperScript.sleep(100, 300);

        // Dispatch FOCUS_LOST — tells the OSRS client the window lost focus (alt-tabbed)
        // This triggers realistic side effects: FPS limiter engages, keyboard state resets, etc.
        java.awt.event.FocusEvent focusLost = new java.awt.event.FocusEvent(
                canvas, java.awt.event.FocusEvent.FOCUS_LOST, false);
        canvas.dispatchEvent(focusLost);

        this.waitingOffScreen = true;
        log.info("Simulated tab-out via {} edge at ({},{})",
                new String[]{"bottom", "top", "left", "right"}[tabOutExitEdge], exitX, exitY);
    }

    /**
     * Simulates alt-tabbing back into the game:
     * 1. FOCUS_GAINED event (window regains focus)
     * 2. MOUSE_ENTERED event at the same position we exited from (with tiny variance)
     * 3. Natural mouse movement from edge back into the canvas
     * 4. Small wake-up pause (human re-orienting)
     */
    private void simulateTabIn() {
        java.awt.Canvas canvas = Microbot.getClient().getCanvas();
        int w = canvas.getWidth();
        int h = canvas.getHeight();

        // Re-enter at the same position we exited from, with tiny jitter (±10px)
        int entryX, entryY;
        switch (tabOutExitEdge) {
            case 0: // bottom
                entryX = tabOutExitX + Rs2Random.randomGaussian(0.0, 10.0);
                entryX = Math.max(50, Math.min(w - 50, entryX));
                entryY = h - 2;
                break;
            case 1: // top
                entryX = tabOutExitX + Rs2Random.randomGaussian(0.0, 10.0);
                entryX = Math.max(50, Math.min(w - 50, entryX));
                entryY = 2;
                break;
            case 2: // left
                entryX = 2;
                entryY = tabOutExitY + Rs2Random.randomGaussian(0.0, 10.0);
                entryY = Math.max(50, Math.min(h - 50, entryY));
                break;
            default: // right
                entryX = w - 2;
                entryY = tabOutExitY + Rs2Random.randomGaussian(0.0, 10.0);
                entryY = Math.max(50, Math.min(h - 50, entryY));
                break;
        }

        // Dispatch FOCUS_GAINED — window regains focus (user clicked back into the game)
        java.awt.event.FocusEvent focusGained = new java.awt.event.FocusEvent(
                canvas, java.awt.event.FocusEvent.FOCUS_GAINED, false);
        canvas.dispatchEvent(focusGained);

        // Small delay between window focus and cursor entering (realistic)
        FlipperScript.sleep(50, 200);

        // Dispatch MOUSE_ENTERED — tells the OSRS client the cursor returned
        java.awt.event.MouseEvent enterEvent = new java.awt.event.MouseEvent(
                canvas, java.awt.event.MouseEvent.MOUSE_ENTERED,
                System.currentTimeMillis(), 0, entryX, entryY, 0, false);
        enterEvent.setSource("Microbot");
        canvas.dispatchEvent(enterEvent);

        // Clear trail to prevent a visible line from (-50,-50) to the entry edge
        Microbot.getMouse().getPoints().clear();

        // Set position at the edge entry point
        Microbot.getMouse().move(new net.runelite.api.Point(entryX, entryY));

        // Natural mouse from edge toward a general center area (like refocusing on the game)
        int targetX = (int) Rs2Random.randomGaussian(w / 2.0, w / 6.0);
        int targetY = (int) Rs2Random.randomGaussian(h / 2.0, h / 6.0);
        targetX = Math.max(100, Math.min(w - 100, targetX));
        targetY = Math.max(100, Math.min(h - 100, targetY));
        Microbot.naturalMouse.moveTo(targetX, targetY);

        // Small wake-up pause — human re-orienting after alt-tabbing back
        int wakeUp = Rs2Random.randomGaussian(800.0, 200.0);
        wakeUp = Math.max(400, Math.min(1500, wakeUp));
        FlipperScript.sleep(wakeUp, wakeUp + 100);

        this.waitingOffScreen = false;
        log.info("Simulated tab-in via {} edge at ({},{}), wake-up {}ms",
                new String[]{"bottom", "top", "left", "right"}[tabOutExitEdge], entryX, entryY, wakeUp);
    }

    // ── Idle behaviors ──────────────────────────────────────────────────

    private void rollStuckThreshold() {
        long threshold = Rs2Random.randomGaussian(1990.0, 500.0);
        this.currentStuckThreshold = Math.max(1000L, Math.min(2980L, threshold));
    }

    private void rollBreakWaitThreshold() {
        this.breakWaitThreshold = Rs2Random.between(6000, 8000);
    }

    private void rollIdleResetThreshold() {
        if (config.enableVariableIdleReset()) {
            // 30-60s range — no real human reopens the GE every few seconds
            long threshold = Rs2Random.randomGaussian(45000.0, 8000.0);
            this.currentIdleResetThreshold = Math.max(30000L, Math.min(60000L, threshold));
        } else {
            this.currentIdleResetThreshold = 45000L;
        }
    }

    private void handleIdleMouse(long now) {
        if (!config.enableMouseDrift()) return;

        long minMs = config.mouseDriftMinSeconds() * 1000L;
        long maxMs = config.mouseDriftMaxSeconds() * 1000L;

        if (now >= lastMouseDriftTime + minMs) {
            long driftInterval = Rs2Random.randomGaussian(
                    (minMs + maxMs) / 2.0, (maxMs - minMs) / 4.0);
            driftInterval = Math.max(minMs, Math.min(maxMs, driftInterval));

            if (now >= lastMouseDriftTime + driftInterval) {
                this.status = "Idle: mouse drift";
                Rs2Antiban.moveMouseRandomly();
                lastMouseDriftTime = now;
            }
        }
    }

    private void maybeFidgetCamera(long now) {
        if (!config.enableCameraFidget()) return;
        if (now < nextCameraFidgetTime) return;

        try {
            this.status = "Antiban: camera fidget";
            int currentAngle = Rs2Camera.getAngle();
            int offset = Rs2Random.randomGaussian(0, 30.0);
            offset = Math.max(-90, Math.min(90, offset));
            int newAngle = (currentAngle + offset + 360) % 360;
            Rs2Camera.setAngle(newAngle, 15);
            log.info("Camera fidget: rotated {}deg.", offset);
        } catch (Exception e) {
            log.debug("Camera fidget failed: {}", e.getMessage());
        }
        rollNextCameraFidget();
    }

    private void rollNextCameraFidget() {
        long minMs = config.cameraFidgetMinMinutes() * 60_000L;
        long maxMs = config.cameraFidgetMaxMinutes() * 60_000L;
        long delay = Rs2Random.randomGaussian((minMs + maxMs) / 2.0, (maxMs - minMs) / 4.0);
        delay = Math.max(minMs, Math.min(maxMs, delay));
        this.nextCameraFidgetTime = System.currentTimeMillis() + delay;
    }

    private void maybeCheckRandomTab() {
        if (!config.enableRandomTabChecks()) return;
        if (!Rs2Random.dicePercentage(config.randomTabCheckChance())) return;

        InterfaceTab tab = RANDOM_TABS[(int) (Math.random() * RANDOM_TABS.length)];
        this.status = "Antiban: checking " + tab.name().toLowerCase();
        log.info("Random tab check: switching to {}.", tab.name());
        Rs2Tab.switchTo(tab);
        FlipperScript.sleep(1000, 5000);
        Rs2Tab.switchTo(InterfaceTab.INVENTORY);
    }

    private void maybePositionJitter() {
        if (!config.enablePositionJitter()) return;
        if (!Rs2Random.dicePercentage(config.positionJitterChance())) return;

        try {
            WorldPoint current = Rs2Player.getWorldLocation();
            // Only jitter if we're actually at the GE
            if (!this.grandExchangeArea.contains(current)) return;

            int dx = Rs2Random.randomGaussian(0, 1.0);
            int dy = Rs2Random.randomGaussian(0, 1.0);
            dx = Math.max(-2, Math.min(2, dx));
            dy = Math.max(-2, Math.min(2, dy));
            if (dx == 0 && dy == 0) dx = 1;

            WorldPoint jitterTarget = new WorldPoint(
                    current.getX() + dx, current.getY() + dy, current.getPlane());

            // Ensure the jitter target stays within the GE area
            if (!this.grandExchangeArea.contains(jitterTarget)) {
                log.debug("Position jitter target outside GE area, skipping.");
                return;
            }

            this.status = "Antiban: position jitter";
            log.info("Position jitter: walking to ({}, {}).", jitterTarget.getX(), jitterTarget.getY());
            Rs2Walker.walkTo(jitterTarget, 0);
            FlipperScript.sleep(1200, 2400);

            // Walk back
            Rs2Walker.walkTo(current, 0);
            FlipperScript.sleep(1200, 2400);

            // Reopen GE if it closed (use our human-like method, not framework doInvoke)
            if (!Rs2GrandExchange.isOpen()) {
                openExchangeHumanlike();
            }
        } catch (Exception e) {
            log.debug("Position jitter failed: {}", e.getMessage());
        }
    }

    // ── Click behavior enhancements ─────────────────────────────────────

    private void preClickHover(Widget widget) {
        if (!config.enablePreClickHover() || widget == null) return;
        try {
            Rectangle bounds = widget.getBounds();
            if (bounds == null || !Rs2UiHelper.isRectangleWithinCanvas(bounds)) return;

            // Move to general area with slight overshoot
            int overshootX = Rs2Random.randomGaussian(0, 8.0);
            int overshootY = Rs2Random.randomGaussian(0, 8.0);
            int targetX = (int) bounds.getCenterX() + overshootX;
            int targetY = (int) bounds.getCenterY() + overshootY;

            Microbot.naturalMouse.moveTo(targetX, targetY);
            FlipperScript.sleep(50, 200);
        } catch (Exception e) {
            log.debug("Pre-click hover failed: {}", e.getMessage());
        }
    }

    private void maybeMisclick(Widget widget) {
        if (!config.enableMisclicks()) return;
        if (!Rs2Random.dicePercentage(config.misclickChance())) return;

        try {
            Rectangle bounds = widget.getBounds();
            if (bounds == null) return;

            // Click offset to miss the widget
            int offsetX = Rs2Random.randomGaussian(0, 15.0);
            int offsetY = Rs2Random.randomGaussian(0, 15.0);
            // Ensure the offset actually misses by adding extra if too small
            if (Math.abs(offsetX) < bounds.getWidth() / 2) {
                offsetX += (offsetX >= 0 ? 1 : -1) * (int)(bounds.getWidth() / 2 + 5);
            }

            int missX = (int) bounds.getCenterX() + offsetX;
            int missY = (int) bounds.getCenterY() + offsetY;
            Microbot.naturalMouse.moveTo(missX, missY);
            // Brief click at wrong spot
            Microbot.getMouse().click(missX, missY);
            FlipperScript.sleep(200, 500);
            log.info("Misclick simulated.");
        } catch (Exception e) {
            log.debug("Misclick failed: {}", e.getMessage());
        }
    }

    // ── Post-click dedup helpers ────────────────────────────────────────

    /**
     * Snapshots current copilot highlights after performing any click.
     * Subsequent calls to {@link #isPostClickGuardActive()} will block
     * until the highlights change (copilot acknowledged the click).
     */
    private void setPostClickGuard() {
        postClickHighlightSnapshot = getHighlightWidgets(highlightController).stream()
                .filter(Objects::nonNull)
                .map(Widget::getId)
                .collect(Collectors.toSet());
        postClickTime = System.currentTimeMillis();
        log.info("[dedup] snapshot set: {}", postClickHighlightSnapshot);
    }

    /**
     * Returns true if the post-click guard is active.
     * Two phases:
     *   1. Wait for highlights to mutate (copilot acknowledged the click) — or 3s timeout.
     *   2. Once mutation detected, wait a small human reaction delay before clearing.
     * This replaces large fixed cooldowns with event-driven timing.
     */
    private boolean isPostClickGuardActive() {
        if (postClickHighlightSnapshot == null) return false;

        long now = System.currentTimeMillis();

        // Phase 2: reaction delay after mutation was detected
        if (highlightMutationDetectedAt > 0L) {
            long reactionElapsed = now - highlightMutationDetectedAt;
            if (reactionElapsed >= postClickReactionDelay) {
                log.info("[dedup] guard cleared: reaction complete ({}ms reaction, {}ms total)",
                        reactionElapsed, now - postClickTime);
                clearPostClickGuard();
                return false;
            }
            log.debug("[dedup] reaction phase: {}ms / {}ms", reactionElapsed, postClickReactionDelay);
            return true;
        }

        // Phase 1: waiting for highlight mutation
        long elapsed = now - postClickTime;
        Set<Integer> currentIds = getHighlightWidgets(highlightController).stream()
                .filter(Objects::nonNull)
                .map(Widget::getId)
                .collect(Collectors.toSet());
        boolean changed = !currentIds.equals(postClickHighlightSnapshot);
        boolean timedOut = elapsed > 3000L;

        if (changed || timedOut) {
            // Mutation detected (or timeout) — enter reaction phase
            boolean isSequential = actionsSinceIdle > 1;
            boolean isAfterIdle = actionsSinceIdle == 0;
            double mean = isAfterIdle ? 380.0 : (isSequential ? 200.0 : 280.0);
            postClickReactionDelay = Math.max(100L, Math.min(550L,
                    (long) Rs2Random.randomGaussian(mean, 50.0)));
            highlightMutationDetectedAt = now;
            log.info("[dedup] {} after {}ms, highlights {} → {}, reaction={}ms",
                    changed ? "mutation detected" : "TIMEOUT",
                    elapsed, postClickHighlightSnapshot, currentIds, postClickReactionDelay);
            return true; // still active — reaction delay hasn't started counting yet
        }

        log.debug("[dedup] waiting for mutation ({}ms elapsed)", elapsed);
        return true;
    }

    private void clearPostClickGuard() {
        postClickHighlightSnapshot = null;
        postClickTime = 0L;
        highlightMutationDetectedAt = 0L;
        postClickReactionDelay = 0L;
    }

    // ── Core action handlers ────────────────────────────────────────────

    /**
     * Checks if any GE offers are in a completed or cancelled state (items to collect).
     */
    private boolean hasCollectableOffers() {
        try {
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            if (offers == null) {
                log.debug("[collect-check] offers array is null");
                return false;
            }
            List<String> slotStates = new ArrayList<>();
            boolean found = false;
            for (int i = 0; i < offers.length; i++) {
                GrandExchangeOffer offer = offers[i];
                if (offer == null) continue;
                GrandExchangeOfferState state = offer.getState();
                if (state != GrandExchangeOfferState.EMPTY) {
                    slotStates.add("slot" + i + "=" + state);
                }
                if (state == GrandExchangeOfferState.BOUGHT
                        || state == GrandExchangeOfferState.SOLD
                        || state == GrandExchangeOfferState.CANCELLED_BUY
                        || state == GrandExchangeOfferState.CANCELLED_SELL) {
                    found = true;
                }
            }
            if (!slotStates.isEmpty()) {
                log.debug("[collect-check] GE slots: {} → collectable={}", String.join(", ", slotStates), found);
            }
            return found;
        } catch (Exception e) {
            log.info("[collect-check] Error reading offers: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Self-collects completed/aborted offers when Flipping Copilot has no active highlights.
     * Acts as a fallback — if copilot is highlighting something, we defer to it.
     */
    private boolean checkAndCollectCompletedOffers() {
        long now = System.currentTimeMillis();
        long cooldownLeft = (this.lastActionTime + this.actionCooldown) - now;
        if (cooldownLeft > 0) {
            log.debug("[collect] skipped — cooldown {}ms remaining", cooldownLeft);
            return false;
        }

        // If we just clicked something (e.g. copilot-highlighted collect button), don't
        // double-fire the collect fallback — wait for that click to be processed first
        if (isPostClickGuardActive()) {
            log.debug("[collect] skipped — dedup guard active (recent click still processing)");
            return false;
        }

        boolean hasOffers = hasCollectableOffers();
        if (!hasOffers) return false;

        // Only act as fallback: if copilot has highlights, let it handle collect
        boolean copilotHasHighlights = this.getWidgetFromOverlay(this.highlightController, "") != null;
        if (copilotHasHighlights) {
            log.info("[collect] collectable offers detected, but copilot has highlights — deferring to copilot");
            return false;
        }

        // Try to find the collect-all button widget (465:6)
        Widget collectBtn = Rs2Widget.getWidget(COLLECT_ALL_BUTTON);

        log.info("[collect] collectable offers detected, no copilot highlight. collectBtn={}, GE open={}",
                collectBtn != null ? "found (id=" + collectBtn.getId() + ")" : "NULL",
                Rs2GrandExchange.isOpen());
        this.status = "Collecting offers";

        // Short reaction delay — the bot is already active and focused on the GE
        boolean isSequential = actionsSinceIdle > 1;
        boolean isAfterIdle = actionsSinceIdle == 0;
        double mean = isAfterIdle ? 380.0 : (isSequential ? 200.0 : 280.0);
        long delay = Math.max(100L, Math.min(500L,
                (long) Rs2Random.randomGaussian(mean, 50.0)));
        log.info("[collect] reaction delay={}ms, sequential={}, afterIdle={}", delay, isSequential, isAfterIdle);
        sleep((int) delay);

        if (collectBtn != null) {
            log.info("[collect] clicking collect-all button widget (human-like)");
            preClickHover(collectBtn);
            Rs2Widget.clickWidget(collectBtn);
        } else {
            log.info("[collect] collect button widget not found, using framework Rs2GrandExchange.collectAll()");
            Rs2GrandExchange.collectAll(false);
        }

        this.lastActionTime = System.currentTimeMillis();
        this.actionCount++;
        // Minimal cooldown — dedup guard handles timing for next action
        this.actionCooldown = 80L;
        this.actionsSinceIdle++;
        setPostClickGuard();
        log.info("[collect] done. dedup guard set for next action.");
        return true;
    }

    private boolean checkAndAbortIfNeeded() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - this.lastActionTime < this.actionCooldown) {
            return false; // still reacting to previous action — no abort was performed
        }
        if (this.flippingCopilot == null || this.highlightController == null || this.suggestionManager == null) {
            return false;
        }
        // Post-click dedup: block until copilot has processed the previous click
        if (isPostClickGuardActive()) {
            log.debug("[dedup] abort blocked — waiting for highlight mutation");
            return false;
        }
        try {
            Object currentSuggestion = this.getSuggestion(this.suggestionManager);
            if (currentSuggestion == null) return false;

            String suggestionType = this.getSuggestionType(currentSuggestion);
            if (!Objects.equals(suggestionType, "abort")) return false;

            log.info("Found suggestion type '{}'.", suggestionType);
            Widget abortWidget = this.getWidgetFromOverlay(this.highlightController, suggestionType);
            if (abortWidget != null) {
                this.status = "Aborting offer (right-click)";
                boolean isAfterIdle = actionsSinceIdle == 0;

                preClickHover(abortWidget);

                // Right-click on the widget like a human would
                Rectangle bounds = abortWidget.getBounds() != null
                        && Rs2UiHelper.isRectangleWithinCanvas(abortWidget.getBounds())
                        ? abortWidget.getBounds() : Rs2UiHelper.getDefaultRectangle();
                net.runelite.api.Point clickPoint = Rs2UiHelper.getClickingPoint(bounds, true);
                Microbot.getMouse().click(clickPoint, true); // right-click

                // Wait for context menu to open
                if (!FlipperScript.sleepUntil(() -> Microbot.getClient().isMenuOpen(), 2000)) {
                    log.warn("Context menu did not open after right-click on abort widget");
                    return false;
                }

                // Short delay — experienced flipper knows where "Abort offer" is
                int menuReadDelay = Rs2Random.randomGaussian(220.0, 40.0);
                FlipperScript.sleep(Math.max(150, Math.min(350, menuReadDelay)));

                // Find "Abort offer" in the open menu and click it
                if (!clickMenuOption("Abort offer")) {
                    log.warn("Could not find 'Abort offer' in context menu");
                    // Close menu by clicking elsewhere
                    Microbot.getMouse().click(new net.runelite.api.Point(10, 10), false);
                    FlipperScript.sleep(200, 400);
                    return false;
                }

                this.lastActionTime = System.currentTimeMillis();
                this.actionCount++;
                long delay = getReactionDelay(actionsSinceIdle > 1, isAfterIdle);
                this.actionCooldown = applyFittsLaw(delay, bounds);
                setPostClickGuard();
                return true;
            }
        } catch (Exception e) {
            log.error("Could not process suggestion: {} - ", e.getMessage(), e);
        }
        return false;
    }

    /**
     * Finds a menu option in the currently open context menu and clicks it.
     * Menu entries are stored bottom-to-top (index 0 = bottom of menu).
     * Each row is 15px tall, with a 22px header at the top.
     */
    private boolean clickMenuOption(String optionText) {
        if (!Microbot.getClient().isMenuOpen()) return false;

        net.runelite.api.Menu menu = Microbot.getClient().getMenu();
        net.runelite.api.MenuEntry[] entries = menu.getMenuEntries();

        // Find the index of the target option
        int targetIndex = -1;
        for (int i = 0; i < entries.length; i++) {
            if (entries[i].getOption() != null && entries[i].getOption().equals(optionText)) {
                targetIndex = i;
                break;
            }
        }
        if (targetIndex < 0) return false;

        // Menu entries are rendered bottom-to-top: index 0 at the bottom, last at the top
        // Menu layout: 22px header + (entryCount * 15px) rows
        // Row Y for entry at index i (from bottom) = menuY + header + (entryCount - 1 - i) * 15
        int menuX = menu.getMenuX();
        int menuY = menu.getMenuY();
        int menuWidth = menu.getMenuWidth();
        int headerHeight = 22;
        int rowHeight = 15;

        int rowFromTop = entries.length - 1 - targetIndex;
        int clickY = menuY + headerHeight + (rowFromTop * rowHeight) + rowHeight / 2;
        int clickX = menuX + (int) Rs2Random.randomGaussian(menuWidth / 2.0, menuWidth / 6.0);
        clickX = Math.max(menuX + 5, Math.min(menuX + menuWidth - 5, clickX));

        // Add tiny jitter to Y too
        clickY += Rs2Random.randomGaussian(0.0, 2.0);

        // Natural mouse to the menu entry, then click
        Microbot.naturalMouse.moveTo(clickX, clickY);
        FlipperScript.sleep(50, 120);
        Microbot.getMouse().click(new net.runelite.api.Point(clickX, clickY), false);

        log.info("Clicked '{}' in context menu at ({},{}), entry index {} of {}",
                optionText, clickX, clickY, targetIndex, entries.length);
        return true;
    }

    /**
     * Edge-detects the GE chatbox input opening and triggers the Copilot e+Enter hotkey.
     * Only fires once per transition (closed→open) and confirms we're on a GE screen.
     * The flag stays true while the chatbox is open, preventing re-triggers on subsequent
     * 250ms ticks. It resets naturally when the chatbox closes (Enter was accepted).
     */
    private boolean checkAndTriggerCopilotInput() {
        boolean chatboxInputOpen = Rs2Widget.isWidgetVisible(10616870);
        boolean onGeScreen = Rs2Widget.isWidgetVisible(GE_BACK_BUTTON_WIDGET_ID);

        if (chatboxInputOpen && !chatboxInputWasOpen && onGeScreen) {
            // If we're already awaiting input processing from a previous e+Enter,
            // don't fire again — the chatbox may have closed and reopened due to a
            // stale double-click that snuck through before the dedup guard activated.
            if (awaitingInputProcessing) {
                log.info("[e+enter] SUPPRESSED: chatbox edge detected but awaitingInputProcessing still active ({}ms)",
                        System.currentTimeMillis() - awaitingInputSince);
                chatboxInputWasOpen = true; // still track the edge so we don't re-fire next tick
                return false;
            }

            // Mark as open FIRST — prevents re-triggering on subsequent ticks
            // while the chatbox is still visible (250ms loop is faster than chatbox closing)
            chatboxInputWasOpen = true;

            // Snapshot current highlights BEFORE pressing keys.
            // checkAndClickHighlightedWidgets() will compare against this snapshot and block
            // until copilot's highlights actually mutate (like a mutation observer).
            List<Widget> currentHighlights = getHighlightWidgets(highlightController);
            preInputHighlightIds = currentHighlights.stream()
                    .filter(Objects::nonNull)
                    .map(Widget::getId)
                    .collect(Collectors.toSet());
            awaitingInputProcessing = true;
            awaitingInputSince = System.currentTimeMillis();

            this.status = "Typing copilot keybind";
            log.info("[e+enter] EDGE detected: chatbox opened on GE screen. snapshot highlight IDs={}",
                    preInputHighlightIds);

            Rs2Keyboard.keyPress(69); // 'e'

            int keyDelay = Rs2Random.randomGaussian(150.0, 40.0);
            keyDelay = Math.max(80, Math.min(300, keyDelay));
            log.info("[e+enter] pressed 'e', waiting {}ms before Enter", keyDelay);
            FlipperScript.sleep(keyDelay, keyDelay + 30);

            Rs2Keyboard.keyPress(10); // Enter
            log.info("[e+enter] pressed Enter. awaitingInputProcessing=true");

            this.lastActionTime = System.currentTimeMillis();
            this.actionCount++;
            // Minimal cooldown — promise chain + dedup guard handle timing
            this.actionCooldown = 80L;
            setPostClickGuard(); // also set dedup guard so highlight clicks are blocked
            return true;
        }

        // Track chatbox state transitions for edge detection
        if (chatboxInputOpen != chatboxInputWasOpen) {
            log.debug("[e+enter] chatbox transition: {} → {} (onGE={})",
                    chatboxInputWasOpen ? "open" : "closed",
                    chatboxInputOpen ? "open" : "closed",
                    onGeScreen);
        }
        chatboxInputWasOpen = chatboxInputOpen;
        return false;
    }

    private void checkAndClickHighlightedWidgets() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - this.lastActionTime < this.actionCooldown) return;
        if (this.flippingCopilot == null || this.highlightController == null) return;

        // Promise chain after e+Enter: wait for chatbox to close (= game accepted the input),
        // then apply a small human reaction delay before allowing the next click.
        // The chatbox closing IS the confirmation — we don't wait for highlight mutation
        // because copilot often re-highlights the same widget for the next step.
        if (awaitingInputProcessing) {
            boolean chatboxOpen = Rs2Widget.isWidgetVisible(10616870);
            long elapsed = currentTime - awaitingInputSince;
            boolean timedOut = elapsed > 5000L;

            // Chatbox still open → Enter hasn't been processed yet
            if (chatboxOpen && !timedOut) {
                log.debug("[promise] chatbox still open ({}ms elapsed)", elapsed);
                inputChatboxClosedAt = 0L;
                return;
            }

            // Record when chatbox first closed + roll reaction delay
            if (inputChatboxClosedAt == 0L) {
                inputChatboxClosedAt = currentTime;
                boolean isSequential = actionsSinceIdle > 1;
                double mean = isSequential ? 200.0 : 280.0;
                postClickReactionDelay = Math.max(100L, Math.min(450L,
                        (long) Rs2Random.randomGaussian(mean, 45.0)));
                log.info("[promise] chatbox closed after {}ms, reaction={}ms{}",
                        elapsed, postClickReactionDelay, timedOut ? " (TIMEOUT)" : "");
            }

            // Wait for reaction delay after chatbox closed
            long sinceClosed = currentTime - inputChatboxClosedAt;
            if (sinceClosed < postClickReactionDelay) {
                log.debug("[promise] reaction phase: {}ms / {}ms", sinceClosed, postClickReactionDelay);
                return;
            }

            // Resolved — clear promise state AND dedup guard (chatbox close = confirmation)
            log.info("[promise] RESOLVED: {}ms total ({}ms wait + {}ms reaction)",
                    elapsed, inputChatboxClosedAt - awaitingInputSince, sinceClosed);
            awaitingInputProcessing = false;
            preInputHighlightIds = null;
            inputChatboxClosedAt = 0L;
            postClickReactionDelay = 0L;
            clearPostClickGuard(); // chatbox close confirmed the action, no need for dedup timeout
        }

        // Post-click dedup: block until copilot has processed the previous click
        if (isPostClickGuardActive()) {
            log.debug("[dedup] highlight click blocked — waiting for highlight mutation");
            return;
        }

        try {
            Widget highlightedWidget = this.getWidgetFromOverlay(this.highlightController, "");
            boolean isHighlightedVisible = highlightedWidget != null
                    && Rs2Widget.isWidgetVisible(highlightedWidget.getId());

            if (isHighlightedVisible) {
                this.status = "Clicking copilot highlight";
                log.info("[highlight] clicking widget id={}, sequential={}, afterIdle={}",
                        highlightedWidget.getId(), actionsSinceIdle > 1, actionsSinceIdle == 0);

                preClickHover(highlightedWidget);
                maybeMisclick(highlightedWidget);

                Rs2Widget.clickWidget(highlightedWidget);

                this.lastActionTime = currentTime;
                this.actionCount++;
                // Minimal cooldown — the dedup guard's reaction phase handles human timing
                this.actionCooldown = 80L;
                this.actionsSinceIdle++;
                setPostClickGuard();
            }
        } catch (Exception e) {
            log.error("Could not process highlight widgets: {} - ", e.getMessage(), e);
        }
    }

    // ── Overlay helper getters ──────────────────────────────────────────

    long getSecondsUntilMicroBreak() {
        if (this.nextMicroBreakTime <= 0L) return -1L;
        long diff = (this.nextMicroBreakTime - System.currentTimeMillis()) / 1000L;
        return Math.max(0L, diff);
    }

    long getSecondsUntilMacroBreak() {
        if (this.nextMacroBreakTime <= 0L) return -1L;
        long diff = (this.nextMacroBreakTime - System.currentTimeMillis()) / 1000L;
        return Math.max(0L, diff);
    }

    long getSecondsUntilSessionEnd() {
        if (this.sessionEndTime <= 0L) return -1L;
        long diff = (this.sessionEndTime - System.currentTimeMillis()) / 1000L;
        return Math.max(0L, diff);
    }

    String getCurrentSuggestionType() {
        if (this.suggestionManager == null) return null;
        Object suggestion = this.getSuggestion(this.suggestionManager);
        return this.getSuggestionType(suggestion);
    }

    // ── Shutdown ────────────────────────────────────────────────────────

    public void shutdown() {
        invalidateCopilotReferences();
        this.lastActionTime = 0L;
        this.actionCooldown = 1500L;
        this.idleSince = 0L;
        this.currentIdleResetThreshold = 45000L;
        this.actionsSinceIdle = 0;
        this.offerScreenStuckSince = 0L;
        this.currentStuckThreshold = 2000L;
        this.breakWaitThreshold = 7000L;
        this.lastGeOpenAttempt = 0L;
        this.geOpenAttempts = 0;
        this.geExchangeNotFoundCount = 0;
        this.phaseStartTime = 0L;
        this.startTime = 0L;
        this.nextMicroBreakTime = 0L;
        this.nextMacroBreakTime = 0L;
        this.breakEndTime = 0L;
        this.isMacroBreak = false;
        this.sessionEndTime = 0L;
        this.lastMouseDriftTime = 0L;
        this.nextCameraFidgetTime = 0L;
        this.waitingOffScreen = false;
        this.tabOutExitEdge = -1;
        this.tabOutExitX = 0;
        this.tabOutExitY = 0;
        this.tabOutDelayMs = 0L;
        this.disconnectedSince = 0L;
        this.initCheckCounter = 0;
        this.chatboxInputWasOpen = false;
        this.awaitingInputProcessing = false;
        this.awaitingInputSince = 0L;
        this.inputChatboxClosedAt = 0L;
        this.preInputHighlightIds = null;
        this.postClickHighlightSnapshot = null;
        this.postClickTime = 0L;
        this.highlightMutationDetectedAt = 0L;
        this.postClickReactionDelay = 0L;
        this.wasOnSubScreen = false;
        this.subScreenCloseTime = 0L;
        this.currentSettleDelay = 750L;
        this.actionCount = 0;
        this.status = "Starting...";
        this.config = null;
        super.shutdown();
    }

    // ── Initialization / reflection helpers ─────────────────────────────

    private boolean initialize() {
        if (this.flippingCopilot != null && this.suggestionManager != null && this.highlightController != null) {
            // Periodically verify copilot plugin is still active (~every 5s = 20 ticks)
            if (++initCheckCounter >= 20) {
                initCheckCounter = 0;
                boolean stillActive = Microbot.getPluginManager().getPlugins().stream()
                        .anyMatch(p -> p == this.flippingCopilot);
                if (!stillActive) {
                    log.warn("Flipping Copilot plugin was disabled or reloaded, re-initializing.");
                    invalidateCopilotReferences();
                    // Fall through to re-initialize
                } else {
                    return true;
                }
            } else {
                return true;
            }
        }
        initCheckCounter = 0;
        Plugin _flippingCopilot = this.getFlippingCopilot();
        Object _suggestionManager = this.getSuggestionManager(_flippingCopilot);
        Object _highlightController = this.getHighlightController(_flippingCopilot);
        return _flippingCopilot != null && _suggestionManager != null && _highlightController != null;
    }

    private void invalidateCopilotReferences() {
        this.flippingCopilot = null;
        this.suggestionManager = null;
        this.highlightController = null;
        this.cachedSuggestionField = null;
        this.cachedSuggestionTypeField = null;
        this.cachedHighlightOverlaysField = null;
        this.cachedWidgetField = null;
        this.suggestionFieldFailed = false;
        this.suggestionTypeFieldFailed = false;
        this.highlightOverlaysFieldFailed = false;
        this.widgetFieldFailed = false;
    }

    private Plugin getFlippingCopilot() {
        if (this.flippingCopilot == null) {
            this.flippingCopilot = Microbot.getPluginManager().getPlugins().stream()
                    .filter(plugin -> plugin.getClass().getSimpleName().equalsIgnoreCase("FlippingCopilotPlugin"))
                    .findFirst().orElse(null);
        }
        return this.flippingCopilot;
    }

    private Object getHighlightController(Plugin flippingCopilot) {
        if (flippingCopilot == null) return null;
        if (this.highlightController == null) {
            try {
                Field highlightControllerField = flippingCopilot.getClass().getDeclaredField("highlightController");
                highlightControllerField.setAccessible(true);
                this.highlightController = highlightControllerField.get(flippingCopilot);
            } catch (NoSuchFieldException e) {
                log.error("Flipping Copilot plugin has no 'highlightController' field — version mismatch? Available fields: {}",
                        Arrays.stream(flippingCopilot.getClass().getDeclaredFields())
                                .map(Field::getName).collect(Collectors.joining(", ")));
            } catch (Exception e) {
                log.error("Could not access HighlightController: {} - ", e.getMessage(), e);
            }
        }
        return this.highlightController;
    }

    private Object getSuggestionManager(Plugin flippingCopilot) {
        if (flippingCopilot == null) return null;
        if (this.suggestionManager == null) {
            try {
                Field suggestionManagerField = flippingCopilot.getClass().getDeclaredField("suggestionManager");
                suggestionManagerField.setAccessible(true);
                this.suggestionManager = suggestionManagerField.get(flippingCopilot);
            } catch (NoSuchFieldException e) {
                log.error("Flipping Copilot plugin has no 'suggestionManager' field — version mismatch? Available fields: {}",
                        Arrays.stream(flippingCopilot.getClass().getDeclaredFields())
                                .map(Field::getName).collect(Collectors.joining(", ")));
            } catch (Exception e) {
                log.error("Could not access SuggestionManager: {} - ", e.getMessage(), e);
            }
        }
        return this.suggestionManager;
    }

    private Object getSuggestion(Object suggestionManager) {
        if (suggestionManager == null || suggestionFieldFailed) return null;
        try {
            if (cachedSuggestionField == null) {
                cachedSuggestionField = suggestionManager.getClass().getDeclaredField("suggestion");
                cachedSuggestionField.setAccessible(true);
            }
            return cachedSuggestionField.get(suggestionManager);
        } catch (NoSuchFieldException e) {
            suggestionFieldFailed = true;
            log.error("SuggestionManager has no 'suggestion' field — Flipping Copilot version mismatch? Class: {}, fields: {}",
                    suggestionManager.getClass().getName(),
                    Arrays.stream(suggestionManager.getClass().getDeclaredFields())
                            .map(Field::getName).collect(Collectors.joining(", ")));
            return null;
        } catch (Exception e) {
            log.error("Could not access Suggestion: {} ", e.getMessage(), e);
            return null;
        }
    }

    private String getSuggestionType(Object suggestion) {
        if (suggestion == null || suggestionTypeFieldFailed) return null;
        try {
            if (cachedSuggestionTypeField == null) {
                cachedSuggestionTypeField = suggestion.getClass().getDeclaredField("type");
                cachedSuggestionTypeField.setAccessible(true);
            }
            return (String) cachedSuggestionTypeField.get(suggestion);
        } catch (NoSuchFieldException e) {
            suggestionTypeFieldFailed = true;
            log.error("Suggestion object has no 'type' field — Flipping Copilot version mismatch? Class: {}, fields: {}",
                    suggestion.getClass().getName(),
                    Arrays.stream(suggestion.getClass().getDeclaredFields())
                            .map(Field::getName).collect(Collectors.joining(", ")));
            return null;
        } catch (Exception e) {
            log.error("Could not access suggestion type: {} - ", e.getMessage(), e);
            return null;
        }
    }

    private List<Object> getHighlightOverlays(Object highlightController) {
        if (highlightController == null || highlightOverlaysFieldFailed) return null;
        try {
            if (cachedHighlightOverlaysField == null) {
                cachedHighlightOverlaysField = highlightController.getClass().getDeclaredField("highlightOverlays");
                cachedHighlightOverlaysField.setAccessible(true);
            }
            return (List) cachedHighlightOverlaysField.get(highlightController);
        } catch (NoSuchFieldException e) {
            highlightOverlaysFieldFailed = true;
            log.error("HighlightController has no 'highlightOverlays' field — Flipping Copilot version mismatch? Class: {}, fields: {}",
                    highlightController.getClass().getName(),
                    Arrays.stream(highlightController.getClass().getDeclaredFields())
                            .map(Field::getName).collect(Collectors.joining(", ")));
            return null;
        } catch (Exception e) {
            log.error("Could not access highlight overlays: {} - ", e.getMessage(), e);
            return null;
        }
    }

    private List<Widget> getHighlightWidgets(Object highlightController) {
        ArrayList<Widget> highlightWidgets = new ArrayList<>();
        if (highlightController == null) return highlightWidgets;
        List<Object> highlightOverlays = this.getHighlightOverlays(highlightController);
        if (highlightOverlays == null) return highlightWidgets;
        for (Object highlightOverlay : highlightOverlays) {
            try {
                if (widgetFieldFailed) return highlightWidgets;
                if (cachedWidgetField == null) {
                    cachedWidgetField = highlightOverlay.getClass().getDeclaredField("widget");
                    cachedWidgetField.setAccessible(true);
                }
                highlightWidgets.add((Widget) cachedWidgetField.get(highlightOverlay));
            } catch (NoSuchFieldException e) {
                widgetFieldFailed = true;
                log.error("Highlight overlay has no 'widget' field — Flipping Copilot version mismatch? Class: {}, fields: {}",
                        highlightOverlay.getClass().getName(),
                        Arrays.stream(highlightOverlay.getClass().getDeclaredFields())
                                .map(Field::getName).collect(Collectors.joining(", ")));
                return highlightWidgets;
            } catch (Exception e) {
                log.error("Could not get widget from overlay: {} - ", e.getMessage(), e);
                return highlightWidgets;
            }
        }
        return highlightWidgets;
    }

    private Widget getWidgetFromOverlay(Object highlightController, String suggestionType) {
        List<Object> highlightOverlays = this.getHighlightOverlays(highlightController);
        if (highlightOverlays == null || highlightOverlays.isEmpty()) return null;

        if (Objects.equals(suggestionType, "abort")) {
            return this.getHighlightWidgets(highlightController).stream()
                    .filter(Objects::nonNull)
                    .filter(widget -> Arrays.stream(this.grandExchangeSlotIds)
                            .anyMatch(id -> id == widget.getId()))
                    .findFirst().orElse(null);
        }
        return this.getHighlightWidgets(highlightController).stream()
                .filter(Objects::nonNull)
                .findFirst().orElse(null);
    }
}
