package eyre



class ModRM(private val value: Int) {

	constructor(mod: Int, reg: Int, rm: Int) : this((mod shl 6) or (reg shl 3) or rm)

	val modrm get() = value
	val mod   get() = value shr 6
	val reg   get() = (value shr 3) and 0b111
	val rm    get() = value and 0b111

}



class Rex(val value: Int) {

	constructor(w: Int, r: Int, x: Int, b: Int, force: Int, ban: Int) : this(
		(w shl 3) or
		(r shl 2) or
		(x shl 1) or
		(b shl 0) or
		(force shl 4) or
		(ban shl 5)
	)

	val w      get() = (value shr 3) and 1
	val r      get() = (value shr 2) and 1
	val x      get() = (value shr 1) and 1
	val b      get() = (value shr 0) and 1
	val rex    get() = value and 0b1111
	val forced get() = (value shr 4) and 1 == 1
	val banned get() = (value shr 5) and 1 == 1

}


/**
 * - Bits 0-15:  Opcode
 * - Bits 16-18: Escape
 * - Bits 19-21: Prefix
 * - Bits 22-25: Extension
 * - Bits 26-29: Mask
 * - Bits 30-30: REX.W
 * - Bits 31-31: Mismatch
 */
class Enc(val value: Int) {

	val opcode   get() = ((value shr 0) and 0xFFFF)
	val escape   get() = ((value shr ESCAPE_POS) and 0b111)
	val prefix   get() = ((value shr PREFIX_POS) and 0b111)
	val ext      get() = ((value shr EXT_POS) and 0b1111)
	val mask     get() = ((value shr MASK_POS) and 0b1111).let(::OpMask)
	val rexw     get() = ((value shr REXW_POS) and 0b1)
	val mismatch get() = ((value shr MISMATCH_POS) and 0b1)

	val length get() = 1 + (((opcode + 255) and -256) and 1)

	companion object {

		private const val ESCAPE_POS = 16
		private const val PREFIX_POS = 19
		private const val EXT_POS = 22
		private const val MASK_POS = 26
		private const val REXW_POS = 30
		private const val MISMATCH_POS = 31

		const val E0F = 1 shl ESCAPE_POS
		const val E38 = 2 shl ESCAPE_POS
		const val E3A = 3 shl ESCAPE_POS

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
		const val EXT8 = 8 shl EXT_POS
		const val EXT9 = 9 shl EXT_POS

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
		const val MISMATCH = 1 shl MISMATCH_POS

		fun ext(value: Int) = value shl EXT_POS

	}

}



inline fun Enc(block: Enc.Companion.() -> Int) = Enc(block(Enc))