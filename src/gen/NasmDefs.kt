package eyre.gen

import eyre.Width

enum class NasmArch {
	NONE,
	_8086,
	_186,
	_286,
	_386,
	_486,
	PENT,
	P6,
	KATMAI,
	WILLAMETTE,
	PRESCOTT,
	X86_64,
	NEHALEM,
	WESTMERE,
	SANDYBRIDGE,
	FUTURE,
	IA64;
}



enum class NasmExt {
	// 0, 32-bit only
	AES,
	// 1, tmmreg
	AMXBF16,
	// 4, tmmreg
	AMXINT8,
	// 7, tmmreg or mem
	AMXTILE,
	// 703, xmm, ymm
	AVX,
	// 186, xmm, ymm, sib
	AVX2,
	// 1494
	AVX512,
	// 4, rs4
	AVX5124FMAPS,
	// 2, rs4
	AVX5124VNNIW,
	// 12, k
	AVX512BF16,
	// 3, zmm, k
	AVX512BITALG,
	// 412
	AVX512BW,
	// 18
	AVX512CD,
	// 132
	AVX512DQ,
	// 10
	AVX512ER,
	// 2, X_XM64 Y_XM128
	AVX512FC16,
	// 111
	AVX512FP16,
	// 6
	AVX512IFMA,
	// 16
	AVX512PF,
	// 12
	AVX512VBMI,
	// 18
	AVX512VBMI2,
	// 193
	AVX512VL,
	// 4, Z_Z_ZM
	AVX512VNNI,
	// 2, Z_ZM
	AVX512VPOPCNTDQ,
	// 4 (LATEVEX)
	AVXIFMA,
	// 14 (LATEEVEX)
	AVXNECONVERT,
	// 12 (LATEVEX)
	AVXVNNIINT8,
	// 13, GP, R_R_RM, R_RM_R, R_RM, VEX
	BMI1,
	// 16, GP, R_RM_R, R_R_RM, R_RM_I8, VEX
	BMI2,
	// 14, GP
	CET,
	// 1, CMPccXADD
	CMPCCXADD,
	// 4, R32_M512, R64_M512
	ENQCMD,
	// 192, x, y, xm, ym
	FMA,
	// 213
	FPU,
	// 18, but combined with other extensions
	GFNI,
	// 1, I8_EAX
	HRESET,
	// 1, R64_M128
	INVPCID,
	// 95
	MMX,
	// 16, BND
	MPX,
	// 2, void (Not in Intel Manual)
	MSRLIST,
	// 1, void
	PCONFIG,
	// 2, mem8
	PREFETCHI,
	// 1, mem8
	PREFETCHWT1,
	// 6, M_R (AADD, AAND, AXOR) (Not in Intel Manual)
	RAOINT,
	// 6: imm, void
	RTM,
	// 1, void
	SERIALIZE,
	// 3, void
	SGX,
	// 7, xmm
	SHA,
	// 84, xmm
	SSE,
	// 178, xmm
	SSE2,
	// 10, xmm
	SSE3,
	// 56, xmm
	SSE41,
	// 10, xmm, CRC32
	SSE42,
	// 0, AMD-specific
	SSE4A,
	// 0, AMD-specific
	SSE5,
	// 16, xmm
	SSSE3,
	// 2, void
	TSXLDTRK,
	// 5, void, R64
	UINTR,
	// 16, zmm, ymm, xmm
	VAES,
	// 13, GP, void, mem, R_RM, R64_MEM
	VMX,
	// 20, zmm, ymm, xmm,
	VPCLMULQDQ,
	// 1, void
	WBNOINVD,
	// 1, void (Not in Intel Manual)
	WRMSRNS,
	// 0, obsolete
	_3DNOW,
	// 108, void, R, M_R, R_M512, K_K, K_K_K, mem, etc., many unique encodings
	NOT_GIVEN;

}



enum class NasmOpEnc(val string: String?) {
	NONE(null),
	IJ("ij"),
	N("-"),
	NI("-i"),
	RN("r-"),
	NR("-r"),
	IN("i-"),
	MN("m-"),
	NN("--"),
	RI("ri"),
	MRN("mr-"),
	MR("mr"),
	RM("rm"),
	MI("mi"),
	M("m"),
	R("r"),
	RMI("rmi"),
	I("i"),
	MRI("mri"),
	RVM("rvm"),
	RVMI("rvmi"),
	RVMS("rvms"),
	MVR("mvr"),
	VMI("vmi"),
	RMV("rmv"),
	VM("vm"),
	RMX("rmx"),
	MXR("mxr"),
	MRX("mrx"),
	MRV("mrv"),
	RMVI("rmvi");
}



enum class NasmImm {
	NONE,
	IB,
	IW,
	ID,
	IQ,
	IB_S,
	IB_U,
	ID_S,
	REL,
	REL8;
}



