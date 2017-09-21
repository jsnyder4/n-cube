package com.cedarsoftware.ncube

import groovy.transform.CompileStatic

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
class NCubeBuilder
{
    static Axis getStatesAxis()
    {
        Axis states = new Axis("State", AxisType.DISCRETE, AxisValueType.STRING, false)
        states.addColumn("AL")
        states.addColumn("AK")
        states.addColumn("AZ")
        states.addColumn("AR")
        states.addColumn("CA")
        states.addColumn("CO")
        states.addColumn("CT")
        states.addColumn("DE")
        states.addColumn("FL")
        states.addColumn("GA")
        states.addColumn("HI")
        states.addColumn("ID")
        states.addColumn("IL")
        states.addColumn("IN")
        states.addColumn("IA")
        states.addColumn("KS")
        states.addColumn("KY")
        states.addColumn("LA")
        states.addColumn("ME")
        states.addColumn("MD")
        states.addColumn("MA")
        states.addColumn("MI")
        states.addColumn("MN")
        states.addColumn("MS")
        states.addColumn("MO")
        states.addColumn("MT")
        states.addColumn("NE")
        states.addColumn("NV")
        states.addColumn("NH")
        states.addColumn("NJ")
        states.addColumn("NM")
        states.addColumn("NY")
        states.addColumn("NC")
        states.addColumn("ND")
        states.addColumn("OH")
        states.addColumn("OK")
        states.addColumn("OR")
        states.addColumn("PA")
        states.addColumn("RI")
        states.addColumn("SC")
        states.addColumn("SD")
        states.addColumn("TN")
        states.addColumn("TX")
        states.addColumn("UT")
        states.addColumn("VT")
        states.addColumn("VA")
        states.addColumn("WA")
        states.addColumn("WI")
        states.addColumn("WV")
        states.addColumn("WY")
        return states
    }

    static Axis getProvincesAxis()
    {
        Axis provinces = new Axis("Province", AxisType.DISCRETE, AxisValueType.STRING, false)
        provinces.addColumn("Quebec")
        provinces.addColumn("New Brunswick")
        provinces.addColumn("Nova Scotia")
        provinces.addColumn("Ontario")
        provinces.addColumn("Manitoba")
        provinces.addColumn("Saskatchewan")
        provinces.addColumn("Alberta")
        provinces.addColumn("British Columbia")
        provinces.addColumn("Yukon")
        provinces.addColumn("Northwest Territories")
        provinces.addColumn("Nunavut")
        provinces.addColumn("Newfoundland")
        return provinces
    }

    static Axis getContinentAxis()
    {
        Axis continent = new Axis("Continent", AxisType.DISCRETE, AxisValueType.STRING, false, Axis.DISPLAY)
        continent.addColumn("Africa")
        continent.addColumn("Antarctica")
        continent.addColumn("Asia")
        continent.addColumn("Australia")
        continent.addColumn("Europe")
        continent.addColumn("North America")
        continent.addColumn("South America")
        return continent
    }

    static Axis getDecimalRangeAxis(boolean defCol)
    {
        Axis axis = new Axis("bigD", AxisType.RANGE, AxisValueType.BIG_DECIMAL, defCol)
        axis.addColumn(new Range(-10.0, 10.0))
        axis.addColumn(new Range("20.0", "30.0"))
        axis.addColumn(new Range(100 as Byte, 1000 as Short))
        axis.addColumn(new Range(10000, 100000L))
        axis.addColumn(new Range(100000L, 9900000L))
        return axis
    }

    static Axis getDoubleRangeAxis(boolean defCol)
    {
        Axis axis = new Axis("doubleRange", AxisType.RANGE, AxisValueType.DOUBLE, defCol)
        axis.addColumn(new Range(-10.0, 10.0))
        axis.addColumn(new Range("20.0", "30.0"))
        axis.addColumn(new Range(100 as Byte, 1000 as Short))
        axis.addColumn(new Range(10000, 100000L))
        axis.addColumn(new Range(100000L, 9900000L))
        return axis
    }

    static Axis getLongRangeAxis(boolean defCol)
    {
        Axis axis = new Axis("longRange", AxisType.RANGE, AxisValueType.LONG, defCol)
        axis.addColumn(new Range(-10.0, 10.0))
        axis.addColumn(new Range("20", "30"))
        axis.addColumn(new Range(100 as Byte, 1000 as Short))
        axis.addColumn(new Range(10000, 100000L))
        axis.addColumn(new Range(100000L, 9900000L))
        return axis
    }

    static Axis getDateRangeAxis(boolean defCol)
    {
        Axis axis = new Axis("dateRange", AxisType.RANGE, AxisValueType.DATE, defCol)
        Calendar cal = Calendar.instance
        cal.set(1990, 5, 10, 13, 5, 25)
        Calendar cal1 = Calendar.instance
        cal1.set(2000, 0, 1, 0, 0, 0)
        Calendar cal2 = Calendar.instance
        cal2.set(2002, 11, 17, 0, 0, 0)
        Calendar cal3 = Calendar.instance
        cal3.set(2008, 11, 24, 0, 0, 0)
        Calendar cal4 = Calendar.instance
        cal4.set(2010, 0, 1, 12, 0, 0)
        Calendar cal5 = Calendar.instance
        cal5.set(2014, 7, 1, 12, 59, 59)

        axis.addColumn(new Range(cal, cal1.time))
        axis.addColumn(new Range(cal1, cal2.time))
        axis.addColumn(new Range(cal2, cal3))
        axis.addColumn(new Range(cal4, cal5))
        return axis
    }

    static Axis getLongDaysOfWeekAxis()
    {
        Axis axis = new Axis("Days", AxisType.DISCRETE, AxisValueType.STRING, false, Axis.DISPLAY)
        axis.addColumn("Monday")
        axis.addColumn("Tuesday")
        axis.addColumn("Wednesday")
        axis.addColumn("Thursday")
        axis.addColumn("Friday")
        axis.addColumn("Saturday")
        axis.addColumn("Sunday")
        return axis
    }

    static Axis getShortDaysOfWeekAxis()
    {
        Axis axis = new Axis("Days", AxisType.DISCRETE, AxisValueType.STRING, false, Axis.DISPLAY)
        axis.addColumn("Mon")
        axis.addColumn("Tue")
        axis.addColumn("Wed")
        axis.addColumn("Thu")
        axis.addColumn("Fri")
        axis.addColumn("Sat")
        axis.addColumn("Sun")
        return axis
    }

