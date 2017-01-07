package com.cedarsoftware.ncube

import groovy.transform.CompileStatic
import org.junit.Ignore
import org.junit.Test

import static org.junit.Assert.assertTrue

/**
 * Test creating a cube with 50^5 (100,000) cells.  Set every cell, then
 * read every value back.  Goal: Strive to make this test faster and faster
 * as it really exercises the guts of the rule engine.
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br/>
 *         Copyright (c) Cedar Software LLC
 *         <br/><br/>
 *         Licensed under the Apache License, Version 2.0 (the "License")
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br/><br/>
 *         http://www.apache.org/licenses/LICENSE-2.0
 *         <br/><br/>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
@CompileStatic
class TestAllCellsInBigCube
{
    @Test
    void testAllCellsInBigCube()
    {
        for (int qq = 0; qq < 1; qq++)
        {
            long start = System.nanoTime()
            NCube<Long> ncube = new NCube("bigCube")

            for (int i = 0; i < 5; i++)
            {
                Axis axis = new Axis("axis" + i, AxisType.DISCRETE, AxisValueType.LONG, i % 2 == 0)
                ncube.addAxis(axis)
                for (int j = 0; j < 10; j++)
                {
                    if (j % 2 == 0)
                    {
                        axis.addColumn(j)
                    }
                    else
                    {
                        ncube.addColumn("axis" + i, j)
                    }
                }
            }

            Map coord = [:]
            for (int a = 1; a <= 11; a++)
            {
                coord.axis0 = a - 1
                for (int b = 1; b <= 10; b++)
                {
                    coord.axis1 = b - 1
                    for (int c = 1; c <= 11; c++)
                    {
                        coord.axis2 = c - 1
                        for (int d = 1; d <= 10; d++)
                        {
                            coord.axis3 = d - 1
                            for (long e = 1; e <= 11; e++)
                            {
                                coord.axis4 = e - 1
                                ncube.setCell(a * b * c * d * e, coord)
                            }
                        }
                    }
                }
            }
            Map output = [:]
            for (int a = 1; a <= 11; a++)
            {
                coord.axis0 = a - 1
                for (int b = 1; b <= 10; b++)
                {
                    coord.axis1 = b - 1
                    for (int c = 1; c <= 11; c++)
                    {
                        coord.axis2 = c - 1
                        for (int d = 1; d <= 10; d++)
                        {
                            coord.axis3 = d - 1
                            for (long e = 1; e <= 11; e++)
                            {
                                coord.axis4 = e - 1
                                long v = ncube.getCell(coord, output)
                                assertTrue(v == a * b * c * d * e)
                            }
                        }
                    }
                }
            }
            long stop = System.nanoTime()
            double diff = (stop - start) / 1000000.0
            println("time to build and read allCellsInBigCube = " + diff)
        }
    }

    // Uncomment for memory size testing
    @Ignore
    void testLarge1D()
    {
        long start = System.nanoTime()
        NCube<Boolean> ncube = new NCube("bigCube")
        Axis axis = new Axis("axis", AxisType.DISCRETE, AxisValueType.LONG, false)
        ncube.addAxis(axis)
        int max = 2000000
        for (int j = 0; j < max; j++)
        {
            ncube.addColumn("axis", j)
        }
        Map coord = [:]

        for (int e = 0; e < max; e++)
        {
            coord.axis = e
            ncube.setCell(true, coord)
            if (e % 1000000 == 0)
            {
                println e
            }
        }

        println 'num Cells = ' + ncube.numCells
        println 'num Potential Cells = ' + ncube.numPotentialCells
        assert ncube.numCells == ncube.numPotentialCells
        long stop = System.nanoTime()
        double diff = (stop - start) / 1000000.0
        println("time to test large 1D = " + diff)

        axis = new Axis("gender", AxisType.DISCRETE, AxisValueType.STRING, true)
        axis.addColumn('M')
        axis.addColumn('F')
        start = System.nanoTime()
        ncube.addAxis(axis)
        stop = System.nanoTime()
        diff = (stop - start) / 1000000.0
        println("time to add axis to big cube = " + diff)
    }

    // Uncomment for memory size testing
    @Ignore
    void testCubeToBlowupMemory()
    {
        long start = System.nanoTime()
        NCube<Boolean> ncube = new NCube("bigCube")

        int size = 10
        int last = 1400    // 1300 = 13 million cells, 1400 = 14 million cells, ... 17M record on 1.5GB heap

        for (int i = 0; i < 4; i++)
        {
            Axis axis = new Axis("axis" + i, AxisType.DISCRETE, AxisValueType.LONG, false)
            ncube.addAxis(axis)
            for (int j = 0; j < size; j++)
            {
                ncube.addColumn("axis" + i, j)
            }
        }
        Axis axis = new Axis("axis4", AxisType.DISCRETE, AxisValueType.LONG, false)
        ncube.addAxis(axis)
        for (int j = 0; j < last; j++)
        {
            ncube.addColumn("axis4", j)
        }

        Map coord = [:]
        for (int a = 1; a <= size; a++)
        {
            coord.axis0 = a - 1
            for (int b = 1; b <= size; b++)
            {
                coord.axis1 = b - 1
                for (int c = 1; c <= size; c++)
                {
                    coord.axis2 = c - 1
                    for (int d = 1; d <= size; d++)
                    {
                        coord.axis3 = d - 1
                        for (long e = 1; e <= last; e++)
                        {
                            coord.axis4 = e - 1
                            ncube.setCell(true, coord)
                        }
                    }
                }
                println '  ' + b
            }
            println a
        }
        println ncube.numCells
        println ncube.numPotentialCells
        long stop = System.nanoTime()
        double diff = (stop - start) / 1000000.0
        println("time to build and read allCellsInBigCube = " + diff)
    }
}
