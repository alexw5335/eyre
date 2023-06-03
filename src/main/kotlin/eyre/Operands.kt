package eyre



enum class MultiOps(vararg val parts: Ops, val mask: OpMask? = null) {
	RM(Ops.R, Ops.M),
	R_RM(Ops.R_R, Ops.R_M),
	RM_R(Ops.R_R, Ops.M_R),
	O_A(Ops.A_O),

	BND_M64(Ops.BND_M, mask = OpMask.QWORD),
	BND_RM64(Ops.BND_R, Ops.BND_M, mask = OpMask.R1000),
	BND_BNDM128(Ops.BND_BND, Ops.BND_M, mask = OpMask.XWORD),
	BNDM128_BND(Ops.M_BND, Ops.BND_BND, mask = OpMask.XWORD),

	R_RM_I8(Ops.R_R_I8, Ops.R_M_I8),
	RM_R_I8(Ops.R_R_I8, Ops.M_R_I8),

	MEM(Ops.M, mask = OpMask.NONE),
	M16(Ops.M, mask = OpMask.WORD),
	M32(Ops.M, mask = OpMask.DWORD),
	M64(Ops.M, mask = OpMask.QWORD),
	M80(Ops.M, mask = OpMask.TWORD),
	M128(Ops.M, mask = OpMask.XWORD);

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
	RA,
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
	RA_M,
	A_O,

	// 3 operands
	R_RM_I,
	R_R_I8,
	R_M_I8,
	M_R_I8,
	RM_R_CL,

	// MMX
	MM_R,
	R_MM,
	MM_MM,
	MM_M,
	M_MM,
	MM_I8,
	R32_MM_I8,
	MM_R32M16_I8,
	MM_M_I8,
	MM_MM_I8,
	MM_X,
	X_MM,

	// Mismatched widths
	R_RM8,
	R_RM16,
	R_RM32,
	R_REG,
	R_MEM,
	R32_RM,
	R64_M128,
	RA_M512,

	// ENTER
	I16_I8,

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

	// BND
	BND_R,
	BND_M,
	M_BND,
	BND_BND,
	MIB_BND,
	BND_MIB;

}