package com.cedarsoftware.ncube.proximity

import com.cedarsoftware.ncube.CellInfo
import groovy.transform.CompileStatic

import static java.lang.Math.atan2
import static java.lang.Math.cos
import static java.lang.Math.sin
import static java.lang.Math.sqrt
import static java.lang.Math.toRadians

/**
 * This class is used to represent a latitude / longitude coordinate.
 * This class implements the Proximity interface so that it can work with NCube.
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License")
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br><br>
 *         http://www.apache.org/licenses/LICENSE-2.0
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
@CompileStatic
class LatLon implements Distance<LatLon>
{
	public static final double EARTH_RADIUS = 6371.00 // Radius in Kilometers default
    private final double lat
	private final double lon

	/**
	 * @param lat decimal degrees latitude
	 * @param lon decimal degrees longitude
	 */
	LatLon(Number lat, Number lon)
	{
		this.lat = lat.doubleValue()
		this.lon = lon.doubleValue()
	}

	boolean equals(Object obj)
	{
		if (!(obj instanceof LatLon))
		{
			return false
		}

		LatLon that = (LatLon) obj
		return lat == that.lat && lon == that.lon
	}

	int hashCode()
	{
		long lx = Double.doubleToRawLongBits(lat)
		long ly = Double.doubleToRawLongBits(lon)

		lx ^= lx >> 23
		lx *= 0x2127599bf4325c37L
		lx ^= lx >> 47
		int h = (int) lx

		ly ^= ly >> 23
		ly *= 0x2127599bf4325c37L
		ly ^= ly >> 47
		h = (int) ly * 31 + h

		return h
	}

	int compareTo(LatLon that)
	{
		if (lat < that.lat)
		{
			return -1
		}
		if (lat > that.lat)
		{
			return 1
		}
		if (lon < that.lon)
		{
			return -1
		}
		if (lon > that.lon)
		{
			return 1
		}
		return 0
	}

	/**
	 * @return the distance between another latlon coordinate
	 * and this coordinate, in kilometers.
     * Implemented using the Haversine formula.
	 */
	double distance(LatLon that)
	{
        double earthRadius = EARTH_RADIUS;
        double dLat = toRadians(that.lat - lat)
        double dLng = toRadians(that.lon - lon)
        double sinDlat2 = sin(dLat / 2.0d)
        double sinDlng2 = sin(dLng / 2.0d)
        double a = sinDlat2 * sinDlat2 + cos(toRadians(lat)) * cos(toRadians(that.lat)) * sinDlng2 * sinDlng2
        double c = 2.0d * atan2(sqrt(a), sqrt(1.0d - a))
        double dist = earthRadius * c
        return dist
	}

	String toString()
	{
        return String.format("%s, %s", CellInfo.formatForEditing(lat), CellInfo.formatForEditing(lon))
	}

    double getLat() { return lat }
    double getLon() { return lon }
}
