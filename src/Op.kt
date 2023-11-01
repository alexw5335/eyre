package eyre

enum class Op(
	val type: OpType,
	val width: Width,
	val first: Op?,
	val second: Op?,
	val widths: Array<Op>?
) {
	NONE(OpType.NONE, Width.NONE),
	R8(OpType.R8, Width.BYTE),
	R16(OpType.R16, Width.WORD),
	R32(OpType.R32, Width.WORD),
	R64(OpType.R64, Width.WORD),
	MEM(OpType.MEM, Width.NONE),
	M8(OpType.MEM, Width.BYTE),
	M16(OpType.MEM, Width.WORD),
	M32(OpType.MEM, Width.DWORD),
	M64(OpType.MEM, Width.QWORD),
	M80(OpType.MEM, Width.TWORD),
	M128(OpType.MEM, Width.XWORD),
	M256(OpType.MEM, Width.YWORD),
	M512(OpType.MEM, Width.ZWORD),
	MM(OpType.MM, Width.QWORD),
	X(OpType.X, Width.XWORD),
	Y(OpType.Y, Width.YWORD),
	DX(OpType.R16, Width.WORD),
	CL(OpType.R8, Width.BYTE),
	AL(OpType.R8, Width.BYTE),
	AX(OpType.R16, Width.WORD),
	EAX(OpType.R32, Width.DWORD),
	RAX(OpType.R64, Width.QWORD),
	I8(OpType.IMM, Width.BYTE),
	I16(OpType.IMM, Width.WORD),
	I32(OpType.IMM, Width.DWORD),
	I64(OpType.IMM, Width.QWORD),
	REL8(OpType.IMM, Width.BYTE),
	REL32(OpType.IMM, Width.WORD),
	FS(OpType.SEG, Width.WORD),
	GS(OpType.SEG, Width.WORD),
	SEG(OpType.SEG, Width.WORD),
	CR(OpType.CR, Width.QWORD),
	DR(OpType.DR, Width.QWORD),
	ONE(OpType.IMM, Width.NONE),
	ST(OpType.ST, Width.NONE),
	ST0(OpType.ST, Width.NONE),
	VM32X(OpType.MEM, Width.DWORD),
	VM64X(OpType.MEM, Width.QWORD),
	VM32Y(OpType.MEM, Width.DWORD),
	VM64Y(OpType.MEM, Width.QWORD),
	VM32Z(OpType.MEM, Width.DWORD),
	VM64Z(OpType.MEM, Width.QWORD),

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
	
	constructor(first: Op, second: Op) : this(OpType.NONE, Width.NONE, first, second, null)
	constructor(widths: Array<Op>) : this(OpType.NONE, Width.NONE, null, null, widths)
	constructor(type: OpType, width: Width) : this(type, width, null, null, null)

	val isNone get() = this == NONE
	val isMem get() = this in memOps
	val isReg get() = this in regOps
	val isImm get() = this in immOps
	val usesModRm get() = this in modrmOps
	val isAmbiguous get() = this in ambiguousOps
	
	companion object {
		val map = entries.associateBy { it.name }

		val immOps = setOf(
			I8, I16, I32, I64, I, REL8, REL32, ONE
		)

		val regOps = setOf(
			R8, R16, R32, R64, MM, X, Y, DX, CL, AL, AX, EAX, RAX,
			FS, GS, SEG, CR, DR, ONE, ST, ST0, A, R, RM, XM, YM,
			MMM, RM8, RM16, RM32, RM64, MMM64, XM8, XM16, XM32,
			XM64, XM128, YM8, YM16, YM32, YM64, YM128, YM256,
		)
		
		val memOps = setOf(
			MEM, M8, M16, M32, M64, M80, M128, M256, M512, VM32X,
			VM64X, VM32Y, VM64Y, VM32Z, VM64Z, M, RM, XM, YM, MMM,
			RM8, RM16, RM32, RM64, MMM64, XM8, XM16, XM32, XM64,
			XM128, YM8, YM16, YM32, YM64, YM128, YM256,
		)

		val modrmOps = setOf(
			R8, R16, R32, R64, M8, M16, M32, M64, M80, M128, M256,
			M512, MEM, MM, X, Y, SEG, CR, DR, VM32X, VM64X, VM32Y,
			VM64Y, VM32Z, VM64Z, R, M, RM, XM, YM, MMM, RM8, RM16,
			RM32, RM64, MMM64, XM8, XM16, XM32, XM64, XM128, YM8, 
			YM16, YM32, YM64, YM128, YM256
		)

		val ambiguousOps = setOf(
			DX, CL, AL, AX, EAX, RAX, I16, I32, I64, REL8, REL32, 
			FS, GS, SEG, CR, DR, ONE, ST, ST0, A, I,
		)
	}

}



