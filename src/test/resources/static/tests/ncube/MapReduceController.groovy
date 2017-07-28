package tests.ncube

import ncube.grv.exp.NCubeGroovyExpression

class MapReduceController extends NCubeGroovyExpression
{
    def run()
    {
        if(input.cubeName)
        {
            return mapReduce('key', 'query', "input.foo == 'OH'".toString(), ['foo'] as Set, null, input.cubeName as String)
        }
        return mapReduce('key', 'query', "input.foo == 'KY'".toString(), ['foo'] as Set)
    }
}
