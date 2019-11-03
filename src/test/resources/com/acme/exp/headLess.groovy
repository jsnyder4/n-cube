if (input.get('age') == null)
{
    output.price = 150.0d
}
else
{
    output.price = input.age * input.rate
}
return output.price
