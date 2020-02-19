/**
 * Copyright (C) 2013 Ergo Sarapu <ergo.sarapu@lab.mobi>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.gtfs_transformer.updates;

import org.onebusaway.gtfs.model.Route;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.services.GtfsMutableRelationalDao;
import org.onebusaway.gtfs_transformer.services.GtfsTransformStrategy;
import org.onebusaway.gtfs_transformer.services.TransformContext;

import java.util.*;
import java.util.function.Predicate;

/**
 * A strategy to update Primary trips for our GTFS trips.
 *
 * Most trips have either A>A (round trip) or A>B trips.
 * For round trips, there is no reverse order match, so that is an exception.
 *
 * Most trips have an B>A trip matching the A>B. However, other trips will get more complicated.
 *
 * So this strategy will try to handle most cases.
 * The logic for all trips for a route is:
 *
 * 1. Test if we have a round trip
 * 1.1 If yes, then set it as primary and stop
 * 1.2 Find an outbound primary trip A>B
 * 1.3 Find an outbound primary trip starting with A
 * 1.4 Find the first alphabetically sorted (direction code) outbound trip as primary
 * 2. Find a matching inbound primary trip
 * 2.1 Find an exact reverse match to outbound primary trip
 * 2.2 Find an start and end reversed match to outbound primary trip
 * 2.3 Find an outbound primary trip ending with A
 * 2.4 Find the first alphabetically sorted (direction code) outbound trip as primary
 */
public class PrimaryTripFromDirectionCodeConvertStrategy implements GtfsTransformStrategy {

    private static final Direction DIRECTION_ROUND_TRIP = new Direction("A>A");
    private static final Direction DIRECTION_PRIMARY = new Direction("A>B");

    @Override
    public void run(TransformContext context, GtfsMutableRelationalDao dao) {
        Collection<Trip> allTrips = dao.getAllTrips();
        Map<Route, List<Trip>> routeTrips = new HashMap<>();

        for (Trip trip : allTrips) {
            Route route = trip.getRoute();
            List<Trip> trips = routeTrips.get(route);
            if (trips == null) {
                trips = new ArrayList<>();
            }

            trips.add(trip);
            routeTrips.put(route, trips);
        }

        for (Route route : routeTrips.keySet()) {
            List<Trip> trips = routeTrips.get(route);
            updatePrimaryTrips(dao, trips);
        }
    }

    private void updatePrimaryTrips(GtfsMutableRelationalDao dao, List<Trip> trips) {
        HashMap<Direction, List<Trip>> directionTripsMap = createDirectionTripsMap(trips);
        Set<Direction> keySet = directionTripsMap.keySet();

        // Find round trip
        if (keySet.contains(DIRECTION_ROUND_TRIP)) {
            saveTripsAsPrimary(dao, directionTripsMap.get(DIRECTION_ROUND_TRIP));
            // Round trip found => stop here
            return;
        }

        Direction primaryOutbound = findOutboundPrimaryDirection(keySet);
        Direction primaryInbound = findInboundPrimaryDirection(keySet, primaryOutbound);

        // Set outbound trips as primary
        if (primaryOutbound != null) {
            List<Trip> outboundTrips = directionTripsMap.get(primaryOutbound);
            if (outboundTrips != null) {
                saveTripsAsPrimary(dao, outboundTrips);
            }
        }

        // Set inbound trips as primary
        if (primaryInbound != null) {
            List<Trip> inboundTrips = directionTripsMap.get(primaryInbound);
            if (inboundTrips != null) {
                saveTripsAsPrimary(dao, inboundTrips);
            }
        }
    }

    private Direction findOutboundPrimaryDirection(Set<Direction> directions) {
        Direction primaryOutbound = null;
        // Find A>B
        if (directions.contains(DIRECTION_PRIMARY)) {
            primaryOutbound = DIRECTION_PRIMARY;
        }

        // Starting with A
        if (primaryOutbound == null) {
            primaryOutbound = findDirection(directions, direction -> direction.startsWith("A"));
        }

        // First outbound
        if (primaryOutbound == null) {
            primaryOutbound = findDirection(directions, direction -> direction.getDirectionId() == Direction.DIRECTION_OUTBOUND);
        }
        return primaryOutbound;
    }

    private Direction findInboundPrimaryDirection(Set<Direction> directions, Direction primaryOutbound) {
        Direction primaryInbound = null;
        if (primaryOutbound != null) {
            // Find reverse of primaryOutbound
            primaryInbound = findDirection(directions, new ReversedPredicate(primaryOutbound));

            // Find matching start and end of revered primaryOutbound
            if (primaryInbound == null) {
                primaryInbound = findDirection(directions, new StartEndReversedPredicate(primaryOutbound));
            }
        }

        // Find first ending with A
        if (primaryInbound == null) {
            primaryInbound = findDirection(directions, direction -> direction.endsWith("A"));
        }

        // Find first inbound
        if (primaryInbound == null) {
            primaryInbound = findDirection(directions, direction -> direction.getDirectionId() == Direction.DIRECTION_INBOUND);
        }
        return primaryInbound;
    }

    private Direction findDirection(Set<Direction> directions, Predicate<Direction> predicate) {
        return directions.stream()
                .filter(predicate)
                .min(Direction::compareTo)
                .orElse(null);
    }

    private HashMap<Direction, List<Trip>> createDirectionTripsMap(List<Trip> trips) {
        HashMap<Direction, List<Trip>> directionTripsMap = new HashMap<>();

        // Set the default primary trips
        for (Trip trip : trips) {
            Direction holder = Direction.from(trip);

            List<Trip> existingTrips = directionTripsMap.get(holder);
            if (existingTrips == null) {
                existingTrips = new ArrayList<>();
            }
            existingTrips.add(trip);
            directionTripsMap.put(holder, existingTrips);
        }
        return directionTripsMap;
    }

    private void saveTripsAsPrimary(GtfsMutableRelationalDao dao, List<Trip> trips) {
        for (Trip trip : trips) {
            trip.setPrimaryTrip(1);
            dao.updateEntity(trip);
        }
    }

    /**
     * Match that Direction's directionCodes match in a reversed order.
     * A>B == B>A
     * A>B>C == C>B>A
     */
    private static class ReversedPredicate implements Predicate<Direction> {

        private Direction original;

        public ReversedPredicate(Direction original) {
            this.original = original;
        }

        @Override
        public boolean test(Direction direction) {
            return original.matchReversed(direction);
        }
    }

    /**
     * Match that Direction's directionCode's first and last elements match in a reversed order.
     * A>B == B>A
     * A>B == B>C>A
     * A>B>C == C>A
     * etc
     */
    private static class StartEndReversedPredicate implements Predicate<Direction> {

        private Direction original;

        public StartEndReversedPredicate(Direction original) {
            this.original = original;
        }

        @Override
        public boolean test(Direction direction) {
            return original.matchStartEndReversed(direction);
        }
    }
}
