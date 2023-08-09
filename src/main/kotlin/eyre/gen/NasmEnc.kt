package eyre.gen

import eyre.*

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
	val opEnc    : NasmOpEnc,
	val ops      : List<NasmOp>,
	val rw       : Int,
	val o16      : Int,
	val a32      : Int,
	val opreg    : Boolean,
	val pseudo   : Int,
	val mr       : Boolean,
	val vexw     : NasmVexW,
	val vexl     : NasmVexL,
	val tuple    : NasmTuple?,
	val sae      : Boolean,
	val er       : Boolean,
	val bcst     : Int,
	val k        : Boolean,
	val z        : Boolean,
	val avx      : Boolean,
	val evex     : Boolean
) {
	val opsString = ops.joinToString("_")
	val op1 = ops.getOrNull(0) ?: NasmOp.NONE
	val op2 = ops.getOrNull(1) ?: NasmOp.NONE
	val op3 = ops.getOrNull(2) ?: NasmOp.NONE
	val op4 = ops.getOrNull(3) ?: NasmOp.NONE

	val simdOpEnc = OpEnc.entries.firstOrNull { opEnc in it.encs } ?: OpEnc.RVM

	val i8 = ops.isNotEmpty() && ops.last() == NasmOp.I8

	val vsibValue = when(ops.firstOrNull { it.type.isMem }) {
		NasmOp.VM32X, NasmOp.VM64X -> 1
		NasmOp.VM32Y, NasmOp.VM64Y -> 2
		NasmOp.VM32Z, NasmOp.VM64Z -> 3
		else -> 0
	}

	private val NasmOp?.opType: OpType get() = when {
		this == null -> OpType.BND
		type.isReg -> when(this) {
			NasmOp.R8  -> OpType.R8
			NasmOp.R16 -> OpType.R16
			NasmOp.R32 -> OpType.R32
			NasmOp.R64 -> OpType.R64
			NasmOp.MM  -> OpType.MM
			NasmOp.X   -> OpType.X
			NasmOp.Y   -> OpType.Y
			NasmOp.Z   -> OpType.Z
			NasmOp.K   -> OpType.K
			NasmOp.T   -> OpType.T
			NasmOp.ST  -> OpType.ST
			NasmOp.AX  -> OpType.R16
			else   -> OpType.BND
		}
		type.isMem -> OpType.MEM
		this == NasmOp.I8 -> OpType.IMM
		else -> OpType.NONE
	}

	val simdOps = AutoOps(
		op1.opType.ordinal,
		op2.opType.ordinal,
		op3.opType.ordinal,
		op4.opType.ordinal,
		ops.firstOrNull { it.type.isMem }?.width?.let { it.ordinal + 1 } ?: 0,
		vsibValue,
	)

	val rel = ops.size == 1 && ops[0].type == NasmOpType.REL
	val ax = ops.size == 1 && ops[0] == NasmOp.AX

	// 0: None, 1: ST or ST_ST0, 2: ST0_ST
	val fpuOps: Int = when(ops.size) {
		1 -> if(ops[0] == NasmOp.ST) 1 else 0
		2 -> when {
			ops[0] == NasmOp.ST0 && ops[1] == NasmOp.ST -> 1
			ops[0] == NasmOp.ST && ops[1] == NasmOp.ST0 -> 2
			else -> 0
		}
		else -> 0
	}
}