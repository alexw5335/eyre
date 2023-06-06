package eyre.encoding

import eyre.Width



enum class NasmOps(vararg val parts: NasmOpClass) {
	NONE,

	MEM(NasmOp.MEM),
	A(NasmOpType.A),
	R(NasmOpType.R),
	M(NasmOpType.M),
	I(NasmOpType.I),
	ST(NasmOpType.ST),
	REL(NasmOpType.REL),

	R_R(NasmOpType.R, NasmOpType.R),
	R_M(NasmOpType.R, NasmOpType.M),
	M_R(NasmOpType.M, NasmOpType.R),
	R_I(NasmOpType.R, NasmOpType.I),
	A_I(NasmOpType.A, NasmOpType.I),
	M_I(NasmOpType.M, NasmOpType.I),
	R_I8(NasmOpType.R, NasmOp.I8),
	M_I8(NasmOpType.M, NasmOp.I8),
	R_1(NasmOpType.R, NasmOp.ONE),
	M_1(NasmOpType.M, NasmOp.ONE),
	ST_ST0(NasmOp.ST, NasmOp.ST0),
	ST0_ST(NasmOp.ST0, NasmOp.ST),
	R_CL(NasmOpType.R, NasmOp.CL),
	M_CL(NasmOpType.M, NasmOp.CL),

	R_R_I8(NasmOpType.R, NasmOpType.R, NasmOp.I8),
	R_M_I8(NasmOpType.R, NasmOpType.M, NasmOp.I8),
	M_R_I8(NasmOpType.M, NasmOpType.R, NasmOp.I8),
	R_R_CL(NasmOpType.R, NasmOpType.R, NasmOp.CL),
	M_R_CL(NasmOpType.M, NasmOpType.R, NasmOp.CL),
	R_R_I(NasmOpType.R, NasmOpType.R, NasmOpType.I),
	R_M_I(NasmOpType.R, NasmOpType.M, NasmOpType.I),
	M_R_I(NasmOpType.M, NasmOpType.R, NasmOpType.I),

	S_M32_I8(NasmOpType.S, NasmOp.M32, NasmOp.I8),
	S_S_S_I8(NasmOpType.S, NasmOpType.S, NasmOpType.S, NasmOp.I8),
	S_S_M64_I8(NasmOpType.S, NasmOpType.S, NasmOp.M64, NasmOp.I8),
	K_X_X_I8(NasmOpType.K, NasmOpType.S, NasmOpType.S, NasmOp.I8),

	R64_M128(NasmOp.R64, NasmOp.M128),
	MM_R(NasmOp.MM, NasmOpType.R),
	MM_M(NasmOp.MM, NasmOpType.M),
	R_MM(NasmOpType.R, NasmOp.MM),
	M_MM(NasmOpType.M, NasmOp.MM),
	MM_MM(NasmOp.MM, NasmOp.MM),
	MM_I8(NasmOp.MM, NasmOp.I8),
	S_S(NasmOpType.S, NasmOpType.S),
	R_X(NasmOpType.R, NasmOp.X),

	MM_R_I8(NasmOp.MM, NasmOpType.R, NasmOp.I8),
	MM_M_I8(NasmOp.MM, NasmOpType.M, NasmOp.I8),

	VM32S_S(NasmOpType.VM32S, NasmOpType.S),
	VM64S_S(NasmOpType.VM64S, NasmOpType.S),
	S_VM32S(NasmOpType.S, NasmOpType.VM32S),
	S_VM64S(NasmOpType.S, NasmOpType.VM64S),
	R_R_R(NasmOpType.R, NasmOpType.R, NasmOpType.R),
	R_R_M(NasmOpType.R, NasmOpType.R, NasmOpType.M),
	R_M_R(NasmOpType.R, NasmOpType.M, NasmOpType.R),
	S_S_S_S(NasmOpType.S, NasmOpType.S, NasmOpType.S, NasmOpType.S),
	S_S_M32(NasmOpType.S, NasmOpType.S, NasmOp.M32),
	S_S_S(NasmOpType.S, NasmOpType.S, NasmOpType.S),

	S_M(NasmOpType.S, NasmOpType.M),
	M_S(NasmOpType.M, NasmOpType.S),

