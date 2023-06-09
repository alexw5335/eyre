package eyre

import eyre.Width.*



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



enum class MultiOps(
	vararg val parts: Ops,
	val mask: OpMask? = null,
	val mask2: OpMask? = null
) {
	RM(Ops.R, Ops.M),
	R_RM(Ops.R_R, Ops.R_M),
	RM_R(Ops.R_R, Ops.M_R),
	O_A(Ops.A_O),

	R_RM_I8(Ops.R_R_I8, Ops.R_M_I8),
	RM_R_I8(Ops.R_R_I8, Ops.M_R_I8),

	MEM(Ops.M, mask = OpMask.NONE),
	M16(Ops.M, mask = OpMask.WORD),
	M32(Ops.M, mask = OpMask.DWORD),
	M64(Ops.M, mask = OpMask.QWORD),
	M80(Ops.M, mask = OpMask.TWORD),
	M128(Ops.M, mask = OpMask.XWORD),

	MM_MMM_I8(Ops.MM_MM_I8, Ops.MM_M_I8),
	RM_X_I8(Ops.R_X_I8, Ops.M_X_I8),
	X_XM32(Ops.X_X, Ops.X_M, mask = OpMask.DWORD),
	X_XM64(Ops.X_X, Ops.X_M, mask = OpMask.QWORD),
	X_XM(Ops.X_X, Ops.M),
	XM_X(Ops.X_X, Ops.M_X),
	MM_MMM(Ops.MM_MM, Ops.MM_M),
	X_MEM(Ops.X_M, mask = OpMask.NONE),
	X_XM_I8(Ops.X_X_I8, Ops.X_M_I8),
	MM_XM(Ops.MM_X, Ops.MM_M),
	X_RM(Ops.X_R, Ops.X_M),
	X_MMM(Ops.X_M, Ops.X_MM),
	X_XM_X0(Ops.X_X, Ops.X_M),
	X_RM_I8(Ops.X_R_I8, Ops.X_M_I8),
	X_XM16(Ops.X_X, Ops.X_M, mask = OpMask.WORD),
	MM_RM32(Ops.MM_R, Ops.MM_M, mask = OpMask.DWORD),
	X_RM32(Ops.X_R, Ops.X_M, mask = OpMask.DWORD),
	X_XM32_I8(Ops.X_X_I8, Ops.X_M_I8, mask = OpMask.DWORD),
	X_XM64_I8(Ops.X_X_I8, Ops.X_M_I8, mask = OpMask.QWORD),
	X_M64(Ops.X_M, mask = OpMask.QWORD),
	M64_X(Ops.M_X, mask = OpMask.QWORD),
	MMM_MM(Ops.MM_MM, Ops.M_MM),
	XM64_X(Ops.X_X, Ops.M_X, mask = OpMask.QWORD),
	XM32_X(Ops.X_X, Ops.M_X, mask = OpMask.DWORD),
	MM_XM64(Ops.MM_X, Ops.MM_X, mask = OpMask.QWORD),
	MM_RM(Ops.MM_R, Ops.MM_M),
	RM_MM(Ops.R_MM, Ops.M_MM),
	RM_X(Ops.R_X, Ops.M_X),
}



enum class Ops {
	// No operands
	NONE,

	// 1 operand
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

	// 2 operands
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

	// 3 operands
	R_RM_I,
	R_R_I8,
	R_M_I8,
	M_R_I8,
	RM_R_CL,

	// MMX/SSE (also contains R_M)
	MM_MM,
	MM_M,
	M_MM,
	MM_I8,
	MM_MM_I8,
	MM_M_I8,
	R_MM_I8,
	R_MM,
	MM_R,
	MM_RM_I8,
	X_MM,
	MM_X,
	X_M,
	M_X,
	X_X,
	X_I8,
	X_R_I8,
	X_M_I8,
	X_R,
	M_X_I8,
	R_X_I8,
	R_X,
	X_X_I8,

	// MOVSX/MOVZX
	R_RM8,
	R_RM16,
	// MOVSXD
	R_RM32,
	// LAR/LSL
	R_REG,
	// LAR/LSL/LSS/LFS/LGS
	R_MEM,
	// CRC32
	R32_RM,
	// INVEPT/INVVPID/INVPCID
	R64_M128,
	// CVTSS2SI/CVTTSS2SI
	R_XM32,
	// CVTSD2SI/CVTTSD2SI
	R_XM64,

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
	A_MOFFS,
	MOFFS_A,
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