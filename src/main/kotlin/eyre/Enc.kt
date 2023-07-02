package eyre

import eyre.gen.Ops
import eyre.gen.SseOps
import eyre.util.bin8888


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

	val opcode get() = ((value shr OPCODE_POS) and 0xFFFF)
	val escape get() = ((value shr ESCAPE_POS) and 0b111)
	val prefix get() = ((value shr PREFIX_POS) and 0b111)
	val ext    get() = ((value shr EXT_POS) and 0b1111)
	val mask   get() = ((value shr MASK_POS) and 0b1111).let(::OpMask)
	val rexw   get() = ((value shr REXW_POS) and 0b1)
	val mm     get() = ((value shr MM_POS) and 0b1)

	fun withP66() = Enc(value or P66)

	val length get() = 1 + (((opcode + 255) and -256) and 1)

	companion object {

		private const val OPCODE_POS = 0
		private const val ESCAPE_POS = 16
		private const val PREFIX_POS = 19
		private const val EXT_POS = 22
		private const val MASK_POS = 26
		private const val REXW_POS = 30
		private const val MM_POS = 31

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

		fun ext(value: Int) = value shl EXT_POS

	}

}



inline fun Enc(block: Enc.Companion.() -> Int) = Enc(block(Enc))



/**
 *     Bits 00-15: opcode  16
 *     Bits 16-18: prefix  3
 *     Bits 19-20: escape  2
 *     Bits 21-23: ext     3  /0../7
 *     Bits 24-29: mask    6  BYTE WORD DWORD QWORD TWORD XWORD
 *     Bits 30-30: rex.w   1
 *     Bits 31-31: o16     1
 */
@JvmInline
value class GpEnc(val value: Int) {

	constructor(
		opcode : Int,
		prefix : Int,
		escape : Int,
		ext    : Int,
		mask   : OpMask,
		rexw   : Int,
		o16    : Int,
	) : this(
		(opcode shl 0) or
			(prefix shl 16) or
			(escape shl 19) or
			(ext shl 21) or
			(mask.value shl 24) or
			(rexw shl 30) or
			(o16 shl 31)
	)

	val opcode  get() = ((value shr 0 ) and 0xFFFF)
	val prefix  get() = ((value shr 16) and 0b11)
	val escape  get() = ((value shr 19) and 0b11)
	val ext     get() = ((value shr 21) and 0b1111)
	val mask    get() = ((value shr 24) and 0b111111).let(::OpMask)
	val rexw    get() = ((value shr 30) and 0b1)
	val o16     get() = ((value shr 31) and 0b1)

}



/**
 *     Bits 00-07: opcode  8
 *     Bits 08-10: prefix  3
 *     Bits 11-12: escape  2
 *     Bits 13-15: ext     3  /0../7
 *     Bits 16-23: ops     8
 *     Bits 24-24: rex.w   1
 *     Bits 25-25: o16     1
 *     Bits 26-26: mr      1
 */
@JvmInline
value class SseEnc(val value: Int) {

	constructor(
		opcode : Int,
		prefix : Int,
		escape : Int,
		ext    : Int,
		ops    : SseOps,
		rexw   : Int,
		o16    : Int,
		mr     : Int,
	) : this(
		(opcode shl 0) or
			(prefix shl 8) or
			(escape shl 11) or
			(ext shl 13) or
			(ops.value shl 16) or
			(rexw shl 24) or
			(o16 shl 25) or
			(mr shl 26)
	)

	val opcode  get() = ((value shr 0 ) and 0xFF)
	val prefix  get() = ((value shr 8 ) and 0b11)
	val escape  get() = ((value shr 11) and 0b11)
	val ext     get() = ((value shr 13) and 0b1111)
	val ops     get() = ((value shr 16) and 0xFF).let(::SseOps)
	val rexw    get() = ((value shr 24) and 0b1)
	val o16     get() = ((value shr 25) and 0b1)
	val mr      get() = ((value shr 26) and 0b1)

}



data class ManualEnc(
	val mnemonic : Mnemonic,
	val prefix   : Prefix,
	val escape   : Escape,
	val opcode   : Int,
	val mask     : OpMask,
	val ext      : Int,
	val ops      : Ops,
	val sseOps   : SseOps,
	val rexw     : Int,
	val o16      : Int,
	val pseudo   : Int,
	val mr       : Boolean
) {
	val actualExt = if(ext == -1) 0 else ext
}



class EncGroup(val mnemonic: Mnemonic) {


	var isSse = false

	val encs = ArrayList<ManualEnc>()

	var ops = 0L

	fun add(enc: ManualEnc) {
		if(enc.sseOps != SseOps.NULL) {
			isSse = true
			if(encs.any { it.sseOps == SseOps.NULL })
				error("Mixed SSE and GP encodings: $mnemonic")
			encs += enc
		} else if(enc.ops in this) {
			//encs[enc.ops.index] = enc
		} else {
			ops = ops or (1L shl enc.ops.ordinal)
			encs += enc
		}
	}

	private val Ops.index get() = (ops and ((1L shl ordinal) - 1)).countOneBits()

	operator fun get(ops: Ops) = encs[ops.index]
	operator fun contains(operands: Ops) = this.ops and (1L shl operands.ordinal) != 0L
	operator fun get(ops: SseOps) = encs.first { it.sseOps == ops }
	override fun toString() = "Group($mnemonic, ${ops.toInt().bin8888}, ${encs.joinToString()})"

}