	S_M16(NasmOpType.S, NasmOp.M16),
	S_M32(NasmOpType.S, NasmOp.M32),
	S_M64(NasmOpType.S, NasmOp.M64),
	S_M128(NasmOpType.S, NasmOp.M128),

	X_R(NasmOp.X, NasmOpType.R),

	X_Y(NasmOp.X, NasmOp.Y),
	Y_X(NasmOp.Y, NasmOp.X),
	Y_Z(NasmOp.Y, NasmOp.Z),
	Z_Y(NasmOp.Z, NasmOp.Y),
	Z_X(NasmOp.Z, NasmOp.X),
	X_Z(NasmOp.X, NasmOp.Z),
	K_S(NasmOp.K, NasmOpType.S),
	K_K(NasmOp.K, NasmOp.K),
	K_K_K(NasmOp.K, NasmOp.K, NasmOp.K),

	K_S_S(NasmOp.K, NasmOpType.S, NasmOpType.S),
	K_S_M(NasmOp.K, NasmOpType.S, NasmOpType.M),

	T(NasmOp.T),
	T_MEM(NasmOp.T, NasmOp.MEM),
	MEM_T(NasmOp.MEM, NasmOp.T),
	T_T_T(NasmOp.T, NasmOp.T, NasmOp.T),

	// Custom
	A_I8(NasmOpType.A, NasmOp.I8),
	I8_A(NasmOp.I8, NasmOpType.A),
	A_DX(NasmOpType.A, NasmOp.DX),
	DX_A(NasmOp.DX, NasmOpType.A),
	I16_I8(NasmOp.I16, NasmOp.I8),
	REL8_ECX(NasmOp.REL8, NasmOp.ECX),
	REL8_RCX(NasmOp.REL8, NasmOp.RCX);

	companion object { val values = values() }
}



sealed interface NasmOpClass



enum class NasmOpType : NasmOpClass {
	R,
	M,
	I,
	A,
	C,
	D,
	S,
	ST,
	K,
	MM,
	REL,
	COMPOUND,
	ONE,
	MISC,
	VM32S,
	VM64S,
	MOFFS;
}



enum class OpType {
	R,M,I,A,C,D,ST,MM,X,Y,Z,K,REL,ONE,VM,MOFFS;
}



enum class Op(val type: OpType, val width: Width?) {
	R8(OpType.R, Width.BYTE),
	R16(OpType.R, Width.WORD),
	R32(OpType.R, Width.DWORD),
	R64(OpType.R, Width.QWORD),
	MEM(OpType.M, null),
	M8(OpType.M, Width.BYTE),
	M16(OpType.M, Width.DWORD)
}



