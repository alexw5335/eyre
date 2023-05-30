package eyre.nasm

enum class NasmExt(val isAvx: Boolean = false){
	// 0, 32-bit only
	AES,
	// 1, tmmreg
	AMXBF16,
	// 4, tmmreg
	AMXINT8,
	// 7, tmmreg or mem
	AMXTILE,
	// 703, xmm, ymm
	AVX(true),
	// 186, xmm, ymm, sib
	AVX2(true),
	// 1494
	AVX512(true),
	// 4, rs4
	AVX5124FMAPS(true),
	// 2, rs4
	AVX5124VNNIW(true),
	// 12, k
	AVX512BF16(true),
	// 3, zmm, k
	AVX512BITALG(true),
	// 412
	AVX512BW(true),
	// 18
	AVX512CD(true),
	// 132
	AVX512DQ(true),
	// 10
	AVX512ER(true),
	// 2, X_XM64 Y_XM128
	AVX512FC16(true),
	// 111
	AVX512FP16(true),
	// 6
	AVX512IFMA(true),
	// 16
	AVX512PF(true),
	// 12
	AVX512VBMI(true),
	// 18
	AVX512VBMI2(true),
	// 193
	AVX512VL(true),
	// 4, Z_Z_ZM
	AVX512VNNI(true),
	// 2, Z_ZM
	AVX512VPOPCNTDQ(true),
	// 4 (LATEVEX)
	AVXIFMA(true),
	// 14 (LATEEVEX
	AVXNECONVERT(true),
	// 12 (LATEVEX)
	AVXVNNIINT8(true),
	// 13, GP, R_R_RM, R_RM_R, R_RM
	BMI1,
	// 16, GP, R_RM_R, R_R_RM, R_RM_I8
	BMI2,
	// 14, GP
	CET,
	// 1, CMPccXADD (ignore)
	CMPCCXADD,
	// 4, R32_M512, R64_M512
	ENQCMD,
	// 192, x, y, xm, ym
	FMA(true),
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
	// 2, void
	MSRLIST,
	// 1, void
	PCONFIG,
	// 2, mem8
	PREFETCHI,
	// 1, mem8
	PREFETCHWT1,
	// 6, M_R (AADD, AAND, AXOR)
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
	VAES(true),
	// 13, GP, void, mem, R_RM, R64_MEM
	VMX,
	// 20, zmm, ymm, xmm,
	VPCLMULQDQ(true),
	// 1, void
	WBNOINVD,
	// 1, void
	WRMSRNS,
	// 0, obsolete
	_3DNOW,
	// 108, void, R, M_R, R_M512, K_K, K_K_K, mem, etc., many unique encodings
	NOT_GIVEN;

}