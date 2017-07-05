package com.cedarsoftware.ncube
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
class TestEnums
{
    @Test
    void testDeltaEnum()
    {
        assert Delta.Location.values().length == 11
        assert Delta.Type.values().length == 4
    }

    @Test
    void testChangeType()
    {
        assert ChangeType.values().length == 6
    }
}
