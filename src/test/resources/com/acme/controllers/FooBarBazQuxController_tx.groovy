package com.acme.controllers

import ncube.grv.method.NCubeGroovyController

/**
 * Example Controller-type class.  The methods in this class
 * correspond to columns on a method axis.  When getCell()
 * or getCells() is called on the n-cube, the method specified
 * in the input map [method:'foo'] will be called.
 *
 * This class intentionally used the long-handed approach to
 * call its sibling methods.  This is done purely to highlight
 * the available methods in the parent class.
 */
class FooBarBazQuxController_tx extends NCubeGroovyController
{
    def foo()
    {
        return 3;
    }

    def bar()
    {
//        @[method:'foo'] * 3;   // the call below is identical to this call, however, it does not cause IDE complaints
        return at([method: 'foo']) * 3
    }

    def baz()
    {
        return go([method:'bar',state:'TX']) * 3
    }

    def qux()
    {
        return at([method:'baz'], "TestCube") * 3
    }
}