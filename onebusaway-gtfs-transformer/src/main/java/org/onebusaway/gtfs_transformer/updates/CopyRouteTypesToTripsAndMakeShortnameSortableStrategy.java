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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.onebusaway.gtfs.model.Route;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.services.GtfsMutableRelationalDao;
import org.onebusaway.gtfs_transformer.services.GtfsTransformStrategy;
import org.onebusaway.gtfs_transformer.services.TransformContext;

public class CopyRouteTypesToTripsAndMakeShortnameSortableStrategy implements GtfsTransformStrategy {
	
    private final static Pattern pattern = Pattern.compile("\\d+");

    private final static int SHORTNAME_NUM_LENGTH = 4;

    public int shortNameNumLength = SHORTNAME_NUM_LENGTH;
    
    public static void main(String[] arg) {
		String shortName = "EDR11";
		CopyRouteTypesToTripsAndMakeShortnameSortableStrategy strat = new CopyRouteTypesToTripsAndMakeShortnameSortableStrategy();
	    System.out.println(strat.matchAndReplace(shortName));
	}
    
	@Override
	public void run(TransformContext context, GtfsMutableRelationalDao dao) {
		Collection<Trip> trips = dao.getAllTrips();
	    for (Trip t : trips) {
	    	Route r = t.getRoute();
	    	String shortName = r.getShortName();
	    	t.setRouteType(r.getType());
	    	r.setShortNameSortable(matchAndReplace(shortName));
	    	dao.updateEntity(t);
	    	dao.updateEntity(r);
	    }
	}
	
	public void setShortNameNumLength(int shortNameNumLength) {
		this.shortNameNumLength = shortNameNumLength;
	}

	private String matchAndReplace(String shortName) {
		Matcher matcher = pattern.matcher(shortName);
		if (!matcher.find()) {
			return shortName;
		}
		String matched = matcher.group();
		String prefixed = addPrefix(matched, "0", shortNameNumLength - matched.length());
	    return matcher.replaceFirst(prefixed);
	}
	
	private static String addPrefix(String str, String prefix, int times) {
		for (int i = 0; i < times; i++) {
			str = prefix + str;
		}
		return str;
	}
}