enum class NasmTuple {
	FV,
	T1S,
	T2,
	T4,
	T8,
	HV,
	HVM,
	T1F64,
	T1F32,
	FVM,
	DUP,
	T1S8,
	T1S16,
	QVM,
	OVM,
	M128;
}



enum class NasmVsib {
	VM32X,
	VM64X,
	VM64Y,
	VM32Y,
	VSIBX,
	VSIBY,
	VSIBZ;
}



enum class NasmVexL(val value: Int) {
	LIG(0),
	L0(0),
	LZ(0),
	L1(1),
	L128(0),
	L256(1),
	L512(2);
}



enum class NasmVexW(val value: Int) {
	WIG(0),
	W0(0),
	W1(1);
}



enum class NasmOpType {
	R,
	M,
	I,
	A,
	C,
	ST,
	X,
	Y,
	Z,
	K,
	T,
	MM,
	MISC,
	REL,
	VM,
	SEG,
	MOFFS,
	MULTI;
}



enum class NasmOp(
	val nasmString : String?,
	val type       : NasmOpType,
	val width      : Width?,
	val multi1     : NasmOp? = null,
	val multi2     : NasmOp? = null
) {

	NONE(null, NasmOpType.MISC, null),
	R8("reg8", NasmOpType.R, Width.BYTE),
	R16("reg16", NasmOpType.R, Width.WORD),
	R32("reg32", NasmOpType.R, Width.DWORD),
	R64("reg64", NasmOpType.R, Width.QWORD),
	MEM(null, NasmOpType.M, null),
	M8("mem8", NasmOpType.M, Width.BYTE),
	M16("mem16", NasmOpType.M, Width.WORD),
	M32("mem32", NasmOpType.M, Width.DWORD),
	M64("mem64", NasmOpType.M, Width.QWORD),
	M80("mem80", NasmOpType.M, Width.TWORD),
	M128("mem128", NasmOpType.M, Width.XWORD),
	M256("mem256", NasmOpType.M, Width.YWORD),
	M512("mem512", NasmOpType.M, Width.ZWORD),
	I8("imm8", NasmOpType.I, Width.BYTE),
	I16("imm16", NasmOpType.I, Width.WORD),
	I32("imm32", NasmOpType.I, Width.DWORD),
	I64("imm64", NasmOpType.I, Width.QWORD),
	AL("reg_al", NasmOpType.A, Width.BYTE),
	AX("reg_ax", NasmOpType.A, Width.WORD),
	EAX("reg_eax", NasmOpType.A, Width.DWORD),
	RAX("reg_rax", NasmOpType.A, Width.QWORD),
	CL("reg_cl", NasmOpType.C, Width.BYTE),
	ECX("reg_ecx", NasmOpType.C, Width.DWORD),
	RCX("reg_rcx", NasmOpType.C, Width.QWORD),
	DX("reg_dx", NasmOpType.MISC, Width.WORD),
	REL8(null, NasmOpType.REL, Width.BYTE),
	REL16(null, NasmOpType.REL, Width.WORD),
	REL32(null, NasmOpType.REL, Width.DWORD),
	ST("fpureg", NasmOpType.ST, Width.TWORD),
	ST0("fpu0", NasmOpType.ST, Width.TWORD),
	ONE("unity", NasmOpType.MISC, null),
	MM("mmxreg", NasmOpType.MM, Width.QWORD),
	X("xmmreg", NasmOpType.X, Width.XWORD),
	Y("ymmreg", NasmOpType.Y, Width.YWORD),
	Z("zmmreg", NasmOpType.Z, Width.ZWORD),
	VM32X("xmem32", NasmOpType.VM, Width.DWORD),
	VM64X("xmem64", NasmOpType.VM, Width.QWORD),
	VM32Y("ymem32", NasmOpType.VM, Width.DWORD),
	VM64Y("ymem64", NasmOpType.VM, Width.QWORD),
	VM32Z("zmem32", NasmOpType.VM, Width.DWORD),
	VM64Z("zmem64", NasmOpType.VM, Width.QWORD),
	K("kreg", NasmOpType.K, null),
	BND("bndreg", NasmOpType.MISC, null),
	T("tmmreg", NasmOpType.T, null),
	MOFFS8(null, NasmOpType.MOFFS, Width.BYTE),
	MOFFS16(null, NasmOpType.MOFFS, Width.WORD),
	MOFFS32(null, NasmOpType.MOFFS, Width.DWORD),
	MOFFS64(null, NasmOpType.MOFFS, Width.QWORD),
	SEG("reg_sreg", NasmOpType.SEG, null),
	CR("reg_creg", NasmOpType.MISC, Width.QWORD),
	DR("reg_dreg", NasmOpType.MISC, Width.QWORD),
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