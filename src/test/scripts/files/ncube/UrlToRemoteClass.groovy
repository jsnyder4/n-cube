package files.ncube

import ncube.grv.exp.NCubeGroovyExpression

class UrlToRemoteClass extends NCubeGroovyExpression
{
    def run()
    {
        output.urlToRemoteClass = this.class
    }
}