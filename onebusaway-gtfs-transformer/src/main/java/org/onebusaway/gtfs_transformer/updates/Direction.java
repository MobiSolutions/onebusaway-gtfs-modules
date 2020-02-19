package org.onebusaway.gtfs_transformer.updates;

import org.onebusaway.gtfs.model.Trip;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

class Direction implements Comparable<Direction> {

    public static final int DIRECTION_OUTBOUND = 0;
    public static final int DIRECTION_INBOUND = 1;
    private static final String DIRECTION_CODE_SEPARATOR = ">";

    private String directionCode;
    private int directionId;

    public static Direction from(Trip trip) {
        return new Direction(trip.getDirectionCode());
    }

    public Direction(String directionCode) {
        this.directionCode = directionCode;
        this.directionId = calculateDirectionId(directionCode);
    }

    public int getDirectionId() {
        return directionId;
    }

    private String getDirectionCode() {
        return directionCode;
    }

    public boolean startsWith(String prefix) {
        return directionCode.startsWith(prefix);
    }

    public boolean endsWith(String suffix) {
        return directionCode.endsWith(suffix);
    }

    public boolean matchReversed(Direction another) {
        return Objects.equals(getDirectionCodeReversed(), another.getDirectionCode());
    }

    public boolean matchStartEndReversed(Direction another) {
        final StartEndStopsHolder stopsThis = getStartEndStops();
        final StartEndStopsHolder stopsAnother = another.getStartEndStops();
        return Objects.equals(stopsThis.start, stopsAnother.end) && Objects.equals(stopsThis.end, stopsAnother.start);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Direction that = (Direction) o;
        return Objects.equals(directionCode, that.directionCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(directionCode);
    }

    @Override
    public String toString() {
        return "Direction{" +
                "directionCode='" + directionCode + '\'' +
                ", directionId=" + directionId +
                '}';
    }

    @Override
    public int compareTo(Direction other) {
        List<String> thisStops = getStopsList();
        List<String> thatStops = other.getStopsList();

        int lenThis = thisStops.size();
        int lenThat = thatStops.size();
        int lim = Math.min(lenThis, lenThat);
        int i = 0;
        while (i < lim) {
            int result = thisStops.get(i).compareTo(thatStops.get(i));
            if (result == 0) {
                i++;
                continue;
            }
            return result;
        }
        return lenThis - lenThat;
    }

    private StartEndStopsHolder getStartEndStops() {
        final List<String> stops = getStopsList();
        return new StartEndStopsHolder(
                stops.get(0),
                stops.get(stops.size() - 1)
        );
    }

    private String getDirectionCodeReversed() {
        final List<String> stops = getStopsList();
        Collections.reverse(stops);
        return String.join(DIRECTION_CODE_SEPARATOR, stops);
    }

    private List<String> getStopsList() {
        return Arrays.asList(directionCode.split(DIRECTION_CODE_SEPARATOR));
    }

    private int calculateDirectionId(final String rawDirection) {
        final String cleaned = rawDirection.replaceAll("[0-9]", "");
        final char start = cleaned.charAt(0);
        final char end = cleaned.charAt(cleaned.length() - 1);
        if (start > end) {
            return DIRECTION_INBOUND;
        }
        return DIRECTION_OUTBOUND;
    }

    private static class StartEndStopsHolder {
        final String start;
        final String end;

        public StartEndStopsHolder(String start, String end) {
            this.start = start;
            this.end = end;
        }
    }
}
