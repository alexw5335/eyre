package eyre



enum class MultiOps(vararg val parts: Ops, val mask: OpMask? = null) {
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
	X_XM_X0(Ops.X_X, Ops.X_M);

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

	// MMX/SSE (also contains R_M)
	MM_MM,
	MM_M,
	M_MM,
	X_M,
	M_X,
	X_MM,
	MM_X,
	X_X,
	X_I8,
	M_I8,
	X_X_M_I8,
	X_R_I8,
	X_M_I8,
	X_R,
	MM_MM_I8,
	MM_M_I8,
	REG_MM_I8,
	REG_X_I8,
	M_X_I8,
	R_X_I8,
	R_X,
	X_X_I8,
	MM_R_I8,
	REG_X,
	R_MM;

}