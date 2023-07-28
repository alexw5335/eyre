package eyre

import eyre.Width.*
import eyre.gen.OpEnc




@JvmInline
value class SimdOps(val value: Int) {

	constructor(i8: Int, r1: Int, r2: Int, r3: Int, r4: Int) : this(
		(r1 shl R1) or
		(r2 shl R2) or
		(r3 shl R3) or
		(r4 shl R4) or
		(i8 shl I8)
	)

	constructor(
		i8    : Int,
		r1    : Int,
		r2    : Int,
		r3    : Int,
		r4    : Int,
		width : Int,
		mem   : Int,
		vsib  : Int,
	) : this(
		(r1 shl R1) or
		(r2 shl R2) or
		(r3 shl R3) or
		(r4 shl R4) or
		(mem shl MEM) or
		(width shl WIDTH) or
		(vsib shl VSIB) or
		(i8 shl I8)
	)

	val r1    get() = ((value shr R1) and 15).let(RegType.entries::get)
	val r2    get() = ((value shr R2) and 15).let(RegType.entries::get)
	val r3    get() = ((value shr R3) and 15).let(RegType.entries::get)
	val r4    get() = ((value shr R4) and 15).let(RegType.entries::get)
	val width get()  = ((value shr WIDTH) and 7)
	//val width get() = ((value shr WIDTH) and 7).let { if(it == 0) null else Width.entries[it - 1] }
	val mem   get() = ((value shr MEM) and 3)
	val vsib  get() = ((value shr VSIB) and 3)
	val i8    get() = ((value shr I8) and 1)

	fun equalsExceptWidth(other: SimdOps) =
		(value and 0b1111_11110000_11111111_11111111) ==
		(other.value and 0b1111_11110000_11111111_11111111)

	companion object {
		const val R1    = 0
		const val R2    = 4
		const val R3    = 8
		const val R4    = 12
		const val WIDTH = 16 // 4: NONE, BYTE, WORD, DWORD, QWORD, TWORD, XWORD, YWORD, ZWORD
		const val MEM   = 20 // 2: 0, 1, 2, 3 (0 for none, can't be fourth operand)
		const val VSIB  = 22 // 2: NONE, X, Y, Z
		const val I8    = 23 // 1: 0, 1
	}

	override fun toString() = buildString {
		append("($r1, $r2, $r3, $r4, width=$width, mem=$mem, vsib=$vsib, i8=$i8)")
	}

}





enum class SimdOpEnc(vararg val encs: OpEnc) {
	RMV(OpEnc.R, OpEnc.RI, OpEnc.RM, OpEnc.RMV, OpEnc.RMI, OpEnc.RMVI),
	RVM(OpEnc.RVM, OpEnc.RVMI, OpEnc.RVMS),
	MRV(OpEnc.M, OpEnc.MI, OpEnc.MR, OpEnc.MRI, OpEnc.MRN, OpEnc.MRV),
	MVR(OpEnc.MVR),
	VMR(OpEnc.VM, OpEnc.VMI);
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
	VM32X("xmem32", OpType.VM, DWORD),
	VM64X("xmem64", OpType.VM, QWORD),
	VM32Y("ymem32", OpType.VM, DWORD),
	VM64Y("ymem64", OpType.VM, QWORD),
	VM32Z("zmem32", OpType.VM, DWORD),
	VM64Z("zmem64", OpType.VM, QWORD),
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