    static Axis getLongMonthsOfYear()
    {
        Axis axis = new Axis("Months", AxisType.DISCRETE, AxisValueType.STRING, false, Axis.DISPLAY)
        axis.addColumn("Janurary")
        axis.addColumn("February")
        axis.addColumn("March")
        axis.addColumn("April")
        axis.addColumn("May")
        axis.addColumn("June")
        axis.addColumn("July")
        axis.addColumn("August")
        axis.addColumn("September")
        axis.addColumn("October")
        axis.addColumn("November")
        axis.addColumn("December")
        return axis
    }

    static Axis getShortMonthsOfYear()
    {
        Axis axis = new Axis("Months", AxisType.DISCRETE, AxisValueType.STRING, false, Axis.DISPLAY)
        axis.addColumn("Jan")
        axis.addColumn("Feb")
        axis.addColumn("Mar")
        axis.addColumn("Apr")
        axis.addColumn("May")
        axis.addColumn("Jun")
        axis.addColumn("Jul")
        axis.addColumn("Aug")
        axis.addColumn("Sep")
        axis.addColumn("Oct")
        axis.addColumn("Nov")
        axis.addColumn("Dec")
        return axis
    }

    static Axis getGenderAxis(boolean defCol)
    {
        Axis axis = new Axis("Gender", AxisType.DISCRETE, AxisValueType.STRING, defCol)
        axis.addColumn("Male")
        axis.addColumn("Female")
        return axis
    }

    static Axis getCaseInsensitiveAxis(boolean defCol)
    {
        Axis axis = new Axis("Gender", AxisType.DISCRETE, AxisValueType.CISTRING, defCol)
        axis.addColumn("Male")
        axis.addColumn("Female")
        return axis
    }

    static Axis getEvenAxis(boolean defCol)
    {
        Axis axis = new Axis("Even", AxisType.DISCRETE, AxisValueType.LONG, defCol)
        axis.addColumn(0L)
        axis.addColumn(2L)
        axis.addColumn(4L)
        axis.addColumn(6L)
        axis.addColumn(8L)
        axis.addColumn(10L)
        return axis
    }

    static Axis getOddAxis(boolean defCol)
    {
        Axis axis = new Axis("Odd", AxisType.DISCRETE, AxisValueType.LONG, defCol)
        axis.addColumn(1L)
        axis.addColumn(3L)
        axis.addColumn(5L)
        axis.addColumn(7L)
        axis.addColumn(9L)
        return axis
    }

    static NCube getTestNCube2D(boolean defCol)
    {
        NCube<Double> ncube = new NCube<>("test.Age-Gender")
        Axis axis1 = getGenderAxis(defCol)

        Axis axis2 = new Axis("Age", AxisType.RANGE, AxisValueType.LONG, defCol)
        axis2.addColumn(new Range(0, 18))
        axis2.addColumn(new Range(18, 30))
        axis2.addColumn(new Range(30, 40))
        axis2.addColumn(new Range(40, 65))
        axis2.addColumn(new Range(65, 80))

        ncube.addAxis(axis1)
        ncube.addAxis(axis2)

        return ncube
    }

    static NCube getTestNCube3D_Boolean()
    {
        NCube<Boolean> ncube = new NCube<>("test.ValidTrailorConfigs")
        Axis axis1 = new Axis("Trailers", AxisType.DISCRETE, AxisValueType.STRING, false, Axis.DISPLAY)
        axis1.addColumn("S1A")
        axis1.addColumn("M1A")
        axis1.addColumn("L1A")
        axis1.addColumn("S2A")
        axis1.addColumn("M2A")
        axis1.addColumn("L2A")
        axis1.addColumn("M3A")
        axis1.addColumn("L3A")
        Axis axis2 = new Axis("Vehicles", AxisType.DISCRETE, AxisValueType.STRING, false)
        axis2.addColumn("car")
        axis2.addColumn("small truck")
        axis2.addColumn("med truck")
        axis2.addColumn("large truck")
        axis2.addColumn("van")
        axis2.addColumn("motorcycle")
        axis2.addColumn("limousine")
        axis2.addColumn("tractor")
        axis2.addColumn("golf cart")
        Axis axis3 = new Axis("BU", AxisType.DISCRETE, AxisValueType.STRING, false)
        axis3.addColumn("Agri")
        axis3.addColumn("SHS")

        ncube.addAxis(axis1)
        ncube.addAxis(axis2)
        ncube.addAxis(axis3)

        return ncube
    }

    static NCube getMetaPropWithFormula()
    {
        return NCube.fromSimpleJson('''\
{
  "ncube": "metaPropFormula",
  "formula": {
    "type": "exp",
    "value": "if (input.revenue != null && input.cost != null) { output.profit = (input.revenue - input.cost) * input.tax }"
  },
  "axes": [
    {
      "name": "Column",
      "hasDefault": true,
      "type": "DISCRETE",
      "valueType": "STRING",
      "preferredOrder": 1,
      "fireAll": true,
      "columns": []
    }
  ],
  "cells": []
}''')
    }

    static NCube getSimpleAutoRule()
    {
        return NCube.fromSimpleJson('''\
{
  "ncube": "0Rule",
  "axes": [
    {
      "name": "Conditions",
      "hasDefault": false,
      "type": "RULE",
      "valueType": "EXPRESSION",
      "preferredOrder": 1,
      "fireAll": true,
      "columns": [
        {
          "id": 1000000000001,
          "type": "exp",
          "name": "age-test",
          "value": "input.age < 18"
        },
        {
          "id": 1000000000002,
          "type": "exp",
          "name": "credit-score",
          "value": "input.creditScore > 700"
        }
      ]
    }
  ],
  "cells": [
    {
      "id": [
        1000000000001
      ],
      "type": "exp",
      "value": "if (input.color == 'red')\\n   output.rate +=25"
    },
    {
      "id": [
        1000000000002
      ],
      "type": "exp",
      "value": "output.rate -= 10\\nif (output.rate < 0)\\n   output.rate = 0"
    }
  ]
}''')
    }

    static NCube getSimpleAutoBadRule()
    {
        return NCube.fromSimpleJson('''\
{
  "ncube": "SmokerPenalty",
  "axes": [
    {
      "name": "Smoker",
      "hasDefault": false,
      "type": "RULE",
      "valueType": "EXPRESSION",
      "preferredOrder": 1,
      "fireAll": true,
      "columns": [
        {
          "id": 1000000000001,
          "type": "exp",
          "value": "input.age < 18"
        },
        {
          "id": 1000000000002,
          "type": "exp",
          "name": "credit-score",
          "value": "input.creditScore > 700"
        }
      ]
    },
    {
      "name": "Obesity",
      "hasDefault": false,
      "type": "RULE",
      "valueType": "EXPRESSION",
      "preferredOrder": 1,
      "fireAll": true,
      "columns": [
        {
          "id": 2000000000001,
          "type": "exp",
          "value": "input.age < 18"
        },
        {
          "id": 2000000000002,
          "type": "exp",
          "value": "input.creditScore > 700"
        }
      ]
    }
  ],
  "cells": []}''')
    }

