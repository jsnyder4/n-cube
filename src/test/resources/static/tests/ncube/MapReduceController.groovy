package tests.ncube

import com.cedarsoftware.ncube.NCube
import ncube.grv.exp.NCubeGroovyExpression

class MapReduceController extends NCubeGroovyExpression
{
    def run()
    {
        // remove the 'key' that was used to access this cell to prevent mapReduce from limiting output
        input.remove('key')

        Map options = [(NCube.MAP_REDUCE_COLUMNS_TO_SEARCH): ['foo'] as Set]
        if(input.cubeName)
        {
            return mapReduce('query', { Map input -> input.foo == 'OH'}, options, input.cubeName as String)
        }
        return mapReduce('query', { Map input -> input.foo == 'KY' }, options)
    }
}
