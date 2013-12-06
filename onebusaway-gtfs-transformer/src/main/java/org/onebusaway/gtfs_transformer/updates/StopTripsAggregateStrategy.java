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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

import org.onebusaway.gtfs.model.ServiceCalendar;
import org.onebusaway.gtfs.model.Stop;
import org.onebusaway.gtfs.model.StopTime;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.services.GtfsMutableRelationalDao;
import org.onebusaway.gtfs_transformer.services.GtfsTransformStrategy;
import org.onebusaway.gtfs_transformer.services.TransformContext;

import com.google.gson.Gson;

public class StopTripsAggregateStrategy implements GtfsTransformStrategy {
	
	@Override
	public void run(TransformContext context, GtfsMutableRelationalDao dao) {
		
	    Collection<Stop> stops = dao.getAllStops();
	    for (Stop s : stops) {
	    	TreeSet<TripAggregate> trips = new TreeSet<TripAggregate>();
	    	List<StopTime> stopTimes = dao.getStopTimesForStop(s);
	    	for (StopTime st : stopTimes) {
	    		Trip trip = st.getTrip();
	    		ServiceCalendar calendar = dao.getCalendarForServiceId(trip.getServiceId());
	    		TripAggregate tripAggregate = new TripAggregate(
	    				trip.getRoute().getId().getId(), 
	    				trip.getRoute().getShortName(),
	    				trip.getRoute().getShortNameSortable(),
	    				trip.getTripLongName(),
	    				trip.getRoute().getType(),
	    				trip.getTripHeadsign(),
	    				calendar.getStartDate().getAsString(),
	    				calendar.getEndDate().getAsString());
	    		trips.add(tripAggregate);
	    	}
	    	s.setTripsAggregated(new Gson().toJson(trips.toArray()));
	    	dao.updateEntity(s);
	    }
	}
	
	class TripAggregate implements Comparable {

	    private String tripLongName;
	    private String headsign;
	    private String routeId;
	    private String routeShortName;
	    private String routeShortNameSortable;
	    private int routeType;
	    private String startDate;
	    private String endDate;

	    public TripAggregate(String routeId, String routeShortName, String routeShortNameSortable, String tripLongName, int routeType, String headsign, String startDate, String endDate) {
	        this.routeId = routeId;
	        this.routeShortName = routeShortName;
	        this.routeShortNameSortable = routeShortNameSortable;
	        this.tripLongName = tripLongName;
	        this.routeType = routeType;
	        this.headsign = headsign;
	        this.startDate = startDate;
	        this.endDate = endDate;
	    }

	    public String getHeadsign() {
	        return headsign;
	    }

	    public void setHeadsign(String headsign) {
	        this.headsign = headsign;
	    }

	    public String getRouteId() {
	        return routeId;
	    }

	    public void setRouteId(String routeId) {
	        this.routeId = routeId;
	    }

	    public String getRouteShortName() {
	        return routeShortName;
	    }

	    public void setRouteShortName(String routeShortName) {
	        this.routeShortName = routeShortName;
	    }

	    public int getRouteType() {
	        return routeType;
	    }

	    public void setRouteType(int routeType) {
	        this.routeType = routeType;
	    }
	    
	    public String getTripLongName() {
			return tripLongName;
		}

		public void setTripLongName(String tripLongName) {
			this.tripLongName = tripLongName;
		}

		public String getStartDate() {
			return startDate;
		}

		public void setStartDate(String startDate) {
			this.startDate = startDate;
		}

		public String getEndDate() {
			return endDate;
		}

		public void setEndDate(String endDate) {
			this.endDate = endDate;
		}

		public String getRouteShortNameSortable() {
			return routeShortNameSortable;
		}

		public void setRouteShortNameSortable(String routeShortNameSortable) {
			this.routeShortNameSortable = routeShortNameSortable;
		}

		@Override
	    public int hashCode() {
	    	return routeId.hashCode() ^ tripLongName.hashCode();
	    }
	    
	    @Override
	    public boolean equals(Object obj) {
	    	return routeId.equals(((TripAggregate)obj).getRouteId()) && routeShortName.equals(((TripAggregate)obj).getRouteShortName());
	    }

		@Override
		public int compareTo(Object o) {
			if (routeShortNameSortable != null && ((TripAggregate)o).routeShortNameSortable != null) {
				return routeShortNameSortable.compareTo(((TripAggregate)o).routeShortNameSortable);
			}
			return routeShortName.compareTo(((TripAggregate)o).routeShortName);
		}

	}
}
