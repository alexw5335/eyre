package eyre



enum class Cops(
	vararg val parts: Ops,
	val mask1: OpMask? = null,
	val mask2: OpMask? = null
) {
	RM(Ops1.R, Ops1.M),
	R_RM(Ops2.R_R, Ops2.R_M),
	RM_R(Ops2.R_R, Ops2.M_R),
	RM_I(Ops2.R_I, Ops2.M_I),
	RM_I8(Ops2.R_I8, Ops2.M_I8),
	RM_1(Ops2.R_1, Ops2.M_1),
	RM_CL(Ops2.R_CL, Ops2.M_CL),

	BND_RM64(CustomOps.BND_R64, CustomOps.BND_M64),
	BND_BNDM128(CustomOps.BND_BND, CustomOps.BND_M128),
	BNDM128_BND(CustomOps.M128_BND, CustomOps.BND_BND),

	R_RM_I8(Ops3.R_R_I8, Ops3.R_M_I8),
	R_RM_I(Ops3.R_R_I, Ops3.R_M_I),
	RM_R_I8(Ops3.R_R_I8, Ops3.M_R_I8),
	RM_R_CL(Ops3.R_R_CL, Ops3.M_R_CL),

	MEM(Ops1.M, mask1 = OpMask.NONE),
	M16(Ops1.M, mask1 = OpMask.WORD),
	M32(Ops1.M, mask1 = OpMask.DWORD),
	M64(Ops1.M, mask1 = OpMask.QWORD),
	M80(Ops1.M, mask1 = OpMask.TWORD),
	M128(Ops1.M, mask1 = OpMask.XWORD),

	R_RM8(Ops2.R_R, Ops2.R_M, mask2 = OpMask.BYTE),
	R_RM16(Ops2.R_R, Ops2.R_M, mask2 = OpMask.WORD),
	R_RM32(Ops2.R_R, Ops2.R_M, mask2 = OpMask.DWORD),
	R_M128(Ops2.R_M, mask2 = OpMask.XWORD),
	R_M512(Ops2.R_M, mask2 = OpMask.ZWORD),
	R_REG(Ops2.R_R, mask2 = OpMask.R),
	R_MEM(Ops2.R_M, mask2 = OpMask.NONE),
	R32_RM(Ops2.R_R, Ops2.R_M, mask1 = OpMask.DWORD),
	RA_M512(Ops2.RA_M, mask2 = OpMask.ZWORD);

}



@Suppress("EnumEntryName")
enum class Spec {
	NONE,
	O,
	A,
	A_I,
	_1,
	A_O,
	O_I,
	_CL,
	_I8,
	RA;
}



sealed interface Ops {
	val ordinal: Int
	val spec: Spec
}



object Ops0 : Ops {
	override val ordinal = 0
	override val spec = Spec.NONE
}



enum class Ops1(override val spec: Spec = Spec.NONE) : Ops {
	R,
	M,
	I8,
	I16,
	I32,
	O(Spec.O),
	FS,
	GS,
	A(Spec.A),
	REL8,
	REL16,
	REL32,
	ST,
	RA(Spec.RA)
}



enum class Ops2(override val spec: Spec = Spec.NONE) : Ops {
	R_R,
	R_M,
	M_R,
	R_I,
	M_I,
	R_I8(Spec._I8),
	M_I8(Spec._I8),
	A_I(Spec.A_I),
	R_1(Spec._1),
	M_1(Spec._1),
	R_CL(Spec._CL),
	M_CL(Spec._CL),
	ST_ST0,
	ST0_ST,
	A_O(Spec.A_O),
	O_A(Spec.A_O),
	O_I(Spec.O_I),
	RA_M(Spec.RA);
}



enum class Ops3(override val spec: Spec = Spec.NONE) : Ops {
	R_R_I8(Spec._I8),
	R_M_I8(Spec._I8),
	M_R_I8(Spec._I8),
	R_R_I,
	R_M_I,
	R_R_CL(Spec._CL),
	M_R_CL(Spec._CL),
}



enum class CustomOps : Ops {
	I16_I8,
	R_SEG, M_SEG, SEG_R, SEG_M,
	A_MOFFS, MOFFS_A,
	A_I8, I8_A, A_DX,
	DX_A, R_DR, DR_R, R_CR, CR_R,
	BND_R64, BND_M64, BND_MIB, BND_BND, BND_M128, M128_BND, MIB_BND;
	override val spec = Spec.NONE
}