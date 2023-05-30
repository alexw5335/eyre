package eyre.util



val Char.isHex get() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

val Boolean.int get() = if(this) 1 else 0

val Short.swapEndian get() = java.lang.Short.reverseBytes(this)

val Int.swapEndian get() = Integer.reverseBytes(this)

val Long.swapEndian get() = java.lang.Long.reverseBytes(this)

val Float.swapEndian get() = java.lang.Float.intBitsToFloat(java.lang.Float.floatToRawIntBits(this).swapEndian)

val Double.swapEndian get() = java.lang.Double.longBitsToDouble(java.lang.Double.doubleToRawLongBits(this).swapEndian)



private fun String.zeroPadded(paddedLength: Int): String {
	var s = this
	while(s.length < paddedLength) s = "0$s"
	return s
}



val Int.bin get() = Integer.toBinaryString(this)

val Int.bin8 get() = bin.zeroPadded(8)

val Int.bin16 get() = bin.zeroPadded(16)

val Int.bin32 get() = bin.zeroPadded(32)

val Int.binFull get() = "0b$bin"

val Int.bin8Full get() = "0b$bin8"

val Int.bin16Full get() = "0b$bin16"

val Int.bin32Full get() = "0b$bin32"



val Int.dec8 get() = toString().zeroPadded(3)



val Int.hex get() = Integer.toHexString(this)

val Int.hex8 get() = hex.zeroPadded(2)

val Int.hex16 get() = hex.zeroPadded(4)

val Int.hex32 get() = hex.zeroPadded(8)

val Int.hexFull get() = "0x$hex"

val Int.hex8Full get() = "0x$hex8"

val Int.hex16Full get() = "0x$hex16"

val Int.hex32Full get() = "0x$hex32"



val Int.hexc get() = Integer.toHexString(this).uppercase()

val Int.hexc8 get() = hex8.uppercase()

val Int.hexc16 get() = hex16.uppercase()

val Int.hexc32 get() = hex32.uppercase()




val Long.hex get() = java.lang.Long.toHexString(this)

val Long.hex8 get() = hex.zeroPadded(2)

val Long.hex16 get() = hex.zeroPadded(4)

val Long.hex32 get() = hex.zeroPadded(8)

val Long.hex64 get() = hex.zeroPadded(16)

val Long.hexFull get() = "0x$hex"

val Long.hex8Full get() = "0x$hex8"

val Long.hex16Full get() = "0x$hex16"

val Long.hex32Full get() = "0x$hex32"

val Long.hex64Full get() = "0x$hex64"



val Int.bin233 get() = buildString {
	val bin8 = bin8
	append(bin8, 0, 2)
	append('_')
	append(bin8, 2, 5)
	append('_')
	append(bin8, 5, 8)
}

val Int.bin44 get() = buildString {
	val bin8 = bin8
	append(bin8, 0, 4)
	append('_')
	append(bin8, 4, 8)
}



val Byte.bin get() = toUByte().toInt().bin

val Byte.bin8 get() = toUByte().toInt().bin8

val Byte.hex get() = toUByte().toInt().hex

val Byte.hex8 get() = toUByte().toInt().hex8

val Byte.bin233 get() = toUByte().toInt().bin233