enum class NasmOp(
	val type         : NasmOpType,
	val string       : String?,
	val width        : Width? = null,
	vararg val parts : NasmOp
) : NasmOpClass {

	R8(NasmOpType.R, "reg8", Width.BYTE),
	R16(NasmOpType.R, "reg16", Width.WORD),
	R32(NasmOpType.R, "reg32", Width.DWORD),
	R64(NasmOpType.R, "reg64", Width.QWORD),
	MEM(NasmOpType.M, null, null),
	M8(NasmOpType.M, "mem8", Width.BYTE),
	M16(NasmOpType.M, "mem16", Width.WORD),
	M32(NasmOpType.M, "mem32", Width.DWORD),
	M64(NasmOpType.M, "mem64", Width.QWORD),
	M80(NasmOpType.M, "mem80", Width.TWORD),
	M128(NasmOpType.M, "mem128", Width.XWORD),
	M256(NasmOpType.M, "mem256", Width.YWORD),
	M512(NasmOpType.M, "mem512", Width.ZWORD),
	I8(NasmOpType.I, "imm8", Width.BYTE),
	I16(NasmOpType.I, "imm16", Width.WORD),
	I32(NasmOpType.I, "imm32", Width.DWORD),
	I64(NasmOpType.I, "imm64", Width.QWORD),
	AL(NasmOpType.A, "reg_al", Width.BYTE),
	AX(NasmOpType.A, "reg_ax", Width.WORD),
	EAX(NasmOpType.A, "reg_eax", Width.DWORD),
	RAX(NasmOpType.A, "reg_rax", Width.QWORD),
	DX(NasmOpType.D, "reg_dx", Width.WORD),
	CL(NasmOpType.C, "reg_cl", Width.BYTE),
	ECX(NasmOpType.C, "reg_ecx", Width.DWORD),
	RCX(NasmOpType.C, "reg_rcx", Width.QWORD),
	ONE(NasmOpType.ONE, "unity"),
	REL8(NasmOpType.REL, null, Width.BYTE),
	REL16(NasmOpType.REL, null, Width.WORD),
	REL32(NasmOpType.REL, null, Width.DWORD),
	ST(NasmOpType.ST, "fpureg", Width.TWORD),
	ST0(NasmOpType.ST, "fpu0", Width.TWORD),
	MM(NasmOpType.MM, "mmxreg", Width.QWORD),
	X(NasmOpType.S, "xmmreg", Width.XWORD),
	Y(NasmOpType.S, "ymmreg", Width.YWORD),
	Z(NasmOpType.S, "zmmreg", Width.ZWORD),
	VM32X(NasmOpType.VM32S, "xmem32", Width.XWORD),
	VM64X(NasmOpType.VM64S, "xmem64", Width.XWORD),
	VM32Y(NasmOpType.VM32S, "ymem32", Width.YWORD),
	VM64Y(NasmOpType.VM64S, "ymem64", Width.YWORD),
	VM32Z(NasmOpType.VM32S, "zmem32", Width.ZWORD),
	VM64Z(NasmOpType.VM64S, "zmem64", Width.ZWORD),
	K(NasmOpType.K, "kreg"),
	BND(NasmOpType.MISC, "bndreg"),
	T(NasmOpType.MISC, "tmmreg"),
	SREG(NasmOpType.MISC, "reg_sreg"),
	MOFFS8(NasmOpType.MOFFS, null, Width.BYTE),
	MOFFS16(NasmOpType.MOFFS, null, Width.WORD),
	MOFFS32(NasmOpType.MOFFS, null, Width.DWORD),
	MOFFS64(NasmOpType.MOFFS, null, Width.QWORD),
	CR(NasmOpType.MISC, "reg_creg", Width.QWORD),
	DR(NasmOpType.MISC, "reg_dreg", Width.QWORD),
	FS(NasmOpType.MISC, "reg_fs"),
	GS(NasmOpType.MISC, "reg_gs"),

	RM8(NasmOpType.COMPOUND, "rm8", Width.BYTE, R8, M8),
	RM16(NasmOpType.COMPOUND, "rm16", Width.WORD, R16, M16),
	RM32(NasmOpType.COMPOUND, "rm32", Width.DWORD, R32, M32),
	RM64(NasmOpType.COMPOUND, "rm64", Width.QWORD, R64, M64),
	MMM64(NasmOpType.COMPOUND, "mmxrm64", Width.QWORD, MM, M64),
	XM8(NasmOpType.COMPOUND, "xmmrm8", null, X, M8),
	XM16(NasmOpType.COMPOUND, "xmmrm16", null, X, M16),
	XM32(NasmOpType.COMPOUND, "xmmrm32", null, X, M32),
	XM64(NasmOpType.COMPOUND, "xmmrm64", null, X, M64),
	XM128(NasmOpType.COMPOUND, "xmmrm128", null, X, M128),
	XM256(NasmOpType.COMPOUND, "xmmrm256", null, X, M256),
	YM16(NasmOpType.COMPOUND, "ymmrm16", null, Y, M16),
	YM128(NasmOpType.COMPOUND, "ymmrm128", null, Y, M128),
	YM256(NasmOpType.COMPOUND, "ymmrm256", null, Y, M256),
	ZM16(NasmOpType.COMPOUND, "zmmrm16", null, Z, M16),
	ZM128(NasmOpType.COMPOUND, "zmmrm128", null, Z, M128),
	ZM512(NasmOpType.COMPOUND, "zmmrm512", null, Z, M512),
	KM8(NasmOpType.COMPOUND, "krm8", null, K, M8),
	KM16(NasmOpType.COMPOUND, "krm16", null, K, M16),
	KM32(NasmOpType.COMPOUND, "krm32", null, K, M32),
	KM64(NasmOpType.COMPOUND, "krm64", null, K, M64);

}