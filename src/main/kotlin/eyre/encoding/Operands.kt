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
	"NLE" to 15, "G" to 15
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
	R32_RM(Ops.R_R, Ops.R_M, mask1 = OpMask.DWORD),
	RA_M512(Ops.RA_M, mask2 = OpMask.ZWORD);
	
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
	O_I,
	RA;
}



enum class Ops(val size: Int, val index: Int = 0, val spec: Spec = Spec.NONE) {
	NONE(0, 0),

	R(1, 0),
	M(1, 1),
	I8(1, 2),
	I16(1, 3),
	I32(1, 4),
	O(1, 5, Spec.O),
	FS(1, 6),
	GS(1, 7),
	A(1, 8, Spec.A),
	REL8(1, 9),
	REL16(1, 10),
	REL32(1, 11),
	ST(1, 12),
	RA(1, 13, Spec.RA),

	R_R(2, 0),
	R_M(2, 1),
	M_R(2, 2),
	R_I(2, 3),
	M_I(2, 4),
	R_I8(2, 5, Spec.RM_I8),
	M_I8(2, 6, Spec.RM_I8),
	A_I(2, 7, Spec.A_I),
	R_1(2, 8, Spec.RM_1),
	M_1(2, 9, Spec.RM_1),
	R_CL(2, 10, Spec.RM_CL),
	M_CL(2, 11, Spec.RM_CL),
	ST_ST0(2, 12),
	ST0_ST(2, 13),
	A_O(2, 14, Spec.A_O),
	O_A(2, 15, Spec.A_O),
	O_I(2, 16, Spec.O_I),
	RA_M(2, 17, Spec.RA),

	R_R_I8(3, 0),
	R_M_I8(3, 1),
	R_R_I(3, 2),
	R_M_I(3, 3),
	M_R_I8(3, 4),
	R_R_CL(3, 5),
	M_R_CL(3, 6),

	I16_I8(2),
	R_SEG(2), M_SEG(2), SEG_R(2), SEG_M(2),
	A_MOFFS(2), MOFFS_A(2),
	A_I8(2), I8_A(2), A_DX(2), DX_A(2),
	R_DR(2), DR_R(2), R_CR(2), CR_R(2),
	BND_R64(2), BND_M64(2), BND_MIB(2), BND_BND(2), BND_M128(2), M128_BND(2), MIB_BND(2);

	companion object {
		val start1 = R
		val start2 = R_R
		val start3 = R_R_I8
		val map = values().associateBy { it.name }
	}

}
