def fac = { n -> n == 0 ? 1 : n * call(n - 1) }
fac(input.x)