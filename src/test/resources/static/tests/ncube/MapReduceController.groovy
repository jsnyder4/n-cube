package tests.ncube

import ncube.grv.exp.NCubeGroovyExpression

class MapReduceController extends NCubeGroovyExpression
{
    def run()
    {
        if(input.cubeName)
        {
            return mapReduce('key', 'query', { Map input -> input.foo == 'OH'}, ['foo'] as Set, null, input.cubeName as String)
        }
        return mapReduce('key', 'query', { Map input -> input.foo == 'KY' }, ['foo'] as Set)
    }
}
