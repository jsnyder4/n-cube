package com.cedarsoftware.ncube

import groovy.transform.CompileStatic
import org.junit.Test

/**
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br/>
 *         Copyright (c) Cedar Software LLC
 *         <br/><br/>
 *         Licensed under the Apache License, Version 2.0 (the 'License')
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br/><br/>
 *         http://www.apache.org/licenses/LICENSE-2.0
 *         <br/><br/>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an 'AS IS' BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
@CompileStatic
class TestColumnLevelDefault
{
    @Test
    void testColumnDefault()
    {
        NCube ncube = NCubeBuilder.getTestNCube2D(false)
        ncube.defaultCellValue = Math.PI
        Axis axis = ncube.getAxis('age')
        Column column = axis.findColumn(15)
        column.setMetaProperty(Column.DEFAULT_VALUE, 'kid')

        column = axis.findColumn(25)
        column.setMetaProperty(Column.DEFAULT_VALUE, 'adult')

        ncube.setCell(1.0d, [age:15, gender:'Male'])
        ncube.setCell(2.0d, [age:15, gender:'Female'])
        def x = ncube.getCell([age:15, gender:'Male'])
        assert x == 1.0d
        x = ncube.getCell([age:15, gender:'Female'])
        assert x == 2.0d

        x = ncube.getCell([age:25, gender:'Male'])
        assert 'adult' == x
        x = ncube.getCell([age:25, gender:'Female'])
        assert 'adult' == x

        x = ncube.getCell([age:35, gender:'Male'])
        assert Math.PI == x
        x = ncube.getCell([age:35, gender:'Female'])
        assert Math.PI == x

        x = ncube.getCell([age:50, gender:'Male'])
        assert Math.PI == x
        x = ncube.getCell([age:50, gender:'Female'])
        assert Math.PI == x

        ncube.toHtml()  // covers code inside HtmlFormatter dealing with column-level defaults
        ncube.defaultCellValue = null

        assert ncube.containsCell([age:15, gender:'Male'], true)
        assert ncube.containsCell([age:15, gender:'Female'], true)
        assert ncube.containsCell([age:15, gender:'Male'], false)
        assert ncube.containsCell([age:15, gender:'Female'], false)

        assert ncube.containsCell([age:25, gender:'Male'], true)
        assert ncube.containsCell([age:25, gender:'Female'], true)
        assert !ncube.containsCell([age:25, gender:'Male'], false)
        assert !ncube.containsCell([age:25, gender:'Female'], false)

        ncube.clearCells()

        assert ncube.containsCell([age:15, gender:'Male'], true)
        assert ncube.containsCell([age:15, gender:'Female'], true)
        assert !ncube.containsCell([age:15, gender:'Male'], false)
        assert !ncube.containsCell([age:15, gender:'Female'], false)

        x = ncube.getCell([age:15, gender:'Male'])
        assert 'kid' == x
    }

    @Test
    void testDefaultColumnDefaultValue()
    {
        NCube ncube = NCubeBuilder.getTestNCube2D(true)
        ncube.defaultCellValue = Math.PI
        Axis axis = ncube.getAxis('age')
        Column defaultColumn = axis.findColumn(null)
        defaultColumn.setMetaProperty(Column.DEFAULT_VALUE, 'kid')

        Column column = axis.findColumn(25)
        column.setMetaProperty(Column.DEFAULT_VALUE, 'adult')

        ncube.setCell(1.0d, [age:15, gender:'Male'])
        ncube.setCell(2.0d, [age:15, gender:'Female'])
        def x = ncube.getCell([age:15, gender:'Male'])
        assert x == 1.0d
        x = ncube.getCell([age:15, gender:'Female'])
        assert x == 2.0d

        x = ncube.getCell([age:25, gender:'Male'])
        assert 'adult' == x
        x = ncube.getCell([age:25, gender:'Female'])
        assert 'adult' == x

        x = ncube.getCell([age:35, gender:'Male'])
        assert Math.PI == x
        x = ncube.getCell([age:35, gender:'Female'])
        assert Math.PI == x

        x = ncube.getCell([age:50, gender:'Male'])
        assert Math.PI == x
        x = ncube.getCell([age:50, gender:'Female'])
        assert Math.PI == x

        ncube.toHtml()  // covers code inside HtmlFormatter dealing with column-level defaults
        ncube.defaultCellValue = null

        assert ncube.containsCell([age:15, gender:'Male'], true)
        assert ncube.containsCell([age:15, gender:'Female'], true)
        assert ncube.containsCell([age:15, gender:'Male'], false)
        assert ncube.containsCell([age:15, gender:'Female'], false)

        assert ncube.containsCell([age:25, gender:'Male'], true)
        assert ncube.containsCell([age:25, gender:'Female'], true)
        assert !ncube.containsCell([age:25, gender:'Male'], false)
        assert !ncube.containsCell([age:25, gender:'Female'], false)

        ncube.clearCells()

        x = ncube.getCell([gender:'Male'])
        assert 'kid' == x
    }
}
