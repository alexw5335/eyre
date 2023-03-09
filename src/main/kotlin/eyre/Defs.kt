package eyre

import eyre.util.BitList
import eyre.util.IntList
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.nameWithoutExtension



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
	PAUSE, 
	
	SETO, SETNO, SETB, SETNAE, SETC, SETNB, SETAE, SETNC,
	SETZ, SETE, SETNZ, SETNE, SETBE, SETNA, SETNBE, SETA,
	SETS, SETNS, SETP, SETPE, SETNP, SETPO, SETL, SETNGE, 
	SETNL, SETGE, SETLE, SETNG, SETNLE, SETG,

	CMOVO, CMOVNO, CMOVB, CMOVNAE, CMOVC, CMOVNB, CMOVAE, CMOVNC,
	CMOVZ, CMOVE, CMOVNZ, CMOVNE, CMOVBE, CMOVNA, CMOVNBE, CMOVA,
	CMOVS, CMOVNS, CMOVP, CMOVPE, CMOVNP, CMOVPO, CMOVL, CMOVNGE,
	CMOVNL, CMOVGE, CMOVLE, CMOVNG, CMOVNLE, CMOVG,

	DLLCALL;

	val string = name.lowercase()

}



enum class Width(val varString: String, val bytes: Int) {

	BYTE("db", 1),
	WORD("dw", 2),
	DWORD("dd", 4),
	QWORD("dq", 8);

	val string = name.lowercase()
	val bit = 1 shl ordinal
	val min = -(1 shl ((bytes shl 3) - 1))
	val max = (1 shl ((bytes shl 3) - 1)) - 1
	val immLength = bytes.coerceAtMost(4)

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
		val NO816  = Widths(0b0011)
	}
}



enum class SegReg {
	FS,
	GS;
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
	BSS;

}



enum class UnaryOp(
	val symbol     : String,
	val calculate  : (Long) -> Long,
) {

	POS("+", { it }),
	NEG("-", { -it }),
	NOT("~", { it.inv() }),
	LNOT("!", { if(it == 0L) 1L else 0L });

}



enum class BinaryOp(
	val symbol          : String?,
	val precedence      : Int,
	val calculate       : (Long, Long) -> Long
) {

	DOT (null,  9, { _, _ -> 0L }),

	REF (null,  8, { _,_ -> 0L }),

	MUL ("*",   7, { a, b -> a * b }),
	DIV ("/",   7, { a, b -> a / b }),

	ADD ("+",   6, { a, b -> a + b }),
	SUB ("-",   6, { a, b -> a - b }),

	SHL ("<<",  5, { a, b -> a shl b.toInt() }),
	SHR (">>",  5, { a, b -> a shr b.toInt() }),
	SAR (">>>", 5, { a, b -> a ushr b.toInt() }),

	GT  (">",   4, { a, b -> if(a > b) 1 else 0 }),
	LT  ("<",   4, { a, b -> if(a < b) 1 else 0 }),
	GTE (">=",  4, { a, b -> if(a >= b) 1 else 0 }),
	LTE ("<=",  4, { a, b -> if(a <= b) 1 else 0 }),

	EQ  ("==",  3, { a, b -> if(a == b) 1 else 0 }),
	INEQ("!=",  3, { a, b -> if(a != b) 1 else 0 }),

	AND ("&",   2, { a, b -> a and b }),
	XOR ("^",   2, { a, b -> a xor b }),
	OR  ("|",   2, { a, b -> a or b }),

	LAND("&&",  1, { a, b -> if(a != 0L && b != 0L) 1 else 0 }),
	LOR ("||",  1, { a, b -> if(a != 0L || b != 0L) 1 else 0 }),


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
	DLLIMPORT;

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

	// Used by the resolver
	var resolving = false
	var resolved = false

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



class InbuiltDll(val name: String, val exports: Set<String>)