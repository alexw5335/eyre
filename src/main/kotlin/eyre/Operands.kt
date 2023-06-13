package eyre

import eyre.Width.*



enum class SseEnc {
	NONE,
	RM,
	RMI,
	MRI,
	MR,
	MI,
}



@JvmInline
value class SseOps(val value: Int) {

	constructor(op1: SseOp, op2: SseOp, op3: SseOp) :
		this(op1.value or (op2.value shl 4) or (op3.value shl 8))

	val op1 get() = SseOp.values[(value shr 0) and 0xF]
	val op2 get() = SseOp.values[(value shr 4) and 0xF]
	val op3 get() = SseOp.values[(value shr 8) and 0xF]

	override fun toString() = when {
		this == NULL      -> "NULL"
		op1 == SseOp.NONE -> "NONE"
		op2 == SseOp.NONE -> op1.toString()
		op3 == SseOp.NONE -> "${op1}_$op2"
		else              -> "${op1}_${op2}_$op3"
	}

	fun isSimilar(other: SseOps) = op1.isSimilar(other.op1) && op2.isSimilar(other.op2) && op3.isSimilar(other.op3)

	companion object { val NULL = SseOps(-1) }
}



enum class SseOp(val value: Int, val op: Op) {
	NONE(0, Op.NONE),
	X(1, Op.X),
	MM(2, Op.MM),
	I8(3, Op.I8),
	R8(4, Op.R8),
	R16(5, Op.R16),
	R32(6, Op.R32),
	R64(7, Op.R64),
	M8(8, Op.M8),
	M16(9, Op.M16),
	M32(10, Op.M32),
	M64(11, Op.M64),
	M128(12, Op.M128),
	MEM(13, Op.MEM);

	val isX get() = value == 1
	val isMM get() = value == 2
	val isI get() = value == 3
	val isR get() = value and 0b0100 != 0
	val isM get() = value and 0b1000 != 0
	val isREG get() = isR || isX || isM

	fun isSimilar(other: SseOp) = this == other || isR && other.isR || isM && other.isM

	companion object { val values = values(); val map = values.associateBy { it.name } }
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



enum class MultiOps(vararg val parts: Ops, val mask: OpMask? = null) {
	RM(Ops.R, Ops.M),
	R_RM(Ops.R_R, Ops.R_M),
	RM_R(Ops.R_R, Ops.M_R),

	//XCHG
	O_A(Ops.A_O),
	// IMUL
	R_RM_I8(Ops.R_R_I8, Ops.R_M_I8),
	// SHLD/SHRD
	RM_R_I8(Ops.R_R_I8, Ops.M_R_I8),

	MEM(Ops.M, mask = OpMask.NONE),
	M16(Ops.M, mask = OpMask.WORD),
	M32(Ops.M, mask = OpMask.DWORD),
	M64(Ops.M, mask = OpMask.QWORD),
	M80(Ops.M, mask = OpMask.TWORD),
	M128(Ops.M, mask = OpMask.XWORD),
}



enum class Ops {
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
	M_R,
	RM_I,
	RM_I8,
	A_I,
	RM_1,
	RM_CL,
	ST_ST0,
	ST0_ST,
	A_O,

	R_RM_I,
	R_R_I8,
	R_M_I8,
	M_R_I8,
	RM_R_CL,

	// LEA
	R_MEM,
	// MOVSX/MOVZX
	R_RM8,
	R_RM16,
	// MOVSXD
	R_RM32,
	// CRC32
	R32_RM,
	// INVEPT/INVVPID/INVPCID
	R64_M128,
	// ENTER
	I16_I8,
	// UMONITOR
	RA,
	// ENQCMD/ENQCMDS/MOVDIR64B
	RA_M512,

	// MOV
	O_I,
	R_SEG,
	M_SEG,
	SEG_R,
	SEG_M,
	A_MOF,
	MOF_A,
	R_DR,
	DR_R,
	R_CR,
	CR_R,

	// IN/OUT
	A_I8,
	I8_A,
	A_DX,
	DX_A,

}