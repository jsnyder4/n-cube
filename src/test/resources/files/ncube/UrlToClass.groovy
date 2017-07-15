package files.ncube

import ncube.grv.exp.NCubeGroovyExpression

class UrlToClass extends NCubeGroovyExpression
{
    def run()
    {
        output.urlToClass = this.class
    }
}