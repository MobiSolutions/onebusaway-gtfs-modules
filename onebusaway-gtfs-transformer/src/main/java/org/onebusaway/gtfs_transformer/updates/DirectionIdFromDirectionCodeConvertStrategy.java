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

import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.services.GtfsMutableRelationalDao;
import org.onebusaway.gtfs_transformer.services.GtfsTransformStrategy;
import org.onebusaway.gtfs_transformer.services.TransformContext;

public class DirectionIdFromDirectionCodeConvertStrategy implements GtfsTransformStrategy {
	
	@Override
	public void run(TransformContext context, GtfsMutableRelationalDao dao) {
		
		Collection<Trip> trips = dao.getAllTrips();
		
	    for (Trip t : trips) {
	    	String code = t.getDirectionCode();
	    	code = code.replaceAll("[0-9]", "");
			char start = code.charAt(0);
			char end = code.charAt(code.length()-1);
			if (start > end) {
				t.setDirectionId("0");
			} else {
				t.setDirectionId("1");
			}
	    	dao.updateEntity(t);
	    }
	}
}
