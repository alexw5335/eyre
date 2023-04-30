package eyre.util

import kotlin.math.absoluteValue



const val mantissaMask = (1 shl 24) - 1



private fun intToFloat(int: Int): Float {
	val input     = int.absoluteValue
	val zeroes    = input.countLeadingZeroBits()
	val exponent  = 126 + 32 - zeroes
	val remainder = input - (1 shl (31 - zeroes))
	val mantissa  = remainder shl (23 - 31 + zeroes).coerceAtMost(23)
	val sign      = if(int > 0) 0 else 1
	return Float.fromBits((sign shl 31) or (exponent shl 23) or (mantissa and ((1 shl 24) - 1)))
}



private fun floatToString(input: Float) {
	val bits = input.toRawBits()
	val sign = bits shr 31
	val exponent = (bits shr 23) and 0b11111111
	val mantissa = bits and ((1 shl 24) - 1)
	println(sign)
	println(exponent)
	println(mantissa)
}



fun Int.bitString(start: Int, length: Int): String {
	val builder = StringBuilder()
	for(i in start + length - 1 downTo start) {
		val value = (this shr i) and 1
		builder.append(if(value == 1) '1' else '0')
	}
	return builder.toString()
}



fun Int.floatBitString() = bitString(31, 1) + '_' + bitString(23, 8) + '_' + bitString(0, 23)



fun main() {
	val mantissa = 2
	val exponent = 1

	val finalMantissa = mantissa shl (22 - 31 + mantissa.countLeadingZeroBits())
	val finalExponent = exponent + 127

	val bits = finalMantissa or (finalExponent shl 23)
	val float = Float.fromBits(bits)

	println(bits.floatBitString())
	println(float)
}