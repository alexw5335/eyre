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



inline fun Enc(block: Enc.Companion.() -> Int) = Enc(block(Enc))




data class AvxEnc(
	val opcode : Int,
	val prefix : Prefix,
	val escape : Escape,
	val ext    : Int,
	val hasExt : Boolean,
	val ops    : List<AvxOp>,
	val op1    : AvxOp,
	val op2    : AvxOp,
	val op3    : AvxOp,
	val op4    : AvxOp,
	val l      : Int,
	val w      : Int,
	val tuple  : TupleType?,
	val opEnc  : AvxOpEnc,
	val sae    : Boolean,
	val er     : Boolean,
	val bcst   : Int,
	val vsib   : VSib?,
	val k      : Boolean,
	val z      : Boolean,
	val evex   : Boolean
) {

	fun equalExceptMem(other: AvxEnc): Boolean {
		if(other.ops.size != ops.size)
			return false
		for(i in ops.indices)
			if(other.ops[i] != ops[i])
				return other.ops[i].isM && ops[i].isM
		return false
	}

	fun withoutMemWidth(): AvxEnc {
		val index = ops.indexOfFirst { it.isM }
		val copyOps = ArrayList(ops)
		copyOps[index] = AvxOp.MEM
		return when(index) {
			0    -> copy(ops = copyOps, op1 = AvxOp.MEM)
			1    -> copy(ops = copyOps, op2 = AvxOp.MEM)
			2    -> copy(ops = copyOps, op3 = AvxOp.MEM)
			3    -> copy(ops = copyOps, op4 = AvxOp.MEM)
			else -> error("Invalid index")
		}
	}

}




/*@JvmInline
value class AvxEnc(val value: Long) {
	companion object {
		const val OPCODE_POS = 0  // 8
		const val PREFIX_POS = 8  // 3
		const val ESCAPE_POS = 11 // 2
		const val EXT_POS    = 13 // 3
		const val OP1_POS    = 16 // 5
		const val OP2_POS    = 21 // 5
		const val OP3_POS    = 26 // 5
		const val OP4_POS    = 31 // 5
		const val VL_POS     = 36 // 2  L0 L128 L256 L512
		const val VW_POS     = 38 // 1  W0 W1
		const val TT_POS     = 39 // 4  16
		const val OPENC_POS  = 43 // 4  10
		const val SAE_POS    = 47 // 1
		const val ER_POS     = 48 // 1
		const val BCST_POS   = 49 // 2  NONE B16 B32 B64
		const val VSIB_POS   = 51 // 2  X Y Z (mem op must be DWORD or QWORD)
		const val EVEX_POS   = 53 // 1
	}
}*/

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
	val op1     get() = ((value shr OP1_POS) and 0xF).let(SseOp.entries::get)
	val op2     get() = ((value shr OP2_POS) and 0xF).let(SseOp.entries::get)
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
		Prefix.entries[prefix].string?.let { append("$it ") }
		Escape.entries[escape].string?.let { append("$it ") }
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