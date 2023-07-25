package eyre

import eyre.Width.*
import eyre.gen.OpEnc



data class SimdOps(
	val i8    : Boolean,
	val op1   : Op,
	val op2   : Op,
	val op3   : Op,
	val op4   : Op,
	val width : Width?,
	val vsib  : Int
) {
	fun equalsExceptMem(other: SimdOps) =
		i8 == other.i8 &&
		op1 == other.op1 &&
		op2 == other.op2 &&
		op3 == other.op3 &&
		op4 == other.op4 &&
		vsib == other.vsib
}


/*
@JvmInline
value class SimdOps(val value: Int) {

	constructor(r1: Int, r2: Int, r3: Int, r4: Int, size: Int) : this(
		(r1 shl R1) or
		(r2 shl R2) or
		(r3 shl R3) or
		(r4 shl R4) or
		(size shl SIZE)
	)

	constructor(
		r1    : Int,
		r2    : Int,
		r3    : Int,
		r4    : Int,
		size  : Int,
		mem   : Int,
		width : Int,
		vsib  : Int,
	) : this(
		(r1 shl R1) or
		(r2 shl R2) or
		(r3 shl R3) or
		(r4 shl R4) or
		(size shl SIZE) or
		(mem shl MEM) or
		(width shl WIDTH) or
		(vsib shl VSIB)
	)

	val r1    get() = ((value shr R1) and 15).let(RegType.entries::get)
	val r2    get() = ((value shr R2) and 15).let(RegType.entries::get)
	val r3    get() = ((value shr R3) and 15).let(RegType.entries::get)
	val r4    get() = ((value shr R4) and 15).let(RegType.entries::get)
	val size  get() = ((value shr SIZE) and 3)
	val mem   get() = ((value shr MEM) and 3)
	val width get() = ((value shr WIDTH) and 7).let { if(it == 0) null else Width.entries[it - 1] }
	val vsib  get() = ((value shr VSIB) and 3)

	companion object {
		const val R1    = 0
		const val R2    = 4
		const val R3    = 8
		const val R4    = 12
		const val SIZE  = 16 // 2: 1, 2, 3, 4
		const val MEM   = 18 // 2: 0, 1, 2, 3 (0 for none, can't be fourth operand)
		const val WIDTH = 20 // 4: NONE, BYTE, WORD, DWORD, QWORD, TWORD, XWORD, YWORD, ZWORD
		const val VSIB  = 23 // 2: NONE, X, Y, Z
	}

}*/



data class AutoOps(
	val r1    : RegType,
	val r2    : RegType,
	val r3    : RegType,
	val r4    : RegType,
	val size  : Int,
	val mem   : Int,
	val width : Width?,
	val vsib  : Int,
) {
	fun equalExceptMemWidth(other: AutoOps) =
		r1 == other.r1 &&
		r2 == other.r2 &&
		r3 == other.r3 &&
		r4 == other.r4 &&
		size == other.size &&
		mem == other.mem &&
		vsib == other.vsib
}



enum class SimdOpEnc(vararg val encs: OpEnc) {
	R(OpEnc.R, OpEnc.RI),
	M(OpEnc.M, OpEnc.MI),
	RM(OpEnc.RM, OpEnc.RMV, OpEnc.RMI, OpEnc.RMVI),
	RV(OpEnc.RVM, OpEnc.RVMI, OpEnc.RVMS),
	MR(OpEnc.MR, OpEnc.MRI, OpEnc.MRN, OpEnc.MRV),
	MV(OpEnc.MVR),
	VM(OpEnc.VM, OpEnc.VMI);
}



enum class OpType(val isReg: Boolean = false) {
	R(true),
	M,
	I,
	A(true),
	C(true),
	ST(true),
	S(true),
	K(true),
	T(true),
	MM(true),
	MISC,
	REL,
	VM,
	MOFFS,
	MULTI;

	val isMem get() = this == M || this == VM
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
	MM("mmxreg", OpType.MM, QWORD),
	X("xmmreg", OpType.S, XWORD),
	Y("ymmreg", OpType.S, YWORD),
	Z("zmmreg", OpType.S, ZWORD),
	VM32X("xmem32", OpType.VM, XWORD),
	VM64X("xmem64", OpType.VM, XWORD),
	VM32Y("ymem32", OpType.VM, YWORD),
	VM64Y("ymem64", OpType.VM, YWORD),
	VM32Z("zmem32", OpType.VM, ZWORD),
	VM64Z("zmem64", OpType.VM, ZWORD),
	K("kreg", OpType.K, null),
	BND("bndreg", OpType.MISC, null),
	T("tmmreg", OpType.T, null),
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