    static NCube getDuplicateRule()
    {
        return NCube.fromSimpleJson('''\
{
  "ncube": "SmokerPenalty",
  "axes": [
    {
      "name": "Smoker",
      "hasDefault": false,
      "type": "RULE",
      "valueType": "EXPRESSION",
      "preferredOrder": 1,
      "fireAll": true,
      "columns": [
        {
          "id": 1000000000001,
          "type": "exp",
          "name": "credit-score",
          "value": "input.age < 18"
        },
        {
          "id": 1000000000002,
          "type": "exp",
          "name": "credit-score",
          "value": "input.creditScore > 700"
        }
      ]
    }
  ],
  "cells": []}''')
    }

    static NCube getTrackingTestCube()
    {
        return NCube.fromSimpleJson('''\
{
  "ncube": "TestInputKeyTracking",
  "axes": [
    {
      "name": "Column",
      "hasDefault": false,
      "type": "DISCRETE",
      "valueType": "STRING",
      "preferredOrder": 1,
      "fireAll": true,
      "columns": [
        {
          "id": 1000000000001,
          "type": "string",
          "value": "A"
        },
        {
          "id": 1000000000002,
          "type": "string",
          "value": "B"
        }
      ]
    },
    {
      "name": "Row",
      "hasDefault": true,
      "type": "DISCRETE",
      "valueType": "LONG",
      "preferredOrder": 1,
      "fireAll": true,
      "columns": [
        {
          "id": 2000000000001,
          "type": "long",
          "value": 1
        }
      ]
    }
  ],
  "cells": [
    {
      "id": [
        1000000000001,
        2000000000001
      ],
      "type": "exp",
      "value": "if (input.age < 16)\\n    output.msg = 'You cant drive'; if (input.Weight > 200)\\noutput.msg = 'you are heavy'"
    },
    {
      "id": [
        1000000000002,
        2000000000001
      ],
      "type": "exp",
      "value": "@Secondary[:]"
    },
    {
      "id": [
        1000000000001
      ],
      "type": "string",
      "value": "a1"
    },
    {
      "id": [
        1000000000002
      ],
      "type": "exp",
      "value": "if (input.containsKey('smokes'))\\n    output.rate += 150.0d"
    }
  ]
}''')
    }

    static NCube getTrackingTestCubeSecondary()
    {
        return NCube.fromSimpleJson('''\
{
  "ncube": "Secondary",
  "axes": [
    {
      "name": "State",
      "hasDefault": false,
      "type": "DISCRETE",
      "valueType": "STRING",
      "preferredOrder": 1,
      "fireAll": true,
      "columns": [
        {
          "id": 1000000000001,
          "type": "string",
          "value": "OH"
        }
      ]
    }
  ],
  "cells": [
    {
      "id": [
        1000000000001
      ],
      "type": "exp",
      "value": "println 'in secondary.'; if (input.age < 16)\\n    output.msg = 'You cant drive'; if (input.Weight > 200)\\noutput.msg = 'you are heavy'; return 9"
    }
  ]
}''')
    }

    static NCube getRule1D()
    {
        return NCube.fromSimpleJson('''\
{
  "ncube": "SimpleRule",
  "axes": [
    {
      "name": "rule",
      "type": "RULE",
      "valueType": "EXPRESSION",
      "preferredOrder": 1,
      "hasDefault": false,
      "columns": [
        {
          "id": 1000000000001,
          "type": "exp",
          "value": "true",
          "name": "init"
        },
        {
          "id": 1000000000002,
          "type":"exp",
          "value": "false",
          "name": "process"
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
      "value": "1"
    },
    {
      "id": [
        1000000000002
      ],
      "type": "string",
      "value": "2"
    }
  ]
}''')
    }

    static NCube getDiscrete1DLong()
    {
        return NCube.fromSimpleJson('''\
{
  "ncube": "discreteLong",
  "axes": [
    {
      "name": "code",
      "type": "DISCRETE",
      "valueType": "LONG",
      "preferredOrder": 1,
      "hasDefault": true,
      "columns": [
        {
          "id": 1000000000001,
          "value": "1"
        },
        {
          "id": 1000000000002,
          "value": "2"
        },
        {
          "id": 1000000000003,
          "value": "3"
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
      "value": "a"
    },
    {
      "id": [
        1000000000002
      ],
      "type": "string",
      "value": "b"
    },
    {
      "id": [
        1000000000003
      ],
      "type": "string",
      "value": "c"
    }
  ]
}''')
    }

    static NCube getTransformMultiply()
    {
        return NCube.fromSimpleJson('''\
{
  "ncube": "multiplier",
  "axes": [
    {
      "name": "transform",
      "type": "DISCRETE",
      "valueType": "LONG",
      "preferredOrder": 1,
      "hasDefault": false,
      "columns": [
        {
          "id": 1000000000001,
          "value": "1"
        },
        {
          "id": 1000000000002,
          "value": "2"
        },
        {
          "id": 1000000000003,
          "value": "3"
        },
        {
          "id": 1000000000004,
          "value": "4"
        }
      ]
    },
    {
      "name":"property",
      "hasDefault":false,
      "type":"DISCRETE",
      "valueType":"STRING",
      "preferredOrder":1,
      "fireAll":true,
      "columns":[
        {
          "id":3000000000001,
          "type":"string",
          "value":"type"
        },
        {
          "id":3000000000002,
          "type":"string",
          "value":"value"
        }
      ]
    }
  ],
  "cells": [
    {
      "id": [
        1000000000001,
        3000000000001
      ],
      "type": "string",
      "value": "remove"
    },
    {
      "id": [
        1000000000001,
        3000000000002
      ],
      "type": "string",
      "value": "1,2,3"
    },
    {
      "id": [
        1000000000002,
        3000000000001
      ],
      "type": "string",
      "value": "add"
    },
    {
      "id": [
        1000000000002,
        3000000000002
      ],
      "type": "string",
      "value": "2"
    },
    {
      "id": [
        1000000000003,
        3000000000001
      ],
      "type": "string",
      "value": "add"
    },
    {
      "id": [
        1000000000003,
        3000000000002
      ],
      "type": "string",
      "value": "4"
    },
    {
      "id": [
        1000000000004,
        3000000000001
      ],
      "type": "string",
      "value": "add"
    },
    {
      "id": [
        1000000000004,
        3000000000002
      ],
      "type": "string",
      "value": "6"
    }
  ]
}''')
    }

