package com.cedarsoftware.ncube.exception

import groovy.transform.CompileStatic

/**
 * Sub-class of RuntimeException for extra clarification if needed.
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License");
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
class CoordinateNotFoundException extends RuntimeException
{
    private final String cubeName
    private final Map coordinate
    private final String axisName
    private final Object value

    CoordinateNotFoundException(String msg, String cubeName = null, Map coordinate = null, String axisName = null, Object value = null)
    {
        super(msg)
        this.cubeName = cubeName
        this.coordinate = coordinate
        this.axisName = axisName
        this.value = value
    }

    /**
     * Returns the cube name.
     *
     * @return required cube name
     */
    String getCubeName()
    {
        return cubeName
    }

    /**
     * Returns the coordinate.
     *
     * @return coordinate
     */
    Map getCoordinate()
    {
        return coordinate
    }

    /**
     * Returns the name of the axis on which the value is not found.
     *
     * @return axis name
     */
    String getAxisName()
    {
        return axisName
    }

    /**
     * Returns the value not found on the axis.
     *
     * @return value not found
     */
    Object getValue()
    {
        return value
    }
}
