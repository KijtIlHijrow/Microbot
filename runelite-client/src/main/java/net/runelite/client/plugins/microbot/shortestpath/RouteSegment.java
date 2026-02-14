package net.runelite.client.plugins.microbot.shortestpath;

import lombok.Getter;

/**
 * Represents one segment of a calculated route — either a walk or a transport.
 */
@Getter
public class RouteSegment {

    public enum SegmentType {
        WALK,
        TRANSPORT
    }

    private final SegmentType segmentType;
    private final TransportType transportType;
    private final String description;
    private final int steps;
    private final Transport transport;

    private RouteSegment(SegmentType segmentType, TransportType transportType, String description, int steps, Transport transport) {
        this.segmentType = segmentType;
        this.transportType = transportType;
        this.description = description;
        this.steps = steps;
        this.transport = transport;
    }

    public static RouteSegment walk(int steps) {
        return new RouteSegment(SegmentType.WALK, null, "Walk", steps, null);
    }

    public static RouteSegment transport(Transport transport) {
        String label = buildTransportLabel(transport);
        return new RouteSegment(SegmentType.TRANSPORT, transport.getType(), label, 1, transport);
    }

    public static RouteSegment unknownTransport() {
        return new RouteSegment(SegmentType.TRANSPORT, TransportType.TRANSPORT, "Transport", 1, null);
    }

    private static String buildTransportLabel(Transport transport) {
        if (transport == null) {
            return "Transport";
        }

        String name = transport.getName();
        String displayInfo = transport.getDisplayInfo();
        TransportType type = transport.getType();

        // For fairy rings, show the code
        if (type == TransportType.FAIRY_RING && displayInfo != null && !displayInfo.isEmpty()) {
            return "Fairy Ring (" + displayInfo + ")";
        }

        // For spirit trees, show destination
        if (type == TransportType.SPIRIT_TREE && displayInfo != null && !displayInfo.isEmpty()) {
            return "Spirit Tree (" + displayInfo + ")";
        }

        // For teleportation spells, use the name
        // For teleportation spells, use the name or displayInfo
        if (type == TransportType.TELEPORTATION_SPELL) {
            if (name != null && !name.isEmpty()) return name;
            if (displayInfo != null && !displayInfo.isEmpty()) return displayInfo;
            return "Teleport Spell";
        }

        // For teleportation items, use the name or displayInfo
        if (type == TransportType.TELEPORTATION_ITEM) {
            if (name != null && !name.isEmpty()) return name;
            if (displayInfo != null && !displayInfo.isEmpty()) return displayInfo;
            return "Teleport Item";
        }

        // For gnome gliders
        if (type == TransportType.GNOME_GLIDER && displayInfo != null && !displayInfo.isEmpty()) {
            return "Gnome Glider (" + displayInfo + ")";
        }

        // For POH
        if (type == TransportType.POH) {
            return name != null && !name.isEmpty() ? "POH: " + name : "POH Teleport";
        }

        // Generic: use name if available, then displayInfo, then type name
        if (name != null && !name.isEmpty()) {
            return name;
        }
        if (displayInfo != null && !displayInfo.isEmpty()) {
            return displayInfo;
        }

        // Fallback to type name with title case
        String typeName = type.name().replace('_', ' ');
        return typeName.charAt(0) + typeName.substring(1).toLowerCase();
    }

    public boolean isTransport() {
        return segmentType == SegmentType.TRANSPORT;
    }
}
