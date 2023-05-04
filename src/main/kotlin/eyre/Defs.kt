package eyre

import eyre.util.BitList
import eyre.util.IntList
import java.nio.file.Path



enum class Mnemonic {

	ADD, OR, ADC, SBB, AND,
	SUB, XOR, CMP, PUSH,
	POP, PUSHW, POPW, MOVSXD,
	MOVSX, MOVZX, INSB, INSW,
	INSD, OUTSB, OUTSW, OUTSD,
	JA, JAE, JB, JBE,
	JC, JE, JG, JGE,
	JL, JLE, JNA, JNAE,
	JNB, JNBE, JNC, JNE,
	JNG, JNGE, JNL, JNLE,
	JNO, JNP, JNS, JNZ,
	JO, JP, JPE, JPO,
	JS, JZ, TEST, XCHG,
	MOV, LEA, NOP, CBW,
	CWDE, CDQE, CWD, CDQ,
	CQO, WAIT, FWAIT, PUSHF,
	PUSHFQ, LAHF, MOVSB, MOVSW,
	MOVSD, MOVSQ, CMPSB, CMPSW,
	CMPSD, CMPSQ, STOSB, STOSW,
	STOSD, STOSQ, SCASB, SCASW,
	SCASD, SCASQ, LODSB, LODSW,
	LODSD, LODSQ, ROL, ROR,
	RCL, RCR, SAL, SHL,
	SHR, SAR, RET, RETF,
	LEAVE, INT3, INT, INT1,
	IRETW, IRETD, IRETQ, LOOP,
	LOOPE, LOOPNE, JECXZ, JRCXZ,
	IN, OUT, HLT, CMC,
	NOT, NEG, MUL, IMUL,
	DIV, IDIV, CLC, STC,
	CLI, STI, CLD, STD,
	INC, DEC, CALL, CALLF,
	JMP, JMPF, RDRAND, RDSEED,
	PAUSE, BSR, BSF,
	
	SETO, SETNO, SETB, SETNAE, SETC, SETNB, SETAE, SETNC,
	SETZ, SETE, SETNZ, SETNE, SETBE, SETNA, SETNBE, SETA,
	SETS, SETNS, SETP, SETPE, SETNP, SETPO, SETL, SETNGE, 
	SETNL, SETGE, SETLE, SETNG, SETNLE, SETG,

	CMOVO, CMOVNO, CMOVB, CMOVNAE, CMOVC, CMOVNB, CMOVAE, CMOVNC,
	CMOVZ, CMOVE, CMOVNZ, CMOVNE, CMOVBE, CMOVNA, CMOVNBE, CMOVA,
	CMOVS, CMOVNS, CMOVP, CMOVPE, CMOVNP, CMOVPO, CMOVL, CMOVNGE,
	CMOVNL, CMOVGE, CMOVLE, CMOVNG, CMOVNLE, CMOVG,

	FLD, FST, FSTP, FXCH, FCMOVB, FCMOVE, FCMOVBE, FCMOVU,
	FCMOVNB, FCMOVNE, FCMOVNBE, FCMOVNU, FILD, FIST, FISTP, FISTTP,
	FBLD, FBSTP, FLDZ, FLD1, FLDPI, FLDL2T, FLDL2E,
	FLDLG2, FLDLN2, FADD, FADDP, FIADD, FSUB, FSUBP, FISUB, FSUBR,
	FSUBRP, FISUBR, FMUL, FMULP, FIMUL, FDIV, FDIVP, FIDIV,
	FDIVR, FDIVRP, FIDIVR, FABS, FCHS, FSQRT, FPREM, FPREM1,
	FRNDINT, FXTRACT, FCOM, FCOMP, FCOMPP, FUCOM, FUCOMP, FUCOMPP,
	FICOM, FICOMP, FCOMI, FCOMIP, FUCOMI, FUCOMIP, FTST, FXAM,
	FSIN, FCOS, FSINCOS, FPTAN, FPATAN, FYL2X, FYL2XP1, F2XM1,
	FSCALE, FINIT, FNINIT, FLDCW, FSTCW, FNSTCW, FSTSW, FNSTSW,
	FCLEX, FNCLEX, FLDENV, FSTENV, FNSTENV, FRSTOR, FSAVE, FNSAVE,
	FINCSTP, FDECSTP, FFREE, FNOP,

