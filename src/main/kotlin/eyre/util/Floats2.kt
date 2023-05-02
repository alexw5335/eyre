package eyre.util

import java.math.BigInteger



fun main() {
	val int = 314159L
	val exponent = -5
	val numerator = BigInteger.valueOf(int) * BigInteger.TWO.pow(51)
	val denominator = BigInteger.valueOf(10).pow(-exponent)

	val division = numerator.divideAndRemainder(denominator)
	val quotient = division[0]
	val remainder = division[1]

	println(numerator)
	println(denominator)
	println(quotient)
	println(remainder)
	println(BigInteger("123456789012345670000000000000000000000").toByteArray().size)
}