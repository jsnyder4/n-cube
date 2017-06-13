package files.ncube

import ncube.grv.exp.NCubeGroovyExpression

class ThreadCount extends NCubeGroovyExpression
{
    def run()
    {
        if (input.get('sleep',0L)>0) sleep(input.sleep)
        'test-' + input.tid + '-' + input.cnt
    }
}