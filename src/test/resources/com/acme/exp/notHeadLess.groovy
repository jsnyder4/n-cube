package com.acme.exp

import ncube.grv.exp.NCubeGroovyExpression

class Pricing extends NCubeGroovyExpression
{
    def run()
    {
        if (input.get('age') == null)
        {
            output.price = 150.0d
        }
        else
        {
            output.price = input.age * input.rate
        }
        return output.price
    }
}
