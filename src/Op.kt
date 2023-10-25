package eyre



enum class Ops(val first: Ops? = null, val second: Ops? = null) {
	NONE,
	R,
	M,
	I8,
	I16,
	I32,
	AX,
	REL8,
	REL32,
	FS,
	GS,
	R_R,
	R_M,
	M_R,
	RM_I,
	RM_I8,
	RM_ONE,
	A_I,
	RM_CL,
	A_R,
	R_A,
	R_RM_I,
	R_RM_I8,
	RM_R_I8,
	RM_R_CL,

	// Multi ops
	RM(R, M),
	RM_R(R_R, M_R),
	R_RM(R_R, R_M);
}



enum class Op(
	val type: OpType,
	val first: Op?,
	val second: Op?,
	val widths: Array<Op>?
) {
	NONE(OpType.NONE),

	R8(OpType.R8),
	R16(OpType.R16),
	R32(OpType.R32),
	R64(OpType.R64),
	M8(OpType.MEM),
	M16(OpType.MEM),
	M32(OpType.MEM),
	M64(OpType.MEM),
	M80(OpType.MEM),
	M128(OpType.MEM),
	M256(OpType.MEM),
	M512(OpType.MEM),
	MEM(OpType.MEM),
	MM(OpType.MM),
	X(OpType.X),
	Y(OpType.Y),
	DX(OpType.R16),
	CL(OpType.R8),
	AL(OpType.R8),
	AX(OpType.R16),
	EAX(OpType.R32),
	RAX(OpType.R64),
	I8(OpType.IMM),
	I16(OpType.IMM),
	I32(OpType.IMM),
	I64(OpType.IMM),
	REL8(OpType.IMM),
	REL32(OpType.IMM),
	FS(OpType.SEG),
	GS(OpType.SEG),
	SEG(OpType.SEG),
	CR(OpType.CR),
	DR(OpType.DR),
	ONE(OpType.IMM),
	ST(OpType.ST),
	ST0(OpType.ST),
	VM32X(OpType.MEM),
	VM64X(OpType.MEM),
	VM32Y(OpType.MEM),
	VM64Y(OpType.MEM),
	VM32Z(OpType.MEM),
	VM64Z(OpType.MEM),

	// Width multi ops
	A(arrayOf(AL, AX, EAX, RAX)),
	R(arrayOf(R8, R16, R32, R64)),
	M(arrayOf(M8, M16, M32, M64)),
	I(arrayOf(I8, I16, I32, I32)),

	// Multi ops
	RM(R, M),
	XM(X, M128),
	YM(Y, M256),
	MMM(MM, M64),
	RM8(R8, M8),
	RM16(R16, M16),
	RM32(R32, M32),
	RM64(R64, M64),
	MMM64(MM, M64),
	XM8(X, M8),
	XM16(X, M16),
	XM32(X, M32),
	XM64(X, M64),
	XM128(X, M128),
	YM8(Y, M8),
	YM16(Y, M16),
	YM32(Y, M32),
	YM64(Y, M64),
	YM128(Y, M128),
	YM256(Y, M256);
	
	constructor(first: Op, second: Op) : this(OpType.NONE, first, second, null)
	constructor(widths: Array<Op>) : this(OpType.NONE, null, null, widths)
	constructor(type: OpType) : this(type, null, null, null)

	val usesModRm get() = this in modrmOps
	val isAmbiguous get() = this in ambiguousOps

	companion object {
		val modrmOps = setOf(
			R8, R16, R32, R64,
			M8, M16, M32, M64,
			M80, M128, M256, M512,
			MEM, MM, X, Y,
			SEG, CR, DR, VM32X,
			VM64X, VM32Y, VM64Y, VM32Z,
			VM64Z, R, M, RM, XM,
			YM, MMM, RM8, RM16,
			RM32, RM64, MMM64, XM8,
			XM16, XM32, XM64, XM128,
			YM8, YM16, YM32, YM64,
			YM128, YM256
		)

		val ambiguousOps = setOf(
			DX, CL, AL, AX, EAX,
			RAX, I16, I32, I64,
			REL8, REL32, FS, GS,
			SEG, CR, DR, ONE, ST,
			ST0, A, I,
		)
	}

}



