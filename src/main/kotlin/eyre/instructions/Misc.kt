package eyre.instructions


class NasmLine(
	val lineNumber     : Int,
	val mnemonic       : String,
	val operandsString : String,
	val operands       : List<String>,
	val parts          : List<String>,
	val extras         : List<String>,
	val arch           : Arch,
	val extension      : Extension?,
) {

	override fun toString() = "$mnemonic $operandsString $parts $extras"

}



class Encoding(mnemonic: String, operands: NasmOperands)



class Group(mnemonic: String) {
	val encodings = ArrayList<Encoding>()
}



val groupMap = HashMap<String, Group>()


val nasmOperands = NasmOperands.values()

val noneMap = nasmOperands.associateBy { it.noneString }

val smMap = nasmOperands.associateBy { it.smString }



enum class NasmOperands(
	val noneString: String? = null,
	val smString: String? = null
) {
	M8_R8(smString = "mem,reg8"),
	M16_R16(smString = "mem,reg16"),
	M32_R32(smString = "mem,reg32"),
	M64_R64(smString = "mem,reg64"),
	R8_M8(smString = "reg8,mem"),
	R16_M16(smString = "reg16,mem"),
	R32_M32(smString = "reg32,mem"),
	R64_M64(smString = "reg64,mem"),

	R8_R8(noneString = "reg8,reg8"),
	R16_R16(noneString = "reg16,reg16"),
	R32_R32(noneString = "reg32,reg32"),
	R64_R64(noneString = "reg64,reg64"),

	AL_I8(smString = "reg_al,imm"),
	AX_I16(smString = "reg_ax,imm"),
	EAX_I32(smString = "reg_eax,imm",),
	RAX_I32(smString = "reg_rax,imm"),

	RM16_I8(noneString = "rm16,imm8"),
	RM32_I8(noneString = "rm32,imm8"),
	RM64_I8(noneString = "rm64,imm8"),

	RM8_I(smString = "rm8,imm"),
	RM16_I(smString = "rm16,imm"),
	RM32_I(smString = "rm32,imm"),
	RM64_I(smString = "rm64,imm"),

	MEM_I8(noneString = "mem,imm8"),
	MEM_I16(noneString = "mem,imm16"),
	MEM_I32(noneString = "mem,imm32"),
	R32(noneString = "reg32"),
	R64(noneString = "reg64"),

}



val invalidExtras = setOf(
	"NOLONG",
	"NEVER",
	"UNDOC",
	"OBSOLETE",
	"AMD",
	"CYRIX",
	"LATEVEX",
	"OPT"
)



val ignoredExtras = setOf(
	"DEFAULT",
	"ANY",
	"VEX",
	"EVEX",
	"NOP",
	"HLE",
	"NOHLE",
	"PRIV",
	"SMM",
	"PROT",
	"LOCK",
	"LONG",
	"BND",
	"MIB",
	"SIB",
	"SIZE",
	"ANYSIZE",
	"ND"
)



enum class Size {
	NONE,
	SB,
	SW,
	SD,
	SQ,
	SO,
	SY,
	SZ,
	SX,
	SM,
	SM2,
	AR0,
	SM2_SB_AR2,
	SD_AR1,
	SQ_AR1,
	SB_AR2,
	SB_AR1,
	SB_SM;
	companion object { val map = values().associateBy { it.name } }
}



enum class Extra {

	SM,
	SM2,
	SB,
	SW,
	SD,
	SQ,
	SO,
	SY,
	SZ,
	SX,
	AR0,
	AR1,
	AR2;

	companion object { val map = values().associateBy { it.name} }

}



enum class Arch {
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

	companion object { val map = values().associateBy { it.name.trimStart('_') } }

}



enum class Extension {

	NONE,
	FPU,
	MMX,
	_3DNOW,
	SSE,
	SSE2,
	SSE3,
	VMX,
	SSSE3,
	SSE4A,
	SSE41,
	SSE42,
	SSE5,
	AVX,
	AVX2,
	FMA,
	BMI1,
	BMI2,
	TBM,
	RTM,
	INVPCID,
	AVX512,
	AVX512CD,
	AVX512ER,
	AVX512PF,
	MPX,
	SHA,
	PREFETCHWT1,
	AVX512VL,
	AVX512DQ,
	AVX512BW,
	AVX512IFMA,
	AVX512VBMI,
	AES,
	VAES,
	VPCLMULQDQ,
	GFNI,
	AVX512VBMI2,
	AVX512VNNI,
	AVX512BITALG,
	AVX512VPOPCNTDQ,
	AVX5124FMAPS,
	AVX5124VNNIW,
	AVX512FP16,
	AVX512FC16,
	SGX,
	CET,
	ENQCMD,
	PCONFIG,
	WBNOINVD,
	TSXLDTRK,
	SERIALIZE,
	AVX512BF16,
	AVX512VP2INTERSECT,
	AMXTILE,
	AMXBF16,
	AMXINT8,
	FRED,
	RAOINT,
	UINTR,
	CMPCCXADD,
	PREFETCHI,
	WRMSRNS,
	MSRLIST,
	AVXNECONVERT,
	AVXVNNIINT8,
	AVXIFMA,
	HRESET;

	companion object { val map = values().associateBy { it.name.trimStart('_') } }

}