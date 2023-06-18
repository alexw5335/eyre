package eyre



class ModRM(private val value: Int) {

	constructor(mod: Int, reg: Int, rm: Int) : this((mod shl 6) or (reg shl 3) or rm)

	val modrm get() = value
	val mod   get() = value shr 6
	val reg   get() = (value shr 3) and 0b111
	val rm    get() = value and 0b111

}



class Rex(private val value: Int) {

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
 * - Bits 19-22: Prefix
 * - Bits 23-26: Extension
 * - Bits 27-31: Mask
 * - Bits 32-32: REX.W
 * - Bits 33-33: O16
 */
class Enc(private val value: Long) {

	val opcode get() = ((value shr 0)          and 0xFFFF ).toInt()
	val escape get() = ((value shr ESCAPE_POS) and 0b111  ).toInt()
	val prefix get() = ((value shr PREFIX_POS) and 0b111  ).toInt()
	val ext    get() = ((value shr EXT_POS)    and 0b1111 ).toInt()
	val mask   get() = ((value shr MASK_POS)   and 0b1111 ).toInt().let(::OpMask)
	val rw     get() = ((value shr RW_POS)     and 0b1    ).toInt()
	val o16    get() = ((value shr O16_POS)    and 0b1    ).toInt()

	val length get() = 1 + (((opcode + 255) and -256) and 1)

	companion object {

		private const val ESCAPE_POS = 16
		private const val PREFIX_POS = 19
		private const val EXT_POS = 23
		private const val MASK_POS = 27
		private const val RW_POS = 31
		private const val O16_POS = 32

		const val E0F = 1 shl ESCAPE_POS
		const val E00 = 2 shl ESCAPE_POS
		const val E38 = 3 shl ESCAPE_POS
		const val E3A = 4 shl ESCAPE_POS

		const val P66 = 1 shl PREFIX_POS
		const val P67 = 2 shl PREFIX_POS
		const val PF2 = 3 shl PREFIX_POS
		const val PF3 = 4 shl PREFIX_POS

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

		const val RW = 1 shl RW_POS
		const val O16 = 1 shl O16_POS

	}

}



inline fun Enc(block: Enc.Companion.() -> Long) = Enc(block(Enc))