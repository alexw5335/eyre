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
	MOFFS,
	MULTI;
}



sealed interface OpKind



enum class Op(
	val nasmString: String?,
	val type: OpType,
	val width: Width?,
	val multi1: Op? = null,
	val multi2: Op? = null
) : OpKind {

	NONE(null, OpType.MISC, null),
	R8("reg8", OpType.R, BYTE),
	R16("reg16", OpType.R, WORD),
	R32("reg32", OpType.R, DWORD),
	R64("reg64", OpType.R, QWORD),
	MEM(null, OpType.M, null),
	M8("mem8", OpType.M, BYTE),
	M16("mem16", OpType.M, WORD),
	M32("mem32", OpType.M, DWORD),
	M64("mem64", OpType.M, QWORD),
	M80("mem80", OpType.M, TWORD),
	M128("mem128", OpType.M, XWORD),
	M256("mem256", OpType.M, YWORD),
	M512("mem512", OpType.M, ZWORD),
	I8("imm8", OpType.I, BYTE),
	I16("imm16", OpType.I, WORD),
	I32("imm32", OpType.I, DWORD),
	I64("imm64", OpType.I, QWORD),
	AL("reg_al", OpType.A, BYTE),
	AX("reg_ax", OpType.A, WORD),
	EAX("reg_eax", OpType.A, DWORD),
	RAX("reg_rax", OpType.A, QWORD),
	CL("reg_cl", OpType.C, BYTE),
	ECX("reg_ecx", OpType.C, DWORD),
	RCX("reg_rcx", OpType.C, QWORD),
	DX("reg_dx", OpType.MISC, WORD),
	REL8(null, OpType.REL, BYTE),
	REL16(null, OpType.REL, WORD),
	REL32(null, OpType.REL, DWORD),
	ST("fpureg", OpType.ST, TWORD),
	ST0("fpu0", OpType.ST, TWORD),
	ONE("unity", OpType.MISC, null),
	MM("mmxreg", OpType.MISC, QWORD),
	X("xmmreg", OpType.MISC, XWORD),
	Y("ymmreg", OpType.MISC, YWORD),
	Z("zmmreg", OpType.MISC, ZWORD),
	VM32X("xmem32", OpType.VM, XWORD),
	VM64X("xmem64", OpType.VM, XWORD),
	VM32Y("ymem32", OpType.VM, YWORD),
	VM64Y("ymem64", OpType.VM, YWORD),
	VM32Z("zmem32", OpType.VM, ZWORD),
	VM64Z("zmem64", OpType.VM, ZWORD),
	K("kreg", OpType.MISC, null),
	BND("bndreg", OpType.MISC, null),
	T("tmmreg", OpType.MISC, null),
	MOFFS8(null, OpType.MOFFS, BYTE),
	MOFFS16(null, OpType.MOFFS, WORD),
	MOFFS32(null, OpType.MOFFS, DWORD),
	MOFFS64(null, OpType.MOFFS, QWORD),
	SEG("reg_sreg", OpType.MISC, null),
	CR("reg_creg", OpType.MISC, QWORD),
	DR("reg_dreg", OpType.MISC, QWORD),
	FS("reg_fs", OpType.MISC, null),
	GS("reg_gs", OpType.MISC, null),

	RM8  ("rm8",      R8,  M8),
	RM16 ("rm16",     R16, M16),
	RM32 ("rm32",     R32, M32),
	RM64 ("rm64",     R64, M64),
	MMM64("mmxrm64",  MM,  M64),
	XM8  ("xmmrm8",   X,   M8),
	XM16 ("xmmrm16",  X,   M16),
	XM32 ("xmmrm32",  X,   M32),
	XM64 ("xmmrm64",  X,   M64),
	XM128("xmmrm128", X,   M128),
	XM256("xmmrm256", X,   M256),
	YM16 ("ymmrm16",  Y,   M16),
	YM128("ymmrm128", Y,   M128),
	YM256("ymmrm256", Y,   M256),
	ZM16 ("zmmrm16",  Z,   M16),
	ZM128("zmmrm128", Z,   M128),
	ZM512("zmmrm512", Z,   M512),
	KM8  ("krm8",     K,   M8),
	KM16 ("krm16",    K,   M16),
	KM32 ("krm32",    K,   M32),
	KM64 ("krm64",    K,   M64);
	
	constructor(nasmName: String, op1: Op, op2: Op) : 
		this(nasmName, OpType.MULTI, null, op1, op2)

	val isMulti get() = type == OpType.MULTI
	
}