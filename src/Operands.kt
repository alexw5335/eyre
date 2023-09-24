package eyre

import eyre.Width.*
import eyre.gen.NasmOpEnc



@JvmInline
value class AutoOps(val value: Int) {

	constructor(r1: Int, r2: Int, r3: Int, r4: Int, width: Int, vsib: Int) : this(
		(r1 shl R1) or (r2 shl R2) or (r3 shl R3) or
		(r4 shl R4) or (width shl WIDTH) or (vsib shl VSIB)
	)

	val r1    get() = ((value shr R1) and 15)
	val r2    get() = ((value shr R2) and 15)
	val r3    get() = ((value shr R3) and 15)
	val r4    get() = ((value shr R4) and 15)
	val width get() = ((value shr WIDTH) and 7)
	val vsib  get() = ((value shr VSIB) and 3)

	fun equalsExceptWidth(other: AutoOps) =
		value and WIDTH_MASK == other.value and WIDTH_MASK

	companion object {
		const val R1    = 0
		const val R2    = 4
		const val R3    = 8
		const val R4    = 12
		const val WIDTH = 16 // 4: NONE, BYTE, WORD, DWORD, QWORD, TWORD, XWORD, YWORD, ZWORD
		const val VSIB  = 20 // 2: NONE, X, Y, Z
		const val WIDTH_MASK = -1 xor (15 shl WIDTH)
	}

	override fun toString() =
		"(${OpType.entries[r1]}, ${OpType.entries[r2]}, ${OpType.entries[r3]}," +
			" ${OpType.entries[r4]}, width=$width, vsib=$vsib)"

}



enum class OpEnc(vararg val encs: NasmOpEnc) {
	RMV(NasmOpEnc.R, NasmOpEnc.RI, NasmOpEnc.RM, NasmOpEnc.RMV, NasmOpEnc.RMI, NasmOpEnc.RMVI),
	RVM(NasmOpEnc.RVM, NasmOpEnc.RVMI, NasmOpEnc.RVMS),
	MRV(NasmOpEnc.M, NasmOpEnc.MI, NasmOpEnc.MR, NasmOpEnc.MRI, NasmOpEnc.MRN, NasmOpEnc.MRV),
	MVR(NasmOpEnc.MVR),
	VMR(NasmOpEnc.VM, NasmOpEnc.VMI);
}



enum class NasmOpType(val isReg: Boolean = false) {
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
	SEG,
	MOFFS,
	MULTI;
	val isMem get() = this == M || this == VM
}



enum class NasmOp(
	val nasmString : String?,
	val type       : NasmOpType,
	val width      : Width?,
	val multi1     : NasmOp? = null,
	val multi2     : NasmOp? = null
) {

	NONE(null, NasmOpType.MISC, null),
	R8("reg8", NasmOpType.R, BYTE),
	R16("reg16", NasmOpType.R, WORD),
	R32("reg32", NasmOpType.R, DWORD),
	R64("reg64", NasmOpType.R, QWORD),
	MEM(null, NasmOpType.M, null),
	M8("mem8", NasmOpType.M, BYTE),
	M16("mem16", NasmOpType.M, WORD),
	M32("mem32", NasmOpType.M, DWORD),
	M64("mem64", NasmOpType.M, QWORD),
	M80("mem80", NasmOpType.M, TWORD),
	M128("mem128", NasmOpType.M, XWORD),
	M256("mem256", NasmOpType.M, YWORD),
	M512("mem512", NasmOpType.M, ZWORD),
	I8("imm8", NasmOpType.I, BYTE),
	I16("imm16", NasmOpType.I, WORD),
	I32("imm32", NasmOpType.I, DWORD),
	I64("imm64", NasmOpType.I, QWORD),
	AL("reg_al", NasmOpType.A, BYTE),
	AX("reg_ax", NasmOpType.A, WORD),
	EAX("reg_eax", NasmOpType.A, DWORD),
	RAX("reg_rax", NasmOpType.A, QWORD),
	CL("reg_cl", NasmOpType.C, BYTE),
	ECX("reg_ecx", NasmOpType.C, DWORD),
	RCX("reg_rcx", NasmOpType.C, QWORD),
	DX("reg_dx", NasmOpType.MISC, WORD),
	REL8(null, NasmOpType.REL, BYTE),
	REL16(null, NasmOpType.REL, WORD),
	REL32(null, NasmOpType.REL, DWORD),
	ST("fpureg", NasmOpType.ST, TWORD),
	ST0("fpu0", NasmOpType.ST, TWORD),
	ONE("unity", NasmOpType.MISC, null),
	MM("mmxreg", NasmOpType.MM, QWORD),
	X("xmmreg", NasmOpType.S, XWORD),
	Y("ymmreg", NasmOpType.S, YWORD),
	Z("zmmreg", NasmOpType.S, ZWORD),
	VM32X("xmem32", NasmOpType.VM, DWORD),
	VM64X("xmem64", NasmOpType.VM, QWORD),
	VM32Y("ymem32", NasmOpType.VM, DWORD),
	VM64Y("ymem64", NasmOpType.VM, QWORD),
	VM32Z("zmem32", NasmOpType.VM, DWORD),
	VM64Z("zmem64", NasmOpType.VM, QWORD),
	K("kreg", NasmOpType.K, null),
	BND("bndreg", NasmOpType.MISC, null),
	T("tmmreg", NasmOpType.T, null),
	MOFFS8(null, NasmOpType.MOFFS, BYTE),
	MOFFS16(null, NasmOpType.MOFFS, WORD),
	MOFFS32(null, NasmOpType.MOFFS, DWORD),
	MOFFS64(null, NasmOpType.MOFFS, QWORD),
	SEG("reg_sreg", NasmOpType.SEG, null),
	CR("reg_creg", NasmOpType.MISC, QWORD),
	DR("reg_dreg", NasmOpType.MISC, QWORD),
	FS("reg_fs", NasmOpType.MISC, null),
	GS("reg_gs", NasmOpType.MISC, null),

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
	
	constructor(nasmName: String, op1: NasmOp, op2: NasmOp) :
		this(nasmName, NasmOpType.MULTI, null, op1, op2)

	val isMulti get() = type == NasmOpType.MULTI

}