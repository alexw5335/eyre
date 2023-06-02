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
	R_RM_I(Ops.R_R_I, Ops.R_M_I),
	RM_R_I8(Ops.R_R_I8, Ops.M_R_I8),
	RM_R_CL(Ops.R_R_CL, Ops.M_R_CL),

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
	R_REG(Ops.R_R, mask2 = OpMask.R),
	R_MEM(Ops.R_M, mask2 = OpMask.NONE),
	R32_RM(Ops.R_R, Ops.R_M, mask1 = OpMask.DWORD),
	RA_M512(Ops.RA_M, mask2 = OpMask.ZWORD);

}



@Suppress("EnumEntryName")
enum class Spec {
	NONE,
	_CL,
	_I8;
}



enum class Ops(
	val size: Int = 0, 
	val index: Int = 0,
	val spec: Spec = Spec.NONE
) {
	NONE(0, 0),
	
	R(1, 0),
	M(1, 1),
	I8(1, 2),
	I16(1, 3),
	I32(1, 4),
	O(1, 5),
	AX(1, 6),
	REL8(1, 7),
	REL16(1, 8),
	REL32(1, 9),
	ST(1, 10),
	RA(1, 11),

	R_R(2, 0),
	R_M(2, 1),
	M_R(2, 2),
	RM_I(2, 3),
	RM_I8(2, 4),
	A_I(2, 5),
	RM_1(2, 6),
	RM_CL(2, 7),
	ST_ST0(2, 8),
	ST0_ST(2, 9),
	A_O(2, 10),
	O_I(2, 11),
	RA_M(2, 12),
	
	R_R_I(3, 0),
	R_M_I(3, 1),
	R_R_I8(3, 2, Spec._I8),
	R_M_I8(3, 3, Spec._I8),
	M_R_I8(3, 4, Spec._I8),
	R_R_CL(3, 5, Spec._CL),
	M_R_CL(3, 6, Spec._CL),

	FS(-1),
	GS(-1),
	I16_I8(-1),
	R_SEG(-1),
	M_SEG(-1),
	SEG_R(-1),
	SEG_M(-1),
	A_MOFFS(-1),
	MOFFS_A(-1),
	A_I8(-1),
	I8_A(-1),
	A_DX(-1),
	DX_A(-1),
	R_DR(-1),
	DR_R(-1),
	R_CR(-1),
	CR_R(-1),
	BND_R64(-1),
	BND_M64(-1),
	BND_MIB(-1),
	BND_BND(-1),
	BND_M128(-1),
	M128_BND(-1),
	MIB_BND(-1);

	val isCustom get() = size < 0

}