	SLDT, STR, LLDT, LTR, VERR, VERW, SGDT,
	VMCALL, VMLAUNCH, VMRESUME, VMXOFF, SIDT,
	MONITOR, MWAIT, LGDT, XGETBV, XSETBV, LIDT,
	SMSW, LMSW, INVLPG, SWAPGS, RDTSCP, LAR, LSL,
	SYSCALLD, SYSCALLQ, SYSRETD, SYSRETQ,
	CLTS, INVD, WBINVD, PREFETCHT0, PREFETCHT1,
	PREFETCHT2, PREFETCHNTA,

	MOVUPS, MOVUPD, MOVSS, MOVHLPS, MOVLPS, MOVLPD,
	MOVDDUP, MOVSLDUP, UNPCKLPS, UNPCKLPD, UNPCKHPS,
	UNPCKHPD, MOVLHPS, MOVHPS, MOVHPD, MOVSHDUP,
	MOVAPS, MOVAPD, CVTPI2PS, CVTSI2SS, CVTPI2PD,
	CVTSI2SD, MOVNTPS, MOVNTPD, CVTTPS2PI, CVTTSS2SI,
	CVTTPD2PI, CVTTSD2SI, CVTPS2PI, CVTSS2SI, CVTPD2PI,
	CVTSD2SI, UCOMISS, UCOMISD, COMISS, COMISD,

	DLLCALL,
	RETURN;

	val string = name.lowercase()

}



enum class Width(val varString: String, val bytes: Int) {

	BYTE("db", 1),
	WORD("dw", 2),
	DWORD("dd", 4),
	QWORD("dq", 8),
	TWORD("dt", 10),
	XWORD("do", 16),
	YWORD("dy", 32),
	ZWORD("dz", 64);

	val string = name.lowercase()
	val bit = 1 shl ordinal

	// Only for BYTE, WORD, DWORD, and QWORD
	val min: Long = -(1L shl ((bytes shl 3) - 1))
	val max: Long = (1L shl ((bytes shl 3) - 1)) - 1
	operator fun contains(value: Int) = value in min..max
	operator fun contains(value: Long) = value in min..max

}



@JvmInline
value class Widths(val value: Int) {
	operator fun contains(width: Width) = value and width.bit != 0
	companion object {
		val ALL    = Widths(0b1111)
		val NO8    = Widths(0b1110)
		val NO832  = Widths(0b1010)
		val NO864  = Widths(0b0110)
		val ONLY64 = Widths(0b1000)
		val ONLY8  = Widths(0b0001)
		val NO816  = Widths(0b1100)
	}
}



enum class RegType {
	GP,
	SEG,
	FPU,
	MMX,
	XMM;
}



enum class SegReg {
	FS,
	GS;
}


