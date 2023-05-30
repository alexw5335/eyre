package eyre.encoding

import eyre.OpMask


val ccList = arrayOf(
	"O" to 0,
	"NO" to 1,
	"B" to 2, "NAE" to 2, "C" to 2,
	"NB" to 3, "AE" to 3, "NC" to 3,
	"Z" to 4, "E" to 4,
	"NZ" to 5, "NE" to 5,
	"BE" to 6, "NA" to 6,
	"NBE" to 7, "A" to 7,
	"S" to 8,
	"NS" to 9,
	"P" to 9, "PE" to 10,
	"NP" to 11, "PO" to 11,
	"L" to 12, "NGE" to 12,
	"NL" to 13, "GE" to 13,
	"LE" to 14, "NG" to 14,
	"NLE" to 15, "JG" to 15
)



enum class Cops(
	vararg val parts: Ops,
	val mask1: OpMask? = null,
	val mask2: OpMask? = null
) {
	RM(Ops.R, Ops.M),
	R_RM(Ops.R_R, Ops.R_M),
	RM_R(Ops.R_R, Ops.M_R),
	RM_I(Ops.R_I, Ops.M_I),
	RM_I8(Ops.R_I8, Ops.M_I8),
	RM_1(Ops.R_1, Ops.M_1),
	RM_CL(Ops.R_CL, Ops.M_CL),

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
	R32_RM(Ops.R_R, Ops.R_M, mask1 = OpMask.DWORD);
	
	companion object { val map = values().associateBy { it.name } }

}



enum class Spec {
	NONE,
	O,
	A,
	A_I,
	RM_CL,
	RM_1,
	RM_I8,
	A_O,
	O_I;
}



enum class Ops(val spec: Spec = Spec.NONE) {
	NONE,
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

	R_R,
	R_M,
	M_R,
	R_I,
	M_I,
	R_I8(Spec.RM_I8),
	M_I8(Spec.RM_I8),
	A_I(Spec.A_I),
	R_1(Spec.RM_1),
	M_1(Spec.RM_1),
	R_CL(Spec.RM_CL),
	M_CL(Spec.RM_CL),
	ST_ST0,
	ST0_ST,
	A_O(Spec.A_O),
	O_A(Spec.A_O),
	O_I(Spec.O_I),

	I16_I8,
	R_SEG, M_SEG, SEG_R, SEG_M,
	A_MOFFS, MOFFS_A,
	A_I8, I8_A, A_DX, DX_A,
	R_DR, DR_R,
	R_CR, R_CR8, CR_R, CR8_R,
	BND_R64, BND_M64, BND_MIB, BND_BND, BND_M128, M128_BND, MIB_BND,

	R_R_I8,
	R_M_I8,
	R_R_I,
	R_M_I,
	M_R_I8,
	R_R_CL,
	M_R_CL;

	companion object { val map = values().associateBy { it.name } }

}
