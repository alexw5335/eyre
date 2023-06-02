package eyre



enum class MultiOps(
	vararg val parts: Ops,
	val mask1: OpMask? = null,
	val mask2: OpMask? = null
) {
	RM(Ops.R, Ops.M),
	R_RM(Ops.R_R, Ops.R_M),
	RM_R(Ops.R_R, Ops.M_R),
	O_A(Ops.A_O),

	BND_RM64(Ops.BND_R64, Ops.BND_M64),
	BND_BNDM128(Ops.BND_BND, Ops.BND_M128),
	BNDM128_BND(Ops.M128_BND, Ops.BND_BND),

	R_RM_I8(Ops.R_R_I8, Ops.R_M_I8),
	RM_R_I8(Ops.R_R_I8, Ops.M_R_I8),

	MEM(Ops.M, mask1 = OpMask.NONE),
	M16(Ops.M, mask1 = OpMask.WORD),
	M32(Ops.M, mask1 = OpMask.DWORD),
	M64(Ops.M, mask1 = OpMask.QWORD),
	M80(Ops.M, mask1 = OpMask.TWORD),
	M128(Ops.M, mask1 = OpMask.XWORD),

	R_RM8(Ops.R_R, Ops.R_M, mask2 = OpMask.BYTE),
	R_RM16(Ops.R_R, Ops.R_M, mask2 = OpMask.WORD),
	R_RM32(Ops.R_R, Ops.R_M, mask2 = OpMask.DWORD),
	R_M128(Ops.R_M, mask2 = OpMask.XWORD),
	R_M512(Ops.R_M, mask2 = OpMask.ZWORD),
	R_REG(Ops.R_R, mask2 = OpMask.R1111),
	R_MEM(Ops.R_M, mask2 = OpMask.NONE),
	R32_RM(Ops.R_R, Ops.R_M, mask1 = OpMask.DWORD),
	RA_M512(Ops.RA_M, mask2 = OpMask.ZWORD);

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
	RA,
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
	O_I,
	RA_M,
	A_O,

	R_RM_I,
	R_R_I8,
	R_M_I8,
	M_R_I8,
	RM_R_CL,

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

	I16_I8,
	R_SEG,
	M_SEG,
	SEG_R,
	SEG_M,
	A_MOFFS,
	MOFFS_A,
	A_I8,
	I8_A,
	A_DX,
	DX_A,
	R_DR,
	DR_R,
	R_CR,
	CR_R,
	BND_R64,
	BND_M64,
	BND_MIB,
	BND_BND,
	BND_M128,
	M128_BND,
	MIB_BND;
	
	val isCustom get() = ordinal >= O.ordinal

}