    static NCube get3StatesNotSorted()
    {
        return NCube.fromSimpleJson('''\
{
  "ncube": "SimpleDiscrete",
  "axes": [
    {
      "name": "state",
      "hasDefault": false,
      "type": "DISCRETE",
      "valueType": "STRING",
      "preferredOrder": 1,
      "fireAll": true,
      "columns": [
        {
          "id": 1000000000011,
          "type": "string",
          "value": "OH"
        },
        {
          "id": 1000000000012,
          "type": "string",
          "value": "GA"
        },
        {
          "id": 1000000000013,
          "type": "string",
          "value": "TX"
        }
      ]
    }
  ],
  "cells": [
    {
      "id": [
        1000000000011
      ],
      "type": "string",
      "value": "1"
    },
    {
      "id": [
        1000000000012
      ],
      "type": "string",
      "value": "2"
    },
    {
      "id": [
        1000000000013
      ],
      "type": "string",
      "value": "3"
    }
  ]
}''')
    }

    static NCube get4StatesNotSorted()
    {
        return NCube.fromSimpleJson('''\
{
  "ncube": "SimpleDiscrete",
  "axes": [
    {
      "name": "state",
      "hasDefault": false,
      "type": "DISCRETE",
      "valueType": "STRING",
      "preferredOrder": 1,
      "fireAll": true,
      "columns": [
        {
          "id": 1000000000010,
          "type": "string",
          "value": "AL"
        },
        {
          "id": 1000000000011,
          "type": "string",
          "value": "OH"
        },
        {
          "id": 1000000000012,
          "type": "string",
          "value": "GA"
        },
        {
          "id": 1000000000013,
          "type": "string",
          "value": "TX"
        }
      ]
    }
  ],
  "cells": [
    {
      "id": [
        1000000000010
      ],
      "type": "string",
      "value": "0"
    },
    {
      "id": [
        1000000000011
      ],
      "type": "string",
      "value": "1"
    },
    {
      "id": [
        1000000000012
      ],
      "type": "string",
      "value": "2"
    },
    {
      "id": [
        1000000000013
      ],
      "type": "string",
      "value": "3"
    }
  ]
}''')
    }

    static NCube getDiscrete1D()
    {
        return NCube.fromSimpleJson('''\
{
  "ncube": "SimpleDiscrete",
  "axes": [
    {
      "name": "state",
      "type": "DISCRETE",
      "valueType": "STRING",
      "preferredOrder": 1,
      "hasDefault": false,
      "columns": [
        {
          "id": 1000000000001,
          "value": "OH"
        },
        {
          "id": 1000000000002,
          "value": "TX"
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
      "value": "1"
    },
    {
      "id": [
        1000000000002
      ],
      "type": "string",
      "value": "2"
    }
  ]
}''')
    }

    static NCube getDiscrete1DAlt()
    {
        return NCube.fromSimpleJson('''\
{
  "ncube": "SimpleDiscrete",
  "axes": [
    {
      "name": "state",
      "type": "DISCRETE",
      "valueType": "STRING",
      "preferredOrder": 1,
      "hasDefault": false,
      "columns": [
        {
          "id": 1000000000011,
          "value": "OH"
        },
        {
          "id": 1000000000012,
          "value": "TX"
        }
      ]
    }
  ],
  "cells": [
    {
      "id": [
        1000000000011
      ],
      "type": "string",
      "value": "1"
    },
    {
      "id": [
        1000000000012
      ],
      "type": "string",
      "value": "2"
    }
  ]
}''')
    }

    static NCube getDiscrete1DEmpty()
    {
        return NCube.fromSimpleJson('''\
{
  "ncube": "SimpleDiscrete",
  "axes": [
    {
      "name": "state",
      "type": "DISCRETE",
      "valueType": "STRING",
      "preferredOrder": 1,
      "hasDefault": false,
      "columns": [
        {
          "id": 1000000000001,
          "value": "OH"
        },
        {
          "id": 1000000000002,
          "value": "TX"
        }
      ]
    }
  ],
  "cells": []
}''')
    }

    static NCube getDiscrete1DEmptyWithDefault()
    {
        return NCube.fromSimpleJson('''\
{
  "ncube": "SimpleDiscrete",
  "axes": [
    {
      "name": "state",
      "type": "DISCRETE",
      "valueType": "STRING",
      "preferredOrder": 1,
      "hasDefault": true,
      "columns": [
        {
          "id": 1000000000001,
          "value": "OH"
        },
        {
          "id": 1000000000002,
          "value": "TX"
        }
      ]
    }
  ],
  "cells": []
}''')
    }

    static NCube getStateReferrer()
    {
        return NCube.fromSimpleJson('''\
{
  "ncube": "States",
  "axes": [
    {
      "name": "state",
      "hasDefault": false,
      "isRef": true,
      "referenceTenant": "NONE",
      "referenceApp": "DEFAULT_APP",
      "referenceVersion": "1.0.0",
      "referenceStatus": "RELEASE",
      "referenceBranch": "HEAD",
      "referenceCubeName": "SimpleDiscrete",
      "referenceAxisName": "state",
      "transformApp": null,
      "transformVersion": null,
      "transformStatus": null,
      "transformBranch": null,
      "transformCubeName": null
    }
  ],
  "cells": [
    {
      "id": [
        1000000000001
      ],
      "type": "string",
      "value": "1"
    },
    {
      "id": [
        1000000000002
      ],
      "type": "string",
      "value": "2"
    }
  ]
}''')
    }
    static NCube getHeadLessCommands()
    {
        return NCube.fromSimpleJson('''\
{
  "ncube": "headLessCommands",
  "axes": [
    {
      "name": "command",
      "type": "DISCRETE",
      "valueType": "STRING",
      "preferredOrder": 1,
      "hasDefault": false,
      "columns": [
        {
          "id": 1000000000001,
          "value": "rating"
        },
        {
          "id": 1000000000002,
          "value": "pricing"
        },
        {
          "id": 1000000000003,
          "value": "quoting"
        }
      ]
    }
  ],
  "cells": [
    {
      "id": [
        1000000000001
      ],
      "type": "exp",
      "url": "com/acme/exp/headLess.groovy"
    },
    {
      "id": [
        1000000000002
      ],
      "type": "exp",
      "url": "com/acme/exp/notHeadLess.groovy"
    },
    {
      "id": [
        1000000000003
      ],
      "type": "exp",
      "value": "if (input.get('age') == null)\n{ output.price = 150.0d }\n else\n{ output.price = input.age * input.rate }\nreturn output.price"
    }
  ]
}''')
    }

