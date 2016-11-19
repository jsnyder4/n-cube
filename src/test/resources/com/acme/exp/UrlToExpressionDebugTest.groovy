package com.acme.exp

//import com.acme.util.Dumbo
import ncube.grv.exp.NCubeGroovyExpression

class UrlToExpressionDebugTest extends NCubeGroovyExpression
{
    def run()
    {
        if (input.age == null)
        {
            return -1
        }
//        Dumbo dumbo = new Dumbo()
//        println dumbo.multiplyBy10(24)

        LibCode libCode = new LibCode()
        libCode.pow(input.age as double, 2d)
    }
}
