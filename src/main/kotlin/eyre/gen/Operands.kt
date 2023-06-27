package eyre.gen

import eyre.Width.*
import eyre.OpMask
import eyre.Width


/**
 * Bits 0-0: i8
 * Bits 1-3: op1
 * Bits 4-7: op2
 */
@JvmInline
value class SseOps(val value: Int) {

	constructor(i8: Boolean, op1: SseOp, op2: SseOp) :
		this((if(i8) 1 else 0) or (op1.ordinal shl 1) or (op2.ordinal shl 4))

	val i8   get() = (value shr 1) and 1
	val op1  get() = SseOp.values[(value shr 1) and 0b111]
	val op2  get() = SseOp.values[(value shr 4) and 0b111]

	override fun toString() = when {
		op1 == SseOp.NONE -> "NONE"
		op2 == SseOp.NONE -> if(i8 != 0) "${op1}_I8" else "$op1"
		else -> if(i8 != 0) "${op1}_${op2}_I8" else "${op1}_${op2}"
	}

	companion object {
		val NULL = SseOps(-1)
	}

}



enum class SseOp {
	NONE,
	X,
	MM,
	R8,
	R16,
	R32,
	R64,
	M;

	companion object { val values = values() }
}



enum class OpType {
	R,
	M,
	I,
	A,
	C,
	ST,
	MISC,
	REL
}



enum class Op(val type: OpType, val width: Width?) {

	NONE(OpType.MISC, null),

	R8(OpType.R, BYTE),
	R16(OpType.R, WORD),
	R32(OpType.R, DWORD),
	R64(OpType.R, QWORD),

	MEM(OpType.M, null),
	M8(OpType.M, BYTE),
	M16(OpType.M, WORD),
	M32(OpType.M, DWORD),
	M64(OpType.M, QWORD),
	M80(OpType.M, TWORD),
	M128(OpType.M, XWORD),
	M256(OpType.M, YWORD),
	M512(OpType.M, ZWORD),

	I8(OpType.I, BYTE),
	I16(OpType.I, WORD),
	I32(OpType.I, DWORD),
	I64(OpType.I, QWORD),

	AL(OpType.A, BYTE),
	AX(OpType.A, WORD),
	EAX(OpType.A, DWORD),
	RAX(OpType.A, QWORD),
	CL(OpType.C, BYTE),
	ECX(OpType.C, DWORD),
	RCX(OpType.C, QWORD),
	DX(OpType.MISC, WORD),

	REL8(OpType.REL, BYTE),
	REL16(OpType.REL, WORD),
	REL32(OpType.REL, DWORD),

	ST(OpType.ST, TWORD),
	ST0(OpType.ST, TWORD),

	ONE(OpType.MISC, null),

	MM(OpType.MISC, QWORD),

	X(OpType.MISC, XWORD),
	Y(OpType.MISC, YWORD),
	Z(OpType.MISC, ZWORD),

	VM32X(OpType.MISC, XWORD),
	VM64X(OpType.MISC, XWORD),
	VM32Y(OpType.MISC, YWORD),
	VM64Y(OpType.MISC, YWORD),
	VM32Z(OpType.MISC, ZWORD),
	VM64Z(OpType.MISC, ZWORD),

	K(OpType.MISC, null),

	BND(OpType.MISC, null),

	T(OpType.MISC, null),

	MOFFS8(OpType.MISC, BYTE),
	MOFFS16(OpType.MISC, WORD),
	MOFFS32(OpType.MISC, DWORD),
	MOFFS64(OpType.MISC, QWORD),

	SEG(OpType.MISC, null),

	CR(OpType.MISC, QWORD),
	DR(OpType.MISC, QWORD),

	FS(OpType.MISC, null),
	GS(OpType.MISC, null),
	
}



enum class MultiOps(vararg val parts: Ops, val mask: OpMask? = null, val mr: Boolean = false) {
	RM(Ops.R, Ops.M),
	R_RM(Ops.R_R, Ops.R_M),
	RM_R(Ops.R_R, Ops.M_R, mr = true),
	O_A(Ops.A_O),
	MEM(Ops.M, mask = OpMask.NONE),
	M8(Ops.M, mask = OpMask.BYTE),
	M16(Ops.M, mask = OpMask.WORD),
	M32(Ops.M, mask = OpMask.DWORD),
	M64(Ops.M, mask = OpMask.QWORD),
	M80(Ops.M, mask = OpMask.TWORD),
	M128(Ops.M, mask = OpMask.XWORD);
}



enum class Ops(val mr: Boolean = false) {

	NONE,

	R,
	M,
	I8,
	I16,
	I32,
	AX,
	REL8,
	REL32,
	ST,
	FS,
	GS,
	O,

	R_R,
	R_M,
	M_R(mr = true),
	RM_I,
	RM_I8,
	A_I,
	RM_1,
	RM_CL,
	ST_ST0,
	ST0_ST,
	A_O,

	// IMUL
	R_RM_I,
	R_RM_I8,
	// SHLD/SHRD
	RM_R_I8(mr = true),
	RM_R_CL(mr = true),

	// LEA/LFS/LGS/LSS
	R_MEM,
	// MOVSX/MOVZX/CRC32
	R_RM8,
	R_RM16,
	// MOVSXD
	R_RM32,
	// INVEPT/INVVPID/INVPCID
	R_M128,
	// ENQCMD/ENQCMDS/MOVDIR64B
	RA_M512,

	// ENTER
	I16_I8,
	// UMONITOR
	RA,

	// MOV
	O_I,
	R_SEG(mr = true),
	M_SEG(mr = true),
	SEG_R,
	SEG_M,
	A_MOF,
	MOF_A,
	R_DR(mr = true),
	DR_R,
	R_CR(mr = true),
	CR_R,

	// IN/OUT
	A_I8,
	I8_A,
	A_DX,
	DX_A,

}