enum class SseReg(
	val value : Int,
	val rex   : Int,
	val high  : Int,
	val width : Width
) {
				  
	MM0(0, 0, 0, Width.QWORD),
	MM1(1, 0, 0, Width.QWORD),
	MM2(2, 0, 0, Width.QWORD),
	MM3(3, 0, 0, Width.QWORD),
	MM4(4, 0, 0, Width.QWORD),
	MM5(5, 0, 0, Width.QWORD),
	MM6(6, 0, 0, Width.QWORD),
	MM7(7, 0, 0, Width.QWORD),

	XMM0(0, 0, 0, Width.XWORD),
	XMM1(1, 0, 0, Width.XWORD),
	XMM2(2, 0, 0, Width.XWORD),
	XMM3(3, 0, 0, Width.XWORD),
	XMM4(4, 0, 0, Width.XWORD),
	XMM5(5, 0, 0, Width.XWORD),
	XMM6(6, 0, 0, Width.XWORD),
	XMM7(0, 0, 0, Width.XWORD),
	XMM8(0, 1, 0, Width.XWORD),
	XMM9(0, 1, 0, Width.XWORD),
	XMM10(0, 1, 0, Width.XWORD),
	XMM11(0, 1, 0, Width.XWORD),
	XMM12(0, 1, 0, Width.XWORD),
	XMM13(0, 1, 0, Width.XWORD),
	XMM14(0, 1, 0, Width.XWORD),
	XMM15(0, 1, 0, Width.XWORD),
	XMM16(0, 0, 1, Width.XWORD),
	XMM17(0, 0, 1, Width.XWORD),
	XMM18(0, 0, 1, Width.XWORD),
	XMM19(0, 0, 1, Width.XWORD),
	XMM20(0, 0, 1, Width.XWORD),
	XMM21(0, 0, 1, Width.XWORD),
	XMM22(0, 0, 1, Width.XWORD),
	XMM23(0, 0, 1, Width.XWORD),
	XMM24(0, 1, 1, Width.XWORD),
	XMM25(0, 1, 1, Width.XWORD),
	XMM26(0, 1, 1, Width.XWORD),
	XMM27(0, 1, 1, Width.XWORD),
	XMM28(0, 1, 1, Width.XWORD),
	XMM29(0, 1, 1, Width.XWORD),
	XMM30(0, 1, 1, Width.XWORD),
	XMM31(0, 1, 1, Width.XWORD),

	YMM0(0, 0, 0, Width.YWORD),
	YMM1(1, 0, 0, Width.YWORD),
	YMM2(2, 0, 0, Width.YWORD),
	YMM3(3, 0, 0, Width.YWORD),
	YMM4(4, 0, 0, Width.YWORD),
	YMM5(5, 0, 0, Width.YWORD),
	YMM6(6, 0, 0, Width.YWORD),
	YMM7(0, 0, 0, Width.YWORD),
	YMM8(0, 1, 0, Width.YWORD),
	YMM9(0, 1, 0, Width.YWORD),
	YMM10(0, 1, 0, Width.YWORD),
	YMM11(0, 1, 0, Width.YWORD),
	YMM12(0, 1, 0, Width.YWORD),
	YMM13(0, 1, 0, Width.YWORD),
	YMM14(0, 1, 0, Width.YWORD),
	YMM15(0, 1, 0, Width.YWORD),
	YMM16(0, 0, 1, Width.YWORD),
	YMM17(0, 0, 1, Width.YWORD),
	YMM18(0, 0, 1, Width.YWORD),
	YMM19(0, 0, 1, Width.YWORD),
	YMM20(0, 0, 1, Width.YWORD),
	YMM21(0, 0, 1, Width.YWORD),
	YMM22(0, 0, 1, Width.YWORD),
	YMM23(0, 0, 1, Width.YWORD),
	YMM24(0, 1, 1, Width.YWORD),
	YMM25(0, 1, 1, Width.YWORD),
	YMM26(0, 1, 1, Width.YWORD),
	YMM27(0, 1, 1, Width.YWORD),
	YMM28(0, 1, 1, Width.YWORD),
	YMM29(0, 1, 1, Width.YWORD),
	YMM30(0, 1, 1, Width.YWORD),
	YMM31(0, 1, 1, Width.YWORD);

}



enum class FpuReg(val value: Int) {

	ST0(0),
	ST1(1),
	ST2(2),
	ST3(3),
	ST4(4),
	ST5(5),
	ST6(6),
	ST7(7);

	val string = name.lowercase()

}



enum class MmxReg(val value: Int) {

	MM0(0),
	MM1(1),
	MM2(2),
	MM3(3),
	MM4(4),
	MM5(5),
	MM6(6),
	MM7(7);

	val string = name.lowercase()

}



interface SimdReg {
	val value: Int
	val rex: Int
	val high: Int
}



enum class XmmReg : SimdReg {

	XMM0,
	XMM1,
	XMM2,
	XMM3,
	XMM4,
	XMM5,
	XMM6,
	XMM7,
	XMM8,
	XMM9,
	XMM10,
	XMM11,
	XMM12,
	XMM13,
	XMM14,
	XMM15,
	XMM16,
	XMM17,
	XMM18,
	XMM19,
	XMM20,
	XMM21,
	XMM22,
	XMM23,
	XMM24,
	XMM25,
	XMM26,
	XMM27,
	XMM28,
	XMM29,
	XMM30,
	XMM31;
	
	override val value  = ordinal and 0b111
	override val rex    = (ordinal shr 3) and 1
	override val high   = ordinal shr 4
	val string = name.lowercase()

}



enum class YmmReg : SimdReg {

