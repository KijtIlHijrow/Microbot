package net.runelite.client.plugins.microbot.shortestpath;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntilTrue;

@Slf4j
public class RouteConfirmation {

    public enum State {
        NONE,
        PENDING,
        CONFIRMED,
        WALK_ONLY,
        CANCELLED
    }

    @Getter
    private volatile State state = State.NONE;

    @Getter
    private volatile List<RouteSegment> segments;

    @Getter
    private volatile boolean hasTransports;

    @Getter @Setter
    private volatile List<MissingItemInfo> missingItems;

    private volatile RouteConfirmationOverlay overlay;

    public void analyze(List<WorldPoint> path) {
        List<RouteSegment> result = new ArrayList<>();
        boolean foundTransport = false;
        int walkCount = 0;

        PathfinderConfig pathfinderConfig = ShortestPathPlugin.getPathfinderConfig();

        for (int i = 0; i < path.size() - 1; i++) {
            WorldPoint current = path.get(i);
            WorldPoint next = path.get(i + 1);
            int dist = current.distanceTo(next);

            if (dist <= 1) {
                walkCount++;
            } else {
                // Flush walk segment
                if (walkCount > 0) {
                    result.add(RouteSegment.walk(walkCount));
                    walkCount = 0;
                }
                // Find matching transport
                Transport matched = matchTransport(pathfinderConfig, current, next);
                if (matched != null) {
                    result.add(RouteSegment.transport(matched));
                } else {
                    result.add(RouteSegment.unknownTransport());
                }
                foundTransport = true;
            }
        }

        // Flush trailing walk segment
        if (walkCount > 0) {
            result.add(RouteSegment.walk(walkCount));
        }

        this.segments = result;
        this.hasTransports = foundTransport;
    }

    public void show(RouteConfirmationOverlay overlay) {
        this.overlay = overlay;
        this.state = State.PENDING;
        overlay.setRouteAnalysis(this);
    }

    public State waitForDecision(long timeoutMs) {
        sleepUntilTrue(() -> {
            if (!Microbot.isLoggedIn()) {
                state = State.CANCELLED;
                return true;
            }
            return state != State.PENDING;
        }, 100, (int) timeoutMs);

        // Timeout → auto-confirm
        if (state == State.PENDING) {
            state = State.CONFIRMED;
        }

        hide();
        return state;
    }

    public void handleKey(int keyCode) {
        if (state != State.PENDING) {
            return;
        }
        switch (keyCode) {
            case KeyEvent.VK_ENTER:
                state = State.CONFIRMED;
                break;
            case KeyEvent.VK_W:
                state = State.WALK_ONLY;
                break;
            case KeyEvent.VK_ESCAPE:
                state = State.CANCELLED;
                break;
        }
    }

    public boolean isPending() {
        return state == State.PENDING;
    }

    public void reset() {
        state = State.NONE;
        segments = null;
        hasTransports = false;
        missingItems = null;
        hide();
    }

    private void hide() {
        if (overlay != null) {
            overlay.setRouteAnalysis(null);
        }
    }

    @Getter
    @AllArgsConstructor
    public static class MissingItemInfo {
        private final String name;
        private final int quantity;
    }

    private Transport matchTransport(PathfinderConfig pathfinderConfig, WorldPoint origin, WorldPoint destination) {
        if (pathfinderConfig == null) {
            return null;
        }

        // Check origin-keyed transports
        Map<WorldPoint, Set<Transport>> transports = pathfinderConfig.getTransports();
        Set<Transport> originTransports = transports.get(origin);
        if (originTransports != null) {
            for (Transport t : originTransports) {
                if (destination.equals(t.getDestination())) {
                    return t;
                }
            }
        }

        // Check null-origin (player-centered) teleports from allTransports
        Map<WorldPoint, Set<Transport>> allTransports = pathfinderConfig.getAllTransports();
        Set<Transport> nullOriginTransports = allTransports.get(null);
        if (nullOriginTransports != null) {
            for (Transport t : nullOriginTransports) {
                if (destination.equals(t.getDestination())) {
                    return t;
                }
            }
        }

        return null;
    }
}
