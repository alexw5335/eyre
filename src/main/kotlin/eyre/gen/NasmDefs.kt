package eyre.gen

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
	// 1, CMPccXADD (ignore)
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



enum class OpEnc(val string: String?) {
	NONE(null),
	ij("ij"),
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
	RMVI("rmvi");
}



enum class ImmType {
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



enum class TupleType {
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



enum class VSib {
	VM32X,
	VM64X,
	VM64Y,
	VM32Y,
	VSIBX,
	VSIBY,
	VSIBZ;
}



enum class VexL(val value: Int) {
	LIG(0),
	L0(0),
	LZ(0),
	L1(1),
	L128(0),
	L256(1),
	L512(2);
}



enum class VexW(val value: Int) {
	WIG(0),
	W0(0),
	W1(1);
}