	YMM0,
	YMM1,
	YMM2,
	YMM3,
	YMM4,
	YMM5,
	YMM6,
	YMM7,
	YMM8,
	YMM9,
	YMM10,
	YMM11,
	YMM12,
	YMM13,
	YMM14,
	YMM15,
	YMM16,
	YMM17,
	YMM18,
	YMM19,
	YMM20,
	YMM21,
	YMM22,
	YMM23,
	YMM24,
	YMM25,
	YMM26,
	YMM27,
	YMM28,
	YMM29,
	YMM30,
	YMM31;

	override val value  = ordinal and 0b111
	override val rex    = (ordinal shr 3) and 1
	override val high   = ordinal shr 4
	val string = name.lowercase()

}



enum class ZmmReg : SimdReg{

	ZMM0,
	ZMM1,
	ZMM2,
	ZMM3,
	ZMM4,
	ZMM5,
	ZMM6,
	ZMM7,
	ZMM8,
	ZMM9,
	ZMM10,
	ZMM11,
	ZMM12,
	ZMM13,
	ZMM14,
	ZMM15,
	ZMM16,
	ZMM17,
	ZMM18,
	ZMM19,
	ZMM20,
	ZMM21,
	ZMM22,
	ZMM23,
	ZMM24,
	ZMM25,
	ZMM26,
	ZMM27,
	ZMM28,
	ZMM29,
	ZMM30,
	ZMM31;

	override val value  = ordinal and 0b111
	override val rex    = (ordinal shr 3) and 1
	override val high   = ordinal shr 4
	val string = name.lowercase()

}



enum class Register(
	val value: Int,
	val width: Width,
	val rex: Int = 0,
	val isA: Boolean = false,
	val isSP: Boolean = false,
	val rex8: Boolean = false,
	val noRex8: Boolean = false
) {

	RAX(0, Width.QWORD, isA = true),
	RCX(1, Width.QWORD),
	RDX(2, Width.QWORD),
	RBX(3, Width.QWORD),
	RSP(4, Width.QWORD, isSP = true),
	RBP(5, Width.QWORD),
	RSI(6, Width.QWORD),
	RDI(7, Width.QWORD),
	R8 (0, Width.QWORD, 1),
	R9 (1, Width.QWORD, 1),
	R10(2, Width.QWORD, 1),
	R11(3, Width.QWORD, 1),
	R12(4, Width.QWORD, 1),
	R13(5, Width.QWORD, 1),
	R14(6, Width.QWORD, 1),
	R15(7, Width.QWORD, 1),

	EAX (0, Width.DWORD, isA = true),
	ECX (1, Width.DWORD),
	EDX (2, Width.DWORD),
	EBX (3, Width.DWORD),
	ESP (4, Width.DWORD, isSP = true),
	EBP (5, Width.DWORD),
	ESI (6, Width.DWORD),
	EDI (7, Width.DWORD),
	R8D (0, Width.DWORD, 1),
	R9D (1, Width.DWORD, 1),
	R10D(2, Width.DWORD, 1),
	R11D(3, Width.DWORD, 1),
	R12D(4, Width.DWORD, 1),
	R13D(5, Width.DWORD, 1),
	R14D(6, Width.DWORD, 1),
	R15D(7, Width.DWORD, 1),

	AX  (0, Width.WORD, isA = true),
	CX  (1, Width.WORD),
	DX  (2, Width.WORD),
	BX  (3, Width.WORD),
	SP  (4, Width.WORD, isSP = true),
	BP  (5, Width.WORD),
	SI  (6, Width.WORD),
	DI  (7, Width.WORD),
	R8W (0, Width.WORD, 1),
	R9W (1, Width.WORD, 1),
	R10W(2, Width.WORD, 1),
	R11W(3, Width.WORD, 1),
	R12W(4, Width.WORD, 1),
	R13W(5, Width.WORD, 1),
	R14W(6, Width.WORD, 1),
	R15W(7, Width.WORD, 1),

	AL  (0, Width.BYTE, isA = true),
	CL  (1, Width.BYTE),
	DL  (2, Width.BYTE),
	BL  (3, Width.BYTE),
	AH  (4, Width.BYTE, rex8 = true),
	CH  (5, Width.BYTE, rex8 = true),
	DH  (6, Width.BYTE, rex8 = true),
	BH  (7, Width.BYTE, rex8 = true),
	R8B (0, Width.BYTE, 1),
	R9B (1, Width.BYTE, 1),
	R10B(2, Width.BYTE, 1),
	R11B(3, Width.BYTE, 1),
	R12B(4, Width.BYTE, 1),
	R13B(5, Width.BYTE, 1),
	R14B(6, Width.BYTE, 1),
	R15B(7, Width.BYTE, 1),

	SPL(4, Width.BYTE, isSP = true, noRex8 = true),
	BPL(5, Width.BYTE, noRex8 = true),
	SIL(6, Width.BYTE, noRex8 = true),
	DIL(7, Width.BYTE, noRex8 = true);

	val string = name.lowercase()

	val invalidBase get() = value == 5
	val isSpOr12 get() = value == 4

}