    static NCube getTestRuleCube()
    {
        return NCube.fromSimpleJson('''\
{
  "ncube": "ruleDeleteTest",
  "axes": [
    {
      "name": "rule",
      "type": "RULE",
      "valueType": "EXPRESSION",
      "preferredOrder": 1,
      "hasDefault": false,
      "fireAll": true,
      "columns": [
        {
          "id": 1000000000001,
          "type": "exp",
          "name": "init",
          "value": "true"
        },
        {
          "id": 1000000000002,
          "type": "exp",
          "name": "br1",
          "value": "true"
        },
        {
          "id": 1000000000003,
          "type": "exp",
          "name": "br2",
          "value": "true"
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
      "value": "1"
    },
    {
      "id": [
        1000000000002
      ],
      "type": "string",
      "value": "2"
    },
    {
      "id": [
        1000000000003
      ],
      "type": "string",
      "value": "3"
    }
  ]
}''')
    }


    static NCube  getCubeWithDefaultColumns()
    {
        return NCube.fromSimpleJson('''\
{
  "ncube": "test.CubeWithDefaultColumns",
  "axes": [
    {
      "id": 1,
      "name": "Axis1",
      "hasDefault": true,
      "type": "DISCRETE",
      "valueType": "STRING",
      "preferredOrder": 0,
      "fireAll": true,
      "columns": [
        {
          "id": 1000527254003,
          "type": "string",
          "value": "Axis1Col1"
        },
        {
          "id": 1001328925773,
          "type": "string",
          "value": "Axis1Col2"
        }
      ]
    },
    {
      "id": 2,
      "name": "Axis2",
      "hasDefault": true,
      "type": "DISCRETE",
      "valueType": "STRING",
      "preferredOrder": 1,
      "fireAll": true,
      "columns": [
        {
          "id": 2000453853552,
          "type": "string",
          "value": "Axis2Col1"
        },
        {
          "id": 2001566313159,
          "type": "string",
          "value": "Axis2Col2"
        }
      ]
    }
  ],
  "cells": [
    {
      "id": [
        1000527254003,
        2000453853552
      ],
      "type": "exp",
      "value": "@CubeA[:]"
    },
    {
      "id": [],
      "type": "exp",
      "value": "@CubeD[:]"
    },
    {
      "id": [
        1001328925773,
        2001566313159
      ],
      "type": "exp",
      "value": "[]"
    },
    {
      "id": [
        1001328925773
      ],
      "type": "exp",
      "value": "@CubeC[:]"
    },
    {
      "id": [
        2001566313159
      ],
      "type": "exp",
      "value": "@CubeB[:]"
    }
  ]
}''')
    }


    static NCube  getCubeWithAllDefaults()
    {
        return NCube.fromSimpleJson('''\
{
  "ncube": "test.CubeWithAllDefaults",
  "defaultCellValueType": "exp",
  "defaultCellValue": "@CubeLevelDefault[:]",
  "axes": [
    {
      "id": 1,
      "name": "Axis1",
      "hasDefault": true,
      "type": "DISCRETE",
      "valueType": "STRING",
      "preferredOrder": 0,
      "fireAll": true,
      "columns": [
        {
          "id": 1000527254003,
          "type": "string",
          "value": "Axis1Col1"
        },
        {
          "id": 1001328925773,
          "type": "string",
          "value": "Axis1Col2"
        },
        {
          "id": 1001017210922,
          "type": "string",
          "default_value": {
            "type": "exp",
            "value": "@Axis1Col3Default[:]"
          },
          "value": "Axis1Col3"
        }
      ]
    },
    {
      "id": 2,
      "name": "Axis2",
      "hasDefault": true,
      "type": "DISCRETE",
      "valueType": "STRING",
      "preferredOrder": 1,
      "fireAll": true,
      "columns": [
        {
          "id": 2000453853552,
          "type": "string",
          "value": "Axis2Col1"
        },
        {
          "id": 2001566313159,
          "type": "string",
          "value": "Axis2Col2"
        },
        {
          "id": 2000580973802,
          "type": "string",
          "default_value": {
            "type": "exp",
            "value": "@Axis2Col3Default[:]"
          },
          "value": "Axis2Col3"
        }
      ]
    }
  ],
  "cells": [
    {
      "id": [
        1000527254003,
        2000453853552
      ],
      "type": "exp",
      "value": "@CubeA[:]"
    },
    {
      "id": [],
      "type": "exp",
      "value": "@CubeD[:]"
    },
    {
      "id": [
        1001328925773,
        2001566313159
      ],
      "type": "exp",
      "value": "[]"
    },
    {
      "id": [
        1001328925773
      ],
      "type": "exp",
      "value": "@CubeC[:]"
    },
    {
      "id": [
        2001566313159
      ],
      "type": "exp",
      "value": "@CubeB[:]"
    }
  ]
}''')
    }

    static NCube  getCubeCallingCubeWithDefaultColumn()
    {
        return NCube.fromSimpleJson('''\
{
  "ncube": "test.CubeCallingCubeWithDefaultColumn",
  "axes": [
    {
      "id": 1,
      "name": "Axis1Primary",
      "hasDefault": true,
      "type": "DISCRETE",
      "valueType": "STRING",
      "preferredOrder": 0,
      "fireAll": true,
      "columns": [
        {
          "id": 1000527254003,
          "type": "string",
          "value": "Axis1Col1"
        },
        {
          "id": 1001328925773,
          "type": "string",
          "value": "Axis1Col2"
        }
      ]
    },
    {
      "id": 2,
      "name": "Axis2Primary",
      "hasDefault": false,
      "type": "DISCRETE",
      "valueType": "STRING",
      "preferredOrder": 1,
      "fireAll": true,
      "columns": [
        {
          "id": 2000453853552,
          "type": "string",
          "value": "Axis2Col1"
        },
        {
          "id": 2001566313159,
          "type": "string",
          "value": "Axis2Col2"
        }
      ]
    }
  ],
  "cells": [
    {
      "id": [
        1000527254003,
        2000453853552
      ],
      "type": "exp",
      "value": "@test.CubeWithDefaultColumn[:]"
    },
    {
      "id": [
        1001328925773,
        2001566313159
      ],
      "type": "exp",
      "value": "@test.CubeWithDefaultColumn[:]"
    }
  ]
}''')
    }

