/**
 * Copyright (C) 2012 Google, Inc.
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

import org.junit.Before;
import org.junit.Test;
import org.onebusaway.collections.beans.PropertyPathExpression;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.ShapePoint;
import org.onebusaway.gtfs.model.StopTime;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.services.GtfsMutableRelationalDao;
import org.onebusaway.gtfs.services.MockGtfs;
import org.onebusaway.gtfs_transformer.match.AlwaysMatch;
import org.onebusaway.gtfs_transformer.match.PropertyValueEntityMatch;
import org.onebusaway.gtfs_transformer.match.SimpleValueMatcher;
import org.onebusaway.gtfs_transformer.match.TypedEntityMatch;
import org.onebusaway.gtfs_transformer.services.TransformContext;
import org.onebusaway.gtfs_transformer.updates.TrimTripTransformStrategy.TrimOperation;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static junit.framework.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PrimaryTripFromDirectionCodeConvertStrategyTest {

  private PrimaryTripFromDirectionCodeConvertStrategy _strategy = new PrimaryTripFromDirectionCodeConvertStrategy();

  private MockGtfs _gtfs;

  private TransformContext _context = new TransformContext();

  @Before
  public void setup() throws IOException {
    _gtfs = MockGtfs.create();
    _gtfs.putAgencies(1);
    _gtfs.putStops(6);
    _gtfs.putStopTimes("t0", "s0,s1,s2,s3,s4,s5");
    _gtfs.putRoutes(1);
  }

  private void putTrips(String... directionCodes) {
    putTrips(directionCodes.length, directionCodes);
  }

  private void putTrips(int count, String... directionCodes) {
    StringJoiner joiner = new StringJoiner(",");
    for (String code : directionCodes) {
      joiner.add(code);
    }
    _gtfs.putTrips(count, "r0", "sid0", "direction_code=" + joiner.toString());
  }

  private Collection<Trip> filterDirectionCode(Collection<Trip> trips, String directionCode) {
    return trips.stream().filter(trip -> trip.getDirectionCode().equals(directionCode)).collect(Collectors.toList());
  }

  private void assertPrimary(Collection<Trip> trips) {
    for (Trip trip : trips) {
      assertEquals(1, trip.getPrimaryTrip());
    }
  }

  private void assertNotPrimary(Collection<Trip> trips) {
    for (Trip trip : trips) {
      assertEquals(0, trip.getPrimaryTrip());
    }
  }

  private void assertTripsByDirectionCode(Collection<Trip> allTrips, String directionCode, int assertCount, boolean assertPrimary) {
    Collection<Trip> sublist = filterDirectionCode(allTrips, directionCode);
    assertEquals(assertCount, sublist.size());
    if (assertPrimary) {
      assertPrimary(sublist);
    } else {
      assertNotPrimary(sublist);
    }
  }

  @Test
  public void test_ROUND_TRIP() throws IOException {
    putTrips(10, "A>A");
    GtfsMutableRelationalDao dao = _gtfs.read();
    _strategy.run(_context, dao);

    Collection<Trip> allTrips = dao.getAllTrips();
    assertEquals(10, allTrips.size());
    assertPrimary(allTrips);
  }

  @Test
  public void test_ROUND_TRIP_OUT_MULTIPLE() throws IOException {
    putTrips("A>A", "A>A", "A>A", "A>B", "A>B", "A>B");

    GtfsMutableRelationalDao dao = _gtfs.read();
    _strategy.run(_context, dao);

    Collection<Trip> trips = dao.getAllTrips();
    assertEquals(6, trips.size());

    assertTripsByDirectionCode(trips, "A>A", 3, true);
    assertTripsByDirectionCode(trips, "A>B", 3, false);
  }

  @Test
  public void test_ROUND_TRIP_IN_MULTIPLE() throws IOException {
    putTrips("A>A", "A>A", "A>A", "B>A", "B>A", "B>A");

    GtfsMutableRelationalDao dao = _gtfs.read();
    _strategy.run(_context, dao);

    Collection<Trip> trips = dao.getAllTrips();
    assertEquals(6, trips.size());

    assertTripsByDirectionCode(trips, "A>A", 3, true);
    assertTripsByDirectionCode(trips, "B>A", 3, false);
  }

  @Test
  public void test_OUT_PRIMARY_IN_EXACT_MULTIPLE() throws IOException {
    putTrips("A>B", "A>B", "A>B", "B>A", "B>A", "B>A", "A1>B", "A>B2", "B1>A", "B>A1");

    GtfsMutableRelationalDao dao = _gtfs.read();
    _strategy.run(_context, dao);

    Collection<Trip> trips = dao.getAllTrips();
    assertEquals(10, trips.size());


    assertTripsByDirectionCode(trips, "A>B", 3, true);
    assertTripsByDirectionCode(trips, "B>A", 3, true);
    assertTripsByDirectionCode(trips, "A1>B", 1, false);
    assertTripsByDirectionCode(trips, "A>B2", 1, false);
    assertTripsByDirectionCode(trips, "B1>A", 1, false);
    assertTripsByDirectionCode(trips, "B>A1", 1, false);
  }

  @Test
  public void test_OUT_FALLBACK_ALPHA_SORT_IN_FALLBACK_ALPHA_SORT() throws IOException {
    putTrips("A>B1", "A>B2", "A>C", "C>A", "B1>A", "B2>A");

    GtfsMutableRelationalDao dao = _gtfs.read();
    _strategy.run(_context, dao);

    Collection<Trip> trips = dao.getAllTrips();
    assertEquals(6, trips.size());


    assertTripsByDirectionCode(trips, "A>B1", 1, true);
    assertTripsByDirectionCode(trips, "A>B2", 1, false);
    assertTripsByDirectionCode(trips, "A>C", 1, false);
    assertTripsByDirectionCode(trips, "C>A", 1, false);
    assertTripsByDirectionCode(trips, "B2>A", 1, false);
    assertTripsByDirectionCode(trips, "B1>A", 1, true);
  }

  @Test
  public void test_OUT_MULTIPLE_IN_MULTIPLE_REVERSE_EXACT() throws IOException {
    putTrips("A>B", "A>B", "A>C>B", "A>C>B", "B>A", "B>A", "B>C>A", "B>C>A");

    GtfsMutableRelationalDao dao = _gtfs.read();
    _strategy.run(_context, dao);

    Collection<Trip> trips = dao.getAllTrips();
    assertEquals(8, trips.size());


    assertTripsByDirectionCode(trips, "A>B", 2, true);
    assertTripsByDirectionCode(trips, "B>A", 2, true);
    assertTripsByDirectionCode(trips, "A>C>B", 2, false);
    assertTripsByDirectionCode(trips, "B>C>A", 2, false);
  }

  @Test
  public void test_OUT_MULTIPLE_IN_REVERSE_START_END() throws IOException {
    putTrips("A>B", "A>B", "A>C>B", "A>C>B", "B>C>A", "B>C>A");

    GtfsMutableRelationalDao dao = _gtfs.read();
    _strategy.run(_context, dao);

    Collection<Trip> trips = dao.getAllTrips();
    assertEquals(6, trips.size());


    assertTripsByDirectionCode(trips, "A>B", 2, true);
    assertTripsByDirectionCode(trips, "A>C>B", 2, false);
    assertTripsByDirectionCode(trips, "B>C>A", 2, true);
  }

  @Test
  public void test_OUT_PRIMARY_IN_FALLBACK() throws IOException {
    putTrips("A>B", "A>B", "C>A", "C>A");

    GtfsMutableRelationalDao dao = _gtfs.read();
    _strategy.run(_context, dao);

    Collection<Trip> trips = dao.getAllTrips();
    assertEquals(4, trips.size());


    assertTripsByDirectionCode(trips, "A>B", 2, true);
    assertTripsByDirectionCode(trips, "C>A", 2, true);
  }

  @Test
  public void test_OUT_MULTIPLE_FALLBACK_IN_REVERSE_START_END() throws IOException {
    putTrips("A>C", "A>C", "A>C>B", "A>C>B", "B>C>A", "B>C>A");

    GtfsMutableRelationalDao dao = _gtfs.read();
    _strategy.run(_context, dao);

    Collection<Trip> trips = dao.getAllTrips();
    assertEquals(6, trips.size());


    assertTripsByDirectionCode(trips, "A>C", 2, true);
    assertTripsByDirectionCode(trips, "A>C>B", 2, false);
    assertTripsByDirectionCode(trips, "B>C>A", 2, true);
  }

  @Test
  public void test_OUT_MULTIPLE_FALLBACK_IN_MULTIPLE_FALLBACK() throws IOException {
    putTrips("A>B>C2", "A>B>C1", "A>D", "A>B>C>D", "B>C>A", "D>B", "D>A");

    GtfsMutableRelationalDao dao = _gtfs.read();
    _strategy.run(_context, dao);

    Collection<Trip> trips = dao.getAllTrips();
    assertEquals(7, trips.size());

    // Sorted alpabetically
    assertTripsByDirectionCode(trips, "A>B>C1", 1, true);
    assertTripsByDirectionCode(trips, "A>B>C2", 1, false);
    assertTripsByDirectionCode(trips, "A>D", 1, false);
    assertTripsByDirectionCode(trips, "A>B>C>D", 1, false);
    assertTripsByDirectionCode(trips, "B>C>A", 1, true);
    assertTripsByDirectionCode(trips, "D>B", 1, false);
    assertTripsByDirectionCode(trips, "D>A", 1, false);
  }


  @Test
  public void test_OUT_FALLBACK_IN_REVERSE_EXACT() throws IOException {
    putTrips("A>C", "A>C", "C>B>A", "C>A");

    GtfsMutableRelationalDao dao = _gtfs.read();
    _strategy.run(_context, dao);

    Collection<Trip> trips = dao.getAllTrips();
    assertEquals(4, trips.size());


    assertTripsByDirectionCode(trips, "A>C", 2, true);
    assertTripsByDirectionCode(trips, "C>A", 1, true);
    assertTripsByDirectionCode(trips, "C>B>A", 1, false);
  }

  @Test
  public void test_OUT_FALLBACK_IN_REVERSE_START_END() throws IOException {
    putTrips("A>C", "A>C", "C>B>A", "C>B>A");

    GtfsMutableRelationalDao dao = _gtfs.read();
    _strategy.run(_context, dao);

    Collection<Trip> trips = dao.getAllTrips();
    assertEquals(4, trips.size());


    assertTripsByDirectionCode(trips, "A>C", 2, true);
    assertTripsByDirectionCode(trips, "C>B>A", 2, true);
  }

  @Test
  public void test_OUT_4_STOPS_IN_REVERSE_EXACT() throws IOException {
    putTrips("A>B>C>D", "A>B>C>D", "D>C>B>A", "D>C>B>A", "D>A", "D>A");

    GtfsMutableRelationalDao dao = _gtfs.read();
    _strategy.run(_context, dao);

    Collection<Trip> trips = dao.getAllTrips();
    assertEquals(6, trips.size());


    assertTripsByDirectionCode(trips, "A>B>C>D", 2, true);
    assertTripsByDirectionCode(trips, "D>C>B>A", 2, true);
    assertTripsByDirectionCode(trips, "D>A", 2, false);
  }

  @Test
  public void test_OUT_4_STOPS_IN_REVERSE_START_END() throws IOException {
    putTrips("A>B>C>D", "A>B>C>D", "D>C>B>F", "D>C>B>F", "D>A", "D>A");

    GtfsMutableRelationalDao dao = _gtfs.read();
    _strategy.run(_context, dao);

    Collection<Trip> trips = dao.getAllTrips();
    assertEquals(6, trips.size());


    assertTripsByDirectionCode(trips, "A>B>C>D", 2, true);
    assertTripsByDirectionCode(trips, "D>C>B>F", 2, false);
    assertTripsByDirectionCode(trips, "D>A", 2, true);
  }
}
