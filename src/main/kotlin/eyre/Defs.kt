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

	VMOVUPS,

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
		val XYZ    = Widths(0b111_0000)
		val XY     = Widths(0b011_0000)
		val X      = Widths(0b001_0000)
	}
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