    static NCube  getCubeWithDefaultColumn()
    {
        return NCube.fromSimpleJson('''\
{
  "ncube": "test.CubeWithDefaultColumn",
  "axes": [
    {
      "id": 1,
      "name": "Axis1Secondary",
      "hasDefault": true,
      "type": "DISCRETE",
      "valueType": "STRING",
      "preferredOrder": 0,
      "fireAll": true,
      "columns": [
        {
          "id": 1000527254003,
          "type": "string",
          "value": "Axis1Col1"
        },
        {
          "id": 1001328925773,
          "type": "string",
          "value": "Axis1Col2"
        }
      ]
    },
    {
      "id": 2,
      "name": "Axis2Secondary",
      "hasDefault": false,
      "type": "DISCRETE",
      "valueType": "STRING",
      "preferredOrder": 1,
      "fireAll": true,
      "columns": [
        {
          "id": 2000453853552,
          "type": "string",
          "value": "Axis2Col1"
        },
        {
          "id": 2001566313159,
          "type": "string",
          "value": "Axis2Col2"
        }
      ]
    },
    {
      "id": 3,
      "name": "Axis3Secondary",
      "hasDefault": true,
      "type": "DISCRETE",
      "valueType": "STRING",
      "preferredOrder": 1,
      "fireAll": true,
      "columns": [
        {
          "id": 3000813453087,
          "type": "string",
          "value": "Axis3Col1"
        }
      ]
    }
  ],
  "cells": [
    {
      "id": [
        2000453853552
      ],
      "type": "exp",
      "value": "input.remove('Axis1Primary')\\n@test.CubeCallingCubeWithDefaultColumn[:]"
    }
  ]
}''')
    }

    static NCube  getCubeWithMultipleRefCubesPerCoord()
    {
        return NCube.fromSimpleJson('''\
{
  "ncube": "test.CubeWithMultipleRefCubesPerCoord",
  "axes": [
    {
      "id": 1,
      "name": "Axis1",
      "hasDefault": false,
      "type": "DISCRETE",
      "valueType": "STRING",
      "preferredOrder": 0,
      "fireAll": true,
      "columns": [
        {
          "id": 1000527254003,
          "type": "string",
          "value": "Axis1Col1"
        },
        {
          "id": 1001328925773,
          "type": "string",
          "value": "Axis1Col2"
        }
      ]
    },
    {
      "id": 2,
      "name": "Axis2",
      "hasDefault": false,
      "type": "DISCRETE",
      "valueType": "STRING",
      "preferredOrder": 1,
      "fireAll": true,
      "columns": [
        {
          "id": 2000453853552,
          "type": "string",
          "value": "Axis2Col1"
        },
        {
          "id": 2001566313159,
          "type": "string",
          "value": "Axis2Col2"
        }
      ]
    }
  ],
  "cells": [
    {
      "id": [
        1000527254003,
        2000453853552
      ],
      "type": "exp",
      "value": "@CubeA[:] && @CubeB[:]"
    },
    {
      "id": [
        1001328925773,
        2001566313159
      ],
      "type": "exp",
      "value": "@CubeA[:] && @CubeB[:] && @CubeC[:]"
    }
  ]
}''')
    }

    static NCube  getRuleCubeWithDefaultColumn()
    {
        return NCube.fromSimpleJson('''\
{
  "ncube": "test.RuleCubeWithDefaultColumn",
  "axes": [
    {
      "id": 2,
      "name": "Axis2",
      "hasDefault": true,
      "type": "DISCRETE",
      "valueType": "STRING",
      "preferredOrder": 1,
      "fireAll": true,
      "columns": [
        {
          "id": 2000453853552,
          "type": "string",
          "value": "Axis2Col1"
        },
        {
          "id": 2001566313159,
          "type": "string",
          "value": "Axis2Col2"
        }
      ]
    },
    {
      "id": 3,
      "name": "RuleAxis1",
      "hasDefault": true,
      "type": "RULE",
      "valueType": "EXPRESSION",
      "preferredOrder": 1,
      "fireAll": true,
      "columns": [
        {
          "id": 3000480286806,
          "type": "exp",
          "name": "Condition1",
          "value": "input.foo == true"
        },
        {
          "id": 3000155851047,
          "type": "exp",
          "name": "Condition2",
          "value": "input.foo == true"
        }
      ]
    }
  ],
  "cells": []
}
''')
    }

    static NCube  getRuleCubeWithAllDefaults()
    {
        return NCube.fromSimpleJson('''\
{
  "ncube": "test.RuleCubeWithAllDefaults",
  "defaultCellValueType": "exp",
  "defaultCellValue": "@CubeLevelDefault[:]",
  "axes": [
    {
      "id": 2,
      "name": "Axis2",
      "hasDefault": false,
      "type": "DISCRETE",
      "valueType": "STRING",
      "preferredOrder": 1,
      "fireAll": true,
      "columns": [
        {
          "id": 2000453853552,
          "type": "string",
          "value": "Axis2Col1"
        },
        {
          "id": 2001566313159,
          "type": "string",
          "default_value": {
            "type": "exp",
            "value": "@Axis2Col2Default[:]"
          },
          "value": "Axis2Col2"
        }
      ]
    },
    {
      "id": 3,
      "name": "RuleAxis1",
      "hasDefault": true,
      "type": "RULE",
      "valueType": "EXPRESSION",
      "preferredOrder": 1,
      "fireAll": true,
      "columns": [
        {
          "id": 3000480286806,
          "type": "exp",
          "name": "Condition1",
          "value": "@Condition1[:]"
        },
        {
          "id": 3000155851047,
          "type": "exp",
          "name": "Condition2",
          "value": "true"
        },
        {
          "id": 3001617015824,
          "type": "exp",
          "name": "Condition3",
          "default_value": {
            "type": "exp",
            "value": "@Condition3ColumnDefault[:]"
          },
          "value": "true"
        }
      ]
    }
  ],
  "cells": [
    {
      "id": [
        2000453853552,
        3000480286806
      ],
      "type": "exp",
      "value": "@CubeB[:]"
    },
    {
      "id": [
        2000453853552,
        3000155851047
      ],
      "type": "exp",
      "value": "[]"
    },
    {
      "id": [
        2001566313159,
        3000155851047
      ],
      "type": "exp",
      "value": "@CubeC[:]"
    }
  ]
}''')
    }

    static NCube  getCubeWithColumnDefault()
    {
        return NCube.fromSimpleJson('''\
{
  "ncube": "test.CubeWithColumnDefault",
  "axes": [
    {
      "id": 1,
      "name": "Axis1",
      "hasDefault": false,
      "type": "DISCRETE",
      "valueType": "STRING",
      "preferredOrder": 0,
      "fireAll": true,
      "columns": [
        {
          "id": 1000527254003,
          "type": "string",
          "value": "Axis1Col1"
        },
        {
          "id": 1001328925773,
          "type": "string",
          "default_value": {
            "type": "exp",
            "value": "@Axis1Col2Default[:]"
          },
          "value": "Axis1Col2"
        }
      ]
    },
    {
      "id": 2,
      "name": "Axis2",
      "hasDefault": false,
      "type": "DISCRETE",
      "valueType": "STRING",
      "preferredOrder": 1,
      "fireAll": true,
      "columns": [
        {
          "id": 2000453853552,
          "type": "string",
          "value": "Axis2Col1"
        },
        {
          "id": 2001566313159,
          "type": "string",
          "value": "Axis2Col2"
        }
      ]
    }
  ],
  "cells": [
    {
      "id": [
        1000527254003,
        2000453853552
      ],
      "type": "exp",
      "value": "@CubeA[:]"
    },
    {
      "id": [
        1001328925773,
        2001566313159
      ],
      "type": "exp",
      "value": "[]"
    }
  ]
}''')
    }


