package eyre

import eyre.gen.*
import eyre.util.hexc8



/**
 * - Note: 2-byte opcodes are only used by encodings that don't use this class, including:
 *     - FPU encodings
 *     - XABORT I8
 *     - XBEGIN REL32
 *     - HRESET I8
 *
 * - Bits 0-7:  Opcode
 * - Bits 8-10: Escape
 * - Bits 11-13: Prefix
 * - Bits 14-17: Extension
 * - Bits 18-21: Mask
 * - Bits 22-22: REX.W
 * - Bits 23-23: Mismatch
 */
@JvmInline
value class Enc(val value: Int) {

	val opcode get() = ((value shr OPCODE_POS) and 0xFF)
	val escape get() = ((value shr ESCAPE_POS) and 0b111)
	val prefix get() = ((value shr PREFIX_POS) and 0b111)
	val ext    get() = ((value shr EXT_POS)    and 0xF)
	val mask   get() = ((value shr MASK_POS)   and 0xF).let(::OpMask)
	val rexw   get() = ((value shr REXW_POS)   and 0b1)
	val mm     get() = ((value shr MM_POS)     and 0b1)

	companion object {

		private const val OPCODE_POS = 0
		private const val ESCAPE_POS = 8
		private const val PREFIX_POS = 11
		private const val EXT_POS = 14
		private const val MASK_POS = 18
		private const val REXW_POS = 22
		private const val MM_POS = 23

		const val ENP = 0
		const val E0F = 1 shl ESCAPE_POS
		const val E38 = 2 shl ESCAPE_POS
		const val E3A = 3 shl ESCAPE_POS

		const val PNP = 0
		const val P66 = 1 shl PREFIX_POS
		const val PF2 = 2 shl PREFIX_POS
		const val PF3 = 3 shl PREFIX_POS
		const val P9B = 4 shl PREFIX_POS

		const val EXT0 = 0 shl EXT_POS
		const val EXT1 = 1 shl EXT_POS
		const val EXT2 = 2 shl EXT_POS
		const val EXT3 = 3 shl EXT_POS
		const val EXT4 = 4 shl EXT_POS
		const val EXT5 = 5 shl EXT_POS
		const val EXT6 = 6 shl EXT_POS
		const val EXT7 = 7 shl EXT_POS

		const val R0000 = 0  shl MASK_POS
		const val R0001 = 1  shl MASK_POS
		const val R0010 = 2  shl MASK_POS
		const val R0011 = 3  shl MASK_POS
		const val R0100 = 4  shl MASK_POS
		const val R0101 = 5  shl MASK_POS
		const val R0110 = 6  shl MASK_POS
		const val R0111 = 7  shl MASK_POS
		const val R1000 = 8  shl MASK_POS
		const val R1001 = 9  shl MASK_POS
		const val R1010 = 10 shl MASK_POS
		const val R1011 = 11 shl MASK_POS
		const val R1100 = 12 shl MASK_POS
		const val R1101 = 13 shl MASK_POS
		const val R1110 = 14 shl MASK_POS
		const val R1111 = 15 shl MASK_POS

		const val RW = 1 shl REXW_POS
		const val MM = 1 shl MM_POS

	}

}