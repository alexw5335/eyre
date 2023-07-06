package eyre

import eyre.gen.Ops
import eyre.gen.SseOp
import eyre.gen.SseOps
import eyre.util.bin8888
import eyre.util.hexc8


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
 *     Bits 16-19: op1     4
 *     Bits 20-23: op2     4
 *     Bits 24-24: i8      1
 *     Bits 25-25: rw      1
 *     Bits 26-26: o16     1
 *     Bits 27-27: mr      1
 */
@JvmInline
value class SseEnc(val value: Int) {

	constructor(
		opcode : Int,
		prefix : Int,
		escape : Int,
		ext    : Int,
		op1    : SseOp,
		op2    : SseOp,
		i8     : Int,
		rw     : Int,
		o16    : Int,
		mr     : Int,
	) : this(
		(opcode shl OPCODE_POS) or
		(prefix shl PREFIX_POS) or
		(escape shl ESCAPE_POS) or
		(ext shl EXT_POS) or
		(op1.ordinal shl OP1_POS) or
		(op2.ordinal shl OP2_POS) or
		(i8 shl I8_POS) or
		(rw shl RW_POS) or
		(o16 shl O16_POS) or
		(mr shl MR_POS)
	)

	val opcode  get() = ((value shr OPCODE_POS) and 0xFF)
	val prefix  get() = ((value shr PREFIX_POS) and 0b11)
	val escape  get() = ((value shr ESCAPE_POS) and 0b11)
	val ext     get() = ((value shr EXT_POS) and 0xF)
	val op1     get() = ((value shr OP1_POS) and 0xF).let(SseOp.values::get)
	val op2     get() = ((value shr OP2_POS) and 0xF).let(SseOp.values::get)
	val i8      get() = ((value shr I8_POS) and 0b1)
	val rw      get() = ((value shr RW_POS) and 0b1)
	val o16     get() = ((value shr O16_POS) and 0b1)
	val mr      get() = ((value shr MR_POS) and 0b1)

	fun equalExceptMem(other: SseEnc) =
		(other.i8 == i8) && (
			(op1.isM && other.op1.isM && op1 != other.op1 && op2 == other.op2) ||
			(op2.isM && other.op2.isM && op2 != other.op2 && op1 == other.op1)
		)

	fun withoutMemWidth() = when {
		op1.isM -> SseEnc((value and (0xF shl OP1_POS).inv()) or (SseOp.MEM.ordinal shl OP1_POS))
		op2.isM -> SseEnc((value and (0xF shl OP2_POS).inv()) or (SseOp.MEM.ordinal shl OP2_POS))
		else    -> this
	}

	override fun toString() = buildString {
		Prefix.values()[prefix].string?.let { append("$it ") }
		Escape.values()[escape].string?.let { append("$it ") }
		append(opcode.hexc8)
		if(ext != 0) append("/$ext")
		if(op1 == SseOp.NONE) {
			append(" NONE")
		} else {
			append(" $op1")
			if(op2 != SseOp.NONE)
				append("_$op2")
		}
		if(i8 == 1) append("_I8")
		append("  ")
		if(rw == 1) append("RW ")
		if(o16 == 1) append("O16 ")
		if(mr == 1) append("MR ")
	}

	fun compareOps(ops: Int) = value and 0b1_11111111_00000000_00000000 == ops

	companion object {
		fun makeOps(op1: SseOp, op2: SseOp, i8: Int) =
			(op1.ordinal shl OP1_POS) or (op2.ordinal shl OP2_POS) or (i8 shl I8_POS)
		const val OPCODE_POS = 0
		const val PREFIX_POS = 8
		const val ESCAPE_POS = 11
		const val EXT_POS = 13
		const val OP1_POS = 16
		const val OP2_POS = 20
		const val I8_POS = 24
		const val RW_POS = 25
		const val O16_POS = 26
		const val MR_POS = 27
	}

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