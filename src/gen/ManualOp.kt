package eyre.gen

import eyre.OpType

enum class ManualOp(
	val type: OpType,
	val first: ManualOp?,
	val second: ManualOp?,
	val widths: Array<ManualOp>?
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
	
	constructor(first: ManualOp, second: ManualOp) : this(OpType.NONE, first, second, null)
	constructor(widths: Array<ManualOp>) : this(OpType.NONE, null, null, widths)
	constructor(type: OpType) : this(type, null, null, null)

}



val ambiguousOps = setOf(
	ManualOp.DX,
	ManualOp.CL,
	ManualOp.AL,
	ManualOp.AX,
	ManualOp.EAX,
	ManualOp.RAX,
	ManualOp.I16,
	ManualOp.I32,
	ManualOp.I64,
	ManualOp.REL8,
	ManualOp.REL32,
	ManualOp.FS,
	ManualOp.GS,
	ManualOp.SEG,
	ManualOp.CR,
	ManualOp.DR,
	ManualOp.ONE,
	ManualOp.ST,
	ManualOp.ST0,
	ManualOp.A,
	ManualOp.I,
)