    static NCube  getCubeWithCubeDefault()
    {
        return NCube.fromSimpleJson('''\
{
  "ncube": "test.CubeWithCubeDefault",
  "defaultCellValueType": "exp",
  "defaultCellValue": "@CubeA[:]",
  "axes": [
    {
      "id": 1,
      "name": "Axis1",
      "hasDefault": false,
      "type": "DISCRETE",
      "valueType": "STRING",
      "preferredOrder": 0,
      "fireAll": true,
      "columns": [
        {
          "id": 1000527254003,
          "type": "string",
          "value": "Axis1Col1"
        },
        {
          "id": 1001328925773,
          "type": "string",
          "value": "Axis1Col2"
        }
      ]
    },
    {
      "id": 2,
      "name": "Axis2",
      "hasDefault": false,
      "type": "DISCRETE",
      "valueType": "STRING",
      "preferredOrder": 1,
      "fireAll": true,
      "columns": [
        {
          "id": 2000453853552,
          "type": "string",
          "value": "Axis2Col1"
        },
        {
          "id": 2001566313159,
          "type": "string",
          "value": "Axis2Col2"
        }
      ]
    }
  ],
  "cells": [
    {
      "id": [
        1000527254003,
        2000453853552
      ],
      "type": "string",
      "value": "not a reference"
    },
    {
      "id": [
        1001328925773,
        2001566313159
      ],
      "type": "exp",
      "value": "@CubeB[:]"
    }
  ]
}''')
    }

    static NCube getRuleWithOutboundRefs()
    {
        return NCube.fromSimpleJson('''\
{
  "ncube": "test.outbound.ref",
  "defaultCellValueType": "exp",
  "defaultCellValue": "@test.price[:]",
  "axes": [
    {
      "name": "rule",
      "type": "RULE",
      "valueType": "EXPRESSION",
      "preferredOrder": 1,
      "hasDefault": false,
      "fireAll": true,
      "columns": [
        {
          "id": 1000000000001,
          "type": "exp",
          "name": "availability",
          "value": "@test.available[:]"
        }
      ]
    }
  ],
  "cells": []
}''')
    }

