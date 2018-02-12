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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class PrimaryTripFromDirectionCodeConvertStrategy implements GtfsTransformStrategy {

    private static final Collection<String> PRIMARY_TRIPS = new HashSet<String>() {{
        add("A>A");
        add("A>B");
        add("B>A");
    }};
    private static final String DIRECTION_CODE_SEPARATOR = ">";
    private static Logger _log = LoggerFactory.getLogger(PrimaryTripFromDirectionCodeConvertStrategy.class);

    @Override
    public void run(TransformContext context, GtfsMutableRelationalDao dao) {

        Collection<Trip> allTrips = dao.getAllTrips();
        Collection<Route> allRoutes = dao.getAllRoutes();

        for (Route route : allRoutes) {
            _log.error(String.valueOf(route));
        }

        Map<Route, List<Trip>> routeTrips = new HashMap<Route, List<Trip>>();
        for (Trip trip : allTrips) {
            Route route = trip.getRoute();
            List<Trip> trips = routeTrips.get(route);
            if (trips == null) {
                trips = new ArrayList<Trip>();
            }

            trips.add(trip);
            routeTrips.put(route, trips);
        }

        for (Route route : routeTrips.keySet()) {
            List<Trip> trips = routeTrips.get(route);
            setPrimaryTrips(dao, trips);
        }
    }

    private void setPrimaryTrips(GtfsMutableRelationalDao dao, List<Trip> trips) {
        // Set the default primary trips
        for (Trip trip : trips) {
            setPrimaryTripDefault(trip);
            dao.updateEntity(trip);
        }

        // Set alternative primary trips in case the default ones weren't found
        if (!hasPrimaryTrip(trips) && trips.size() > 0) {
            // Find the alternative and it's reverse order. Set both as primary trips
            String altPrimaryDirectionCode = getAlternativePrimaryDirectionCode(trips);
            String reversedDirectionCode = reverseDirectionCode(altPrimaryDirectionCode);

            for (Trip trip : trips) {
                final String directionCode = trip.getDirectionCode();
                if (Objects.equals(directionCode, altPrimaryDirectionCode) || Objects.equals(directionCode, reversedDirectionCode)) {
                    trip.setPrimaryTrip(1);
                    dao.updateEntity(trip);
                }
            }
        }
    }

    private String getAlternativePrimaryDirectionCode(List<Trip> trips) {
        for (Trip trip : trips) {
            // Let's prefer trips that start from stop A
            if (trip.getDirectionCode().startsWith("A")) {
                return trip.getDirectionCode();
            }
        }
        // Fall back to the first trip if nothing else was found
        return trips.get(0).getDirectionCode();
    }

    private String reverseDirectionCode(String code) {
        List<String> split = Arrays.asList(code.split(DIRECTION_CODE_SEPARATOR));
        Collections.reverse(split);
        return String.join(DIRECTION_CODE_SEPARATOR, split);
    }

    private boolean hasPrimaryTrip(List<Trip> trips) {
        for (Trip trip : trips) {
            if (trip.getPrimaryTrip() == 1) {
                return true;
            }
        }
        return false;
    }

    private void setPrimaryTripDefault(Trip trip) {
        final String directionCode = trip.getDirectionCode();
        final boolean primary = directionCode != null && PRIMARY_TRIPS.contains(trip.getDirectionCode());
        trip.setPrimaryTrip(primary ? 1 : 0);
    }
}
