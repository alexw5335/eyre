package eyre

/**
 * - Bits 0-28: disp
 * - Bits 29-31: sectionIndex
 */
@JvmInline
value class Pos(val value: Int) {
	val disp get() = value and ((1 shl 30) - 1)
	val secIndex get() = value shr 29
}