enum class Section {

	/** Invalid */
	NONE,

	/** .text, initialised | code, execute | read */
	TEXT,

	/** .data, initialised, read | write */
	DATA,

	/** .idata, initialised, read */
	IDATA,

	/** .bss, uninitialised, read | write */
	BSS,

}



enum class UnaryOp(
	val symbol     : String,
	val calculate  : (Long) -> Long,
) {

	POS("+", { it }),
	NEG("-", { -it }),
	NOT("~", { it.inv() }),
	LNOT("!", { if(it == 0L) 1L else 0L }),

}



enum class BinaryOp(
	val symbol     : String?,
	val precedence : Int,
	val calculate  : (Long, Long) -> Long = { _, _ -> 0L }
) {

	ARR (null,  10),
	DOT (null,  10),

	REF (null,  9),

	MUL ("*",   8, { a, b -> a * b }),
	DIV ("/",   8, { a, b -> a / b }),

	ADD ("+",   7, { a, b -> a + b }),
	SUB ("-",   7, { a, b -> a - b }),

	SHL ("<<",  6, { a, b -> a shl b.toInt() }),
	SHR (">>",  6, { a, b -> a shr b.toInt() }),
	SAR (">>>", 6, { a, b -> a ushr b.toInt() }),

	GT  (">",   5, { a, b -> if(a > b) 1 else 0 }),
	LT  ("<",   5, { a, b -> if(a < b) 1 else 0 }),
	GTE (">=",  5, { a, b -> if(a >= b) 1 else 0 }),
	LTE ("<=",  5, { a, b -> if(a <= b) 1 else 0 }),

	EQ  ("==",  4, { a, b -> if(a == b) 1 else 0 }),
	INEQ("!=",  4, { a, b -> if(a != b) 1 else 0 }),

	AND ("&",   3, { a, b -> a and b }),
	XOR ("^",   3, { a, b -> a xor b }),
	OR  ("|",   3, { a, b -> a or b }),

	LAND("&&",  2, { a, b -> if(a != 0L && b != 0L) 1 else 0 }),
	LOR ("||",  2, { a, b -> if(a != 0L || b != 0L) 1 else 0 }),

	SET("=", 1)


}



enum class Prefix(val value: Int) {

	REP(0xF3),
	REPE(0xF3),
	REPZ(0xF3),
	REPNE(0xF2),
	REPNZ(0xF2),
	LOCK(0xF0);

	val string = name.lowercase()

}



enum class Keyword {

	CONST,
	VAR,
	IMPORT,
	ENUM,
	NAMESPACE,
	FLAGS,
	STRUCT,
	PROC,
	BITMASK,
	TYPEDEF;

	val string = name.lowercase()

}



class SectionData(val size: Int, val rva: Int, val pos: Int)



class SrcFile(val path: Path, val relPath: Path) {

	// Valid after the lexer has been called
	// Invalid after the lexer has been used to lex another file
	lateinit var tokens      : List<Token>
	lateinit var tokenLines  : IntList
	lateinit var newlines    : BitList
	lateinit var terminators : BitList

	// Valid after the parser has been called
	lateinit var nodes: List<AstNode>

}



class SrcPos(val file: SrcFile, val line: Int)



class Reloc(
	val pos     : Int,
	val section : Section,
	val width   : Width,
	val node    : AstNode,
	val offset  : Int,
	val type    : RelocType
)



enum class RelocType {
	ABS,
	RIP,
	LINK
}



class DllImports(val name: Name, val imports: HashMap<Name, DllImportSymbol>)

class DllDef(val name: Name, val exports: Set<Name>)