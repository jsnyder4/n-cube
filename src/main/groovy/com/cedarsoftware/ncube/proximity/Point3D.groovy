package com.cedarsoftware.ncube.proximity

import com.cedarsoftware.ncube.CellInfo
import groovy.transform.CompileStatic

import static java.lang.Math.sqrt

/**
 * This class is used to represent a 3D point.  This
 * class implements the Proximity interface so that it
 * can work with NCube.
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
class Point3D implements Distance<Point3D>
{
	private final double x
	private final double y
	private final double z

	Point3D(Number x, Number y, Number z)
	{
		this.x = x.doubleValue()
		this.y = y.doubleValue()
		this.z = z.doubleValue()
	}

	boolean equals(Object obj)
	{
		if (!(obj instanceof Point3D))
		{
			return false
		}

		Point3D that = (Point3D) obj;
		return x == that.x && y == that.y && z == that.z
	}

	int hashCode()
	{
		long lx = Double.doubleToRawLongBits(x)
		long ly = Double.doubleToRawLongBits(y)
		long lz = Double.doubleToRawLongBits(z)

		lx ^= lx >> 23
		lx *= 0x2127599bf4325c37L
		lx ^= lx >> 47
		int h = (int) lx

		ly ^= ly >> 23
		ly *= 0x2127599bf4325c37L
		ly ^= ly >> 47
		h = (int) ly * 31 + h

		lz ^= lz >> 23
		lz *= 0x2127599bf4325c37L
		lz ^= lz >> 47
		h = (int) lz * 47 + h

		return h
	}

	int compareTo(Point3D that)
	{
		if (x < that.x)
		{
			return -1
		}
		if (x > that.x)
		{
			return 1
		}
		if (y < that.y)
		{
			return -1
		}
		if (y > that.y)
		{
			return 1
		}
		if (z < that.z)
		{
			return -1
		}
		if (z > that.z)
		{
			return 1
		}
		return 0
	}

	double distance(Point3D that)
	{
		double dx = that.x - x
		double dy = that.y - y
		double dz = that.z - z

		sqrt(dx * dx + dy * dy + dz * dz)
	}

	String toString()
	{
        return String.format("%s, %s, %s",
                CellInfo.formatForEditing(x),
                CellInfo.formatForEditing(y),
                CellInfo.formatForEditing(z))
	}

    double getX()
    {
        return x
    }

    double getY()
    {
        return y
    }

    double getZ()
    {
        return z
    }
}
