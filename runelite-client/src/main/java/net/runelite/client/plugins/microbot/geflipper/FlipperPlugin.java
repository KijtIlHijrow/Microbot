package net.runelite.client.plugins.microbot.geflipper;

import com.google.inject.Inject;
import com.google.inject.Provides;
import java.awt.AWTException;
import java.awt.Canvas;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import javax.swing.Timer;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(name="<html>[<font color=#8A2BE2>\u03a9</font>] Flippeith", description="Flipping copilot automation", tags={"flip", "ge", "grand", "exchange", "automation"}, authors={"Choken"}, version="1.2.4", minClientVersion="2.0.7", cardUrl="https://chsami.github.io/Microbot-Hub/FlipperPlugin/assets/card.jpg", iconUrl="https://chsami.github.io/Microbot-Hub/FlipperPlugin/assets/icon.jpg", enabledByDefault=false, isExternal=true)
public class FlipperPlugin
extends Plugin {
    public static final String version = "1.2.4";
    @Inject
    private Client client;
    @Inject
    private FlipperScript flipperScript;
    @Inject
    private FlipperConfig config;
    @Inject
    private FlipperOverlay flipperOverlay;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private MouseManager mouseManager;

    // Last known physical mouse position within the game area (tracked always)
    private volatile Point lastPhysicalPos = null;
    // True while the smooth pause-transition animation is still running
    private volatile boolean pauseAnimating = false;
    // Timer for the smooth pause-transition
    private Timer pauseAnimationTimer;

    /**
     * Intercepts physical mouse events:
     * - Clicks are blocked when running (not paused); bot clicks always pass through.
     * - Physical mouse position is always tracked (for smooth pause transition).
     * - When paused AND the transition animation is done: instant cursor sync.
     */
    private final net.runelite.client.input.MouseListener inputBlocker = new MouseAdapter() {
        private MouseEvent blockClickIfRunning(MouseEvent e) {
            if (!flipperScript.manuallyPaused
                    && !"Microbot".equals(String.valueOf(e.getSource()))) {
                e.consume();
            }
            return e;
        }

        private MouseEvent handleMotion(MouseEvent e) {
            if (!"Microbot".equals(String.valueOf(e.getSource()))) {
                Canvas canvas = Microbot.getClient().getCanvas();
                int x = e.getX(), y = e.getY();
                int margin = 5;
                boolean inBounds = x >= margin && y >= margin
                        && x < canvas.getWidth() - margin
                        && y < canvas.getHeight() - margin;

                if (inBounds) {
                    // Always track physical position (needed for pause transition)
                    lastPhysicalPos = new Point(x, y);

                    // Instant sync only when paused AND the initial animation finished
                    if (flipperScript.manuallyPaused && !pauseAnimating) {
                        Microbot.getMouse().setLastMove(new Point(x, y));
                    }
                }

                // Block ALL physical motion from reaching the game when running
                // (prevents tooltips, hover highlights, and cursor blinks)
                if (!flipperScript.manuallyPaused) {
                    e.consume();
                }
            }
            return e;
        }

        @Override
        public MouseEvent mousePressed(MouseEvent e) { return blockClickIfRunning(e); }

        @Override
        public MouseEvent mouseReleased(MouseEvent e) { return blockClickIfRunning(e); }

        @Override
        public MouseEvent mouseClicked(MouseEvent e) { return blockClickIfRunning(e); }

        @Override
        public MouseEvent mouseMoved(MouseEvent e) { return handleMotion(e); }

        @Override
        public MouseEvent mouseDragged(MouseEvent e) { return handleMotion(e); }

        @Override
        public MouseEvent mouseEntered(MouseEvent e) { return handleMotion(e); }

        @Override
        public MouseEvent mouseExited(MouseEvent e) { return handleMotion(e); }
    };

    // Global F6 hotkey for pause/resume
    private final KeyEventDispatcher pauseKeyDispatcher = e -> {
        if (e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_F6) {
            flipperScript.toggleManualPause();
            if (flipperScript.manuallyPaused) {
                // Paused — enable input and start smooth cursor transition
                Microbot.getClient().getCanvas().setFocusable(true);
                startPauseAnimation();
            } else {
                // Resumed — stop any animation, bot takes over
                stopPauseAnimation();

                // If resuming from a tabbed-out state, the cursor is already at the
                // user's physical mouse (from pause sync). Handle tab-in here so the
                // script thread doesn't call simulateTabIn() with the edge animation.
                if (flipperScript.waitingOffScreen) {
                    Point cursorPos = Microbot.getMouse().getLastMove();
                    Canvas c = Microbot.getClient().getCanvas();
                    int cw = c.getWidth(), ch = c.getHeight();
                    if (cursorPos.getX() >= 0 && cursorPos.getY() >= 0
                            && cursorPos.getX() < cw && cursorPos.getY() < ch) {
                        // Dispatch FOCUS_GAINED + MOUSE_ENTERED so the game knows
                        // the "window" is back, then clear waitingOffScreen before
                        // the script thread can call simulateTabIn().
                        c.dispatchEvent(new FocusEvent(
                                c, FocusEvent.FOCUS_GAINED, false));
                        MouseEvent enterEvt = new MouseEvent(
                                c, MouseEvent.MOUSE_ENTERED,
                                System.currentTimeMillis(), 0,
                                cursorPos.getX(), cursorPos.getY(), 0, false);
                        enterEvt.setSource("Microbot");
                        c.dispatchEvent(enterEvt);
                        flipperScript.waitingOffScreen = false;
                    }
                }

                Microbot.getClient().getCanvas().setFocusable(false);
            }
            return true;
        }
        return false;
    };

    /**
     * Starts a smooth animation from the bot's current overlay cursor position
     * toward the user's physical mouse. Uses exponential easing (~25% per frame
     * at 60fps). Once it arrives, switches to instant sync for subsequent moves.
     *
     * If the cursor is off-canvas (e.g. hidden at -50,-50 during tab-out),
     * we restore it to the actual exit edge position so the animation starts
     * from where the cursor was last visible.
     */
    private void startPauseAnimation() {
        Point target = lastPhysicalPos;
        if (target == null) {
            // No known physical position yet — skip animation, use instant sync
            pauseAnimating = false;
            return;
        }

        // Clear the trail so there's no visible line from the old position
        Microbot.getMouse().getPoints().clear();

        // If the cursor is off-canvas (e.g. hidden at -50,-50 during simulated
        // tab-out), restore it to the exit edge position where it was last visible.
        Point current = Microbot.getMouse().getLastMove();
        Canvas canvas = Microbot.getClient().getCanvas();
        int w = canvas.getWidth();
        int h = canvas.getHeight();
        int cx = current.getX(), cy = current.getY();
        if (cx < 0 || cy < 0 || cx >= w || cy >= h) {
            if (flipperScript.waitingOffScreen && flipperScript.tabOutExitEdge >= 0) {
                // Use the actual exit position, clamped to just inside the canvas edge
                int edgeX = Math.max(0, Math.min(w - 1, flipperScript.tabOutExitX));
                int edgeY = Math.max(0, Math.min(h - 1, flipperScript.tabOutExitY));
                Microbot.getMouse().setLastMove(new Point(edgeX, edgeY));
            } else {
                // Fallback: clamp to nearest canvas edge
                int clampedX = Math.max(0, Math.min(w - 1, cx));
                int clampedY = Math.max(0, Math.min(h - 1, cy));
                Microbot.getMouse().setLastMove(new Point(clampedX, clampedY));
            }
        }

        pauseAnimating = true;
        if (pauseAnimationTimer != null) pauseAnimationTimer.stop();

        pauseAnimationTimer = new Timer(16, evt -> {
            if (!flipperScript.manuallyPaused) {
                // User unpaused during animation
                stopPauseAnimation();
                return;
            }

            // Chase the latest physical position (user may still be moving)
            Point t = lastPhysicalPos;
            if (t == null) {
                stopPauseAnimation();
                return;
            }

            Point cur = Microbot.getMouse().getLastMove();
            int dx = t.getX() - cur.getX();
            int dy = t.getY() - cur.getY();
            double dist = Math.sqrt((double) dx * dx + (double) dy * dy);

            if (dist < 3) {
                // Arrived — snap and switch to instant sync
                Microbot.getMouse().setLastMove(t);
                stopPauseAnimation();
                return;
            }

            double factor = 0.25;
            int newX = cur.getX() + (int) Math.round(dx * factor);
            int newY = cur.getY() + (int) Math.round(dy * factor);
            if (newX == cur.getX() && dx != 0) newX += (dx > 0 ? 1 : -1);
            if (newY == cur.getY() && dy != 0) newY += (dy > 0 ? 1 : -1);

            Microbot.getMouse().setLastMove(new Point(newX, newY));
        });
        pauseAnimationTimer.start();
    }

    private void stopPauseAnimation() {
        pauseAnimating = false;
        if (pauseAnimationTimer != null) {
            pauseAnimationTimer.stop();
            pauseAnimationTimer = null;
        }
    }

    @Provides
    FlipperConfig provideConfig(ConfigManager configManager) {
        return (FlipperConfig)configManager.getConfig(FlipperConfig.class);
    }

    protected void startUp() throws AWTException {
        overlayManager.add(flipperOverlay);
        mouseManager.registerMouseListener(0, inputBlocker);
        Microbot.getClient().getCanvas().setFocusable(false);
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(pauseKeyDispatcher);
        this.flipperScript.run(config);
    }

    protected void shutDown() {
        stopPauseAnimation();
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .removeKeyEventDispatcher(pauseKeyDispatcher);
        mouseManager.unregisterMouseListener(inputBlocker);
        overlayManager.remove(flipperOverlay);
        this.flipperScript.manuallyPaused = false;
        this.flipperScript.state = State.GOING_TO_GE;
        this.flipperScript.shutdown();
        Microbot.getClient().getCanvas().setFocusable(true);
    }
}
