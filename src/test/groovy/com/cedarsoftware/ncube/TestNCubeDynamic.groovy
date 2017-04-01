package com.cedarsoftware.ncube

import org.junit.Test

/**
 * NCube tests run in dynamic (NOT CompileStatic) mode.  Required for some of the Groovy
 * short-hand tests.
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
class TestNCubeDynamic
{
    @Test
    void testNCubeGroovyShortHandAccessors()
    {
        String json = '''\
{
  "ncube": "ruleDeleteTest",
  "axes": [
    {
      "name": "system",
      "type": "DISCRETE",
      "valueType": "STRING",
      "preferredOrder": 1,
      "hasDefault": false,
      "columns": [
        {
          "id": 1000000000001,
          "value": "sys-a"
        }
      ]
    }
  ],
  "cells": [
    {
      "id": [
        1000000000001
      ],
      "type": "string",
      "value": "alpha"
    }
  ]
}'''
        NCube ncube = NCube.fromSimpleJson(json)
        Axis axis = ncube.system
        assert axis.name == 'system'
        axis = ncube.'system'
        assert axis.name == 'system'
        axis = ncube['system'] as Axis
        assert axis.name == 'system'
    }
}
