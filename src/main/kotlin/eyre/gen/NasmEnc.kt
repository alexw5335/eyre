package eyre.gen

import eyre.*
import eyre.util.hexc8

/**
 *     O16/MR/RW: GP/SSE
 *     PSEUDO: SSE/AVX
 *     VEX.L/VEX/VEX.W: AVX/AVX512
 *     SAE/ER/BCST/VSIB/EVEX/TUPLE/K/Z: AVX512
 */
data class NasmEnc(
	val parent   : NasmEnc?,
	val mnemonic : Mnemonic,
	val prefix   : Prefix,
	val escape   : Escape,
	val opcode   : Int,
	val ext      : Int,
	val hasExt   : Boolean,
	val exts     : List<NasmExt>,
	val opEnc    : OpEnc,
	val ops      : List<Op>,
	val rw       : Int,
	val o16      : Int,
	val pseudo   : Int,
	val mr       : Boolean,
	val vexw     : VexW,
	val vexl     : VexL,
	val tuple    : TupleType?,
	val sae      : Boolean,
	val er       : Boolean,
	val bcst     : Int,
	val k        : Boolean,
	val z        : Boolean,
	val avx      : Boolean,
	val evex     : Boolean
) {
	val opsString = ops.joinToString("_")
	val op1 = ops.getOrNull(0) ?: Op.NONE
	val op2 = ops.getOrNull(1) ?: Op.NONE
	val op3 = ops.getOrNull(2) ?: Op.NONE
	val op4 = ops.getOrNull(3) ?: Op.NONE

	val simdOpEnc = SimdOpEnc.entries.firstOrNull { opEnc in it.encs }

	val memIndex = ops.indexOfFirst { it.type.isMem }
	val memOp = if(memIndex >= 0) ops[memIndex] else null
	val memWidth = memOp?.width
	val i8 = ops.isNotEmpty() && ops.last() == Op.I8

	val vsibValue = when(memOp) {
		Op.VM32X, Op.VM64X -> 1
		Op.VM32Y, Op.VM64Y -> 2
		Op.VM32Z, Op.VM64Z -> 3
		else -> 0
	}

	private val Op?.simdOp get() = when {
		this == null  -> Op.NONE
		type.isMem    -> Op.MEM
		this == Op.I8 -> Op.NONE
		else          -> this
	}

	val simdOps = SimdOps(i8, op1.simdOp, op2.simdOp, op3.simdOp, op4.simdOp, memWidth, vsibValue)

}