package eyre.gen

import eyre.Width.*
import eyre.Width



enum class SseOp {
	NONE,
	X,
	MM,
	R8,
	R16,
	R32,
	R64,
	MEM,
	M8,
	M16,
	M32,
	M64,
	M128;

	val isM get() = ordinal > MEM.ordinal

}



enum class AvxOpEnc {
	NONE,
	M, R,
	RM, MR, VM,
	RVM, MVR, RMV,
	RVMS;
}



enum class AvxOp {
	NONE,
	X,
	Y,
	Z,
	R8,
	R16,
	R32,
	R64,
	I8,
	K,
	T,
	MEM,
	M8,
	M16,
	M32,
	M64,
	M128,
	M256,
	M512;

	val isM get() = ordinal > MEM.ordinal

}



enum class OpType {
	R,
	M,
	I,
	A,
	C,
	ST,
	MISC,
	REL,
	VM,
	MOFFS;
}



enum class Op(val type: OpType, val width: Width?) {

	NONE(OpType.MISC, null),

	R8(OpType.R, BYTE),
	R16(OpType.R, WORD),
	R32(OpType.R, DWORD),
	R64(OpType.R, QWORD),

	MEM(OpType.M, null),
	M8(OpType.M, BYTE),
	M16(OpType.M, WORD),
	M32(OpType.M, DWORD),
	M64(OpType.M, QWORD),
	M80(OpType.M, TWORD),
	M128(OpType.M, XWORD),
	M256(OpType.M, YWORD),
	M512(OpType.M, ZWORD),

	I8(OpType.I, BYTE),
	I16(OpType.I, WORD),
	I32(OpType.I, DWORD),
	I64(OpType.I, QWORD),

	AL(OpType.A, BYTE),
	AX(OpType.A, WORD),
	EAX(OpType.A, DWORD),
	RAX(OpType.A, QWORD),
	CL(OpType.C, BYTE),
	ECX(OpType.C, DWORD),
	RCX(OpType.C, QWORD),
	DX(OpType.MISC, WORD),

	REL8(OpType.REL, BYTE),
	REL16(OpType.REL, WORD),
	REL32(OpType.REL, DWORD),

	ST(OpType.ST, TWORD),
	ST0(OpType.ST, TWORD),

	ONE(OpType.MISC, null),

	MM(OpType.MISC, QWORD),

	X(OpType.MISC, XWORD),
	Y(OpType.MISC, YWORD),
	Z(OpType.MISC, ZWORD),

	VM32X(OpType.VM, XWORD),
	VM64X(OpType.VM, XWORD),
	VM32Y(OpType.VM, YWORD),
	VM64Y(OpType.VM, YWORD),
	VM32Z(OpType.VM, ZWORD),
	VM64Z(OpType.VM, ZWORD),

	K(OpType.MISC, null),

	BND(OpType.MISC, null),

	T(OpType.MISC, null),

	MOFFS8(OpType.MOFFS, BYTE),
	MOFFS16(OpType.MOFFS, WORD),
	MOFFS32(OpType.MOFFS, DWORD),
	MOFFS64(OpType.MOFFS, QWORD),

	SEG(OpType.MISC, null),

	CR(OpType.MISC, QWORD),
	DR(OpType.MISC, QWORD),

	FS(OpType.MISC, null),
	GS(OpType.MISC, null),
	
}