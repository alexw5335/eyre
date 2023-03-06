package eyre.util

val Short.swapEndian get() = java.lang.Short.reverseBytes(this)

val Int.swapEndian get() = Integer.reverseBytes(this)

val Long.swapEndian get() = java.lang.Long.reverseBytes(this)

val Float.swapEndian get() = java.lang.Float.intBitsToFloat(java.lang.Float.floatToRawIntBits(this).swapEndian)

val Double.swapEndian get() = java.lang.Double.longBitsToDouble(java.lang.Double.doubleToRawLongBits(this).swapEndian)