    static NCube get5DTestCube()
    {
        return NCube.fromSimpleJson('''\
{
  "ncube": "testMerge",
  "axes": [
    {
      "name": "Age",
      "type": "RANGE",
      "valueType": "LONG",
      "preferredOrder": 0,
      "hasDefault": false,
      "fireAll": true,
      "columns": [
        {
          "id": 1000000000001,
          "value": [
            16,
            18
          ]
        },
        {
          "id": 1000000000002,
          "value": [
            18,
            22
          ]
        }
      ]
    },
    {
      "name": "Salary",
      "type": "SET",
      "valueType": "LONG",
      "preferredOrder": 0,
      "hasDefault": false,
      "fireAll": true,
      "columns": [
        {
          "id": 2000000000001,
          "value": [
            [
              60000,
              75000
            ]
          ]
        },
        {
          "id": 2000000000002,
          "value": [
            [
              75000,
              100000
            ]
          ]
        }
      ]
    },
    {
      "name": "Log",
      "type": "NEAREST",
      "valueType": "LONG",
      "preferredOrder": 0,
      "hasDefault": false,
      "fireAll": true,
      "columns": [
        {
          "id": 3000000000001,
          "type": "long",
          "value": 100
        },
        {
          "id": 3000000000002,
          "type": "long",
          "value": 1000
        }
      ]
    },
    {
      "name": "rule",
      "type": "RULE",
      "valueType": "EXPRESSION",
      "preferredOrder": 1,
      "hasDefault": false,
      "fireAll": true,
      "columns": [
        {
          "id": 4000000000001,
          "type": "exp",
          "name": "init",
          "value": "true"
        },
        {
          "id": 4000000000002,
          "type": "exp",
          "name": "process",
          "value": "true"
        }
      ]
    },
    {
      "name": "State",
      "type": "DISCRETE",
      "valueType": "STRING",
      "preferredOrder": 1,
      "hasDefault": false,
      "fireAll": true,
      "columns": [
        {
          "id": 5000000000002,
          "type": "string",
          "value": "GA"
        },
        {
          "id": 5000000000001,
          "type": "string",
          "value": "OH"
        },
        {
          "id": 5000000000003,
          "type": "string",
          "value": "TX"
        }
      ]
    }
  ],
  "cells": [
    {
      "id": [
        1000000000001,
        2000000000001,
        3000000000001,
        4000000000001,
        5000000000001
      ],
      "type": "string",
      "value": "1"
    },
    {
      "id": [
        1000000000001,
        2000000000001,
        3000000000001,
        4000000000001,
        5000000000002
      ],
      "type": "string",
      "value": "2"
    },
    {
      "id": [
        1000000000001,
        2000000000001,
        3000000000001,
        4000000000001,
        5000000000003
      ],
      "type": "string",
      "value": "3"
    },
    {
      "id": [
        1000000000001,
        2000000000001,
        3000000000001,
        4000000000002,
        5000000000001
      ],
      "type": "string",
      "value": "4"
    },
    {
      "id": [
        1000000000001,
        2000000000001,
        3000000000001,
        4000000000002,
        5000000000002
      ],
      "type": "string",
      "value": "5"
    },
    {
      "id": [
        1000000000001,
        2000000000001,
        3000000000001,
        4000000000002,
        5000000000003
      ],
      "type": "string",
      "value": "6"
    },
    {
      "id": [
        1000000000001,
        2000000000001,
        3000000000002,
        4000000000001,
        5000000000001
      ],
      "type": "string",
      "value": "7"
    },
    {
      "id": [
        1000000000001,
        2000000000001,
        3000000000002,
        4000000000001,
        5000000000002
      ],
      "type": "string",
      "value": "8"
    },
    {
      "id": [
        1000000000001,
        2000000000001,
        3000000000002,
        4000000000001,
        5000000000003
      ],
      "type": "string",
      "value": "9"
    },
    {
      "id": [
        1000000000001,
        2000000000001,
        3000000000002,
        4000000000002,
        5000000000001
      ],
      "type": "string",
      "value": "10"
    },
    {
      "id": [
        1000000000001,
        2000000000001,
        3000000000002,
        4000000000002,
        5000000000002
      ],
      "type": "string",
      "value": "11"
    },
    {
      "id": [
        1000000000001,
        2000000000001,
        3000000000002,
        4000000000002,
        5000000000003
      ],
      "type": "string",
      "value": "12"
    },
    {
      "id": [
        1000000000001,
        2000000000002,
        3000000000001,
        4000000000001,
        5000000000001
      ],
      "type": "string",
      "value": "13"
    },
    {
      "id": [
        1000000000001,
        2000000000002,
        3000000000001,
        4000000000001,
        5000000000002
      ],
      "type": "string",
      "value": "14"
    },
    {
      "id": [
        1000000000001,
        2000000000002,
        3000000000001,
        4000000000001,
        5000000000003
      ],
      "type": "string",
      "value": "15"
    },
    {
      "id": [
        1000000000001,
        2000000000002,
        3000000000001,
        4000000000002,
        5000000000001
      ],
      "type": "string",
      "value": "16"
    },
    {
      "id": [
        1000000000001,
        2000000000002,
        3000000000001,
        4000000000002,
        5000000000002
      ],
      "type": "string",
      "value": "17"
    },
    {
      "id": [
        1000000000001,
        2000000000002,
        3000000000001,
        4000000000002,
        5000000000003
      ],
      "type": "string",
      "value": "18"
    },
    {
      "id": [
        1000000000001,
        2000000000002,
        3000000000002,
        4000000000001,
        5000000000001
      ],
      "type": "string",
      "value": "19"
    },
    {
      "id": [
        1000000000001,
        2000000000002,
        3000000000002,
        4000000000001,
        5000000000002
      ],
      "type": "string",
      "value": "20"
    },
    {
      "id": [
        1000000000001,
        2000000000002,
        3000000000002,
        4000000000001,
        5000000000003
      ],
      "type": "string",
      "value": "21"
    },
    {
      "id": [
        1000000000001,
        2000000000002,
        3000000000002,
        4000000000002,
        5000000000001
      ],
      "type": "string",
      "value": "22"
    },
    {
      "id": [
        1000000000001,
        2000000000002,
        3000000000002,
        4000000000002,
        5000000000002
      ],
      "type": "string",
      "value": "23"
    },
    {
      "id": [
        1000000000001,
        2000000000002,
        3000000000002,
        4000000000002,
        5000000000003
      ],
      "type": "string",
      "value": "24"
    },
    {
      "id": [
        1000000000002,
        2000000000001,
        3000000000001,
        4000000000001,
        5000000000001
      ],
      "type": "string",
      "value": "25"
    },
    {
      "id": [
        1000000000002,
        2000000000001,
        3000000000001,
        4000000000001,
        5000000000002
      ],
      "type": "string",
      "value": "26"
    },
    {
      "id": [
        1000000000002,
        2000000000001,
        3000000000001,
        4000000000001,
        5000000000003
      ],
      "type": "string",
      "value": "27"
    },
    {
      "id": [
        1000000000002,
        2000000000001,
        3000000000001,
        4000000000002,
        5000000000001
      ],
      "type": "string",
      "value": "28"
    },
    {
      "id": [
        1000000000002,
        2000000000001,
        3000000000001,
        4000000000002,
        5000000000002
      ],
      "type": "string",
      "value": "29"
    },
    {
      "id": [
        1000000000002,
        2000000000001,
        3000000000001,
        4000000000002,
        5000000000003
      ],
      "type": "string",
      "value": "30"
    },
    {
      "id": [
        1000000000002,
        2000000000001,
        3000000000002,
        4000000000001,
        5000000000001
      ],
      "type": "string",
      "value": "31"
    },
    {
      "id": [
        1000000000002,
        2000000000001,
        3000000000002,
        4000000000001,
        5000000000002
      ],
      "type": "string",
      "value": "32"
    },
    {
      "id": [
        1000000000002,
        2000000000001,
        3000000000002,
        4000000000001,
        5000000000003
      ],
      "type": "string",
      "value": "33"
    },
    {
      "id": [
        1000000000002,
        2000000000001,
        3000000000002,
        4000000000002,
        5000000000001
      ],
      "type": "string",
      "value": "34"
    },
    {
      "id": [
        1000000000002,
        2000000000001,
        3000000000002,
        4000000000002,
        5000000000002
      ],
      "type": "string",
      "value": "35"
    },
    {
      "id": [
        1000000000002,
        2000000000001,
        3000000000002,
        4000000000002,
        5000000000003
      ],
      "type": "string",
      "value": "36"
    },
    {
      "id": [
        1000000000002,
        2000000000002,
        3000000000001,
        4000000000001,
        5000000000001
      ],
      "type": "string",
      "value": "37"
    },
    {
      "id": [
        1000000000002,
        2000000000002,
        3000000000001,
        4000000000001,
        5000000000002
      ],
      "type": "string",
      "value": "38"
    },
    {
      "id": [
        1000000000002,
        2000000000002,
        3000000000001,
        4000000000001,
        5000000000003
      ],
      "type": "string",
      "value": "39"
    },
    {
      "id": [
        1000000000002,
        2000000000002,
        3000000000001,
        4000000000002,
        5000000000001
      ],
      "type": "string",
      "value": "40"
    },
    {
      "id": [
        1000000000002,
        2000000000002,
        3000000000001,
        4000000000002,
        5000000000002
      ],
      "type": "string",
      "value": "41"
    },
    {
      "id": [
        1000000000002,
        2000000000002,
        3000000000001,
        4000000000002,
        5000000000003
      ],
      "type": "string",
      "value": "42"
    },
    {
      "id": [
        1000000000002,
        2000000000002,
        3000000000002,
        4000000000001,
        5000000000001
      ],
      "type": "string",
      "value": "43"
    },
    {
      "id": [
        1000000000002,
        2000000000002,
        3000000000002,
        4000000000001,
        5000000000002
      ],
      "type": "string",
      "value": "44"
    },
    {
      "id": [
        1000000000002,
        2000000000002,
        3000000000002,
        4000000000001,
        5000000000003
      ],
      "type": "string",
      "value": "45"
    },
    {
      "id": [
        1000000000002,
        2000000000002,
        3000000000002,
        4000000000002,
        5000000000001
      ],
      "type": "string",
      "value": "46"
    },
    {
      "id": [
        1000000000002,
        2000000000002,
        3000000000002,
        4000000000002,
        5000000000002
      ],
      "type": "string",
      "value": "47"
    },
    {
      "id": [
        1000000000002,
        2000000000002,
        3000000000002,
        4000000000002,
        5000000000003
      ],
      "type": "string",
      "value": "48"
    }
  ]
}''')

    }
}
