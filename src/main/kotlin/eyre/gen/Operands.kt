package eyre.gen

import eyre.Reg
import eyre.RegType
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
	I8,
	MEM,
	M8,
	M16,
	M32,
	M64,
	M128;

	val isM get() = ordinal > MEM.ordinal

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



data class TempOps2(val r1: Reg, val r2: Reg, val r3: Reg, val r4: Reg, val memIndex: Int, val vsib: Int) {
	fun equalsExceptMem(other: TempOps2) = r1 == other.r1 && r2 == other.r2 && r3 == other.r3 && r4 == other.r4
}



data class TempOps(
	val op1   : TempOp,
	val op2   : TempOp,
	val op3   : TempOp,
	val op4   : TempOp,
	val width : Width?,
	val vsib  : Int
) {
	fun equalsExceptMem(other: TempOps) =
		op1 == other.op1 &&
		op2 == other.op2 &&
		op3 == other.op3 &&
		op4 == other.op4 &&
		vsib == other.vsib
	fun equalsExceptVsib(other: TempOps) =
		op1 == other.op1 &&
		op2 == other.op2 &&
		op3 == other.op3 &&
		op4 == other.op4
}



enum class TempOp(val op: Op?) {
	NONE(Op.NONE),
	R8(Op.R8),
	R16(Op.R16),
	R32(Op.R32),
	R64(Op.R64),
	MEM(null),
	I8(Op.I8),
	K(Op.K),
	T(Op.T),
	X(Op.X),
	Y(Op.Y),
	Z(Op.Z),
	MM(Op.MM);
	companion object {
		fun from(op: Op?) = when {
			op == null -> NONE
			op.type.isMem -> MEM
			else -> entries.firstOrNull { it.op == op } ?: NONE
		}
	}
}



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