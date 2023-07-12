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



sealed interface OpKind



enum class NasmMultiOp(val string: String, val first: Op, val second: Op) : OpKind {
	RM8("rm8", Op.R8, Op.M8),
	RM16("rm16", Op.R16, Op.M16),
	RM32("rm32", Op.R32, Op.M32),
	RM64("rm64", Op.R64, Op.M64),
	MMM64("mmxrm64", Op.MM, Op.M64),
	XM8("xmmrm8", Op.X, Op.M8),
	XM16("xmmrm16", Op.X, Op.M16),
	XM32("xmmrm32", Op.X, Op.M32),
	XM64("xmmrm64", Op.X, Op.M64),
	XM128("xmmrm128", Op.X, Op.M128),
	XM256("xmmrm256", Op.X, Op.M256),
	YM16("ymmrm16", Op.Y, Op.M16),
	YM128("ymmrm128", Op.Y, Op.M128),
	YM256("ymmrm256", Op.Y, Op.M256),
	ZM16("zmmrm16", Op.Z, Op.M16),
	ZM128("zmmrm128", Op.Z, Op.M128),
	ZM512("zmmrm512", Op.Z, Op.M512),
	KM8("krm8", Op.K, Op.M8),
	KM16("krm16", Op.K, Op.M16),
	KM32("krm32", Op.K, Op.M32),
	KM64("krm64", Op.K, Op.M64);
}



enum class Op(val type: OpType, val width: Width?) : OpKind {

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