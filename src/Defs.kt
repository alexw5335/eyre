package eyre

import eyre.util.BitList
import eyre.util.IntList
import java.nio.file.Path



enum class Section(val string: String, val flags: Int) {

	/** initialised | code, execute | read */
	TEXT(".text", 0x60000020),

	/** initialised, read | write */
	DATA(".data", 0xC0000040L.toInt()),

	/** initialised, read */
	RDATA(".rdata", 0x40000040) ,

	/** uninitialised, read | write */
	BSS(".bss", 0xC0000080L.toInt()),

}



enum class UnOp(val string: String) {

	POS("+"),
	NEG("-",),
	NOT("~"),
	LNOT("!");

	fun calc(value: Int): Int = when(this) {
		POS  -> value
		NEG  -> -value
		NOT  -> value.inv()
		LNOT -> if(value == 0) 1 else 0
	}

	fun calc(value: Long): Long = when(this) {
		POS  -> value
		NEG  -> -value
		NOT  -> value.inv()
		LNOT -> if(value == 0L) 1L else 0L
	}

}



enum class BinOp(val precedence: Int, val string: String?) {

	ARR (10, null),
	DOT (10, "."),
	REF (9, "::"),
	MUL (8, "*"),
	DIV (8, "/"),
	ADD (7, "+"),
	SUB (7, "-"),
	SHL (6, "<<"),
	SHR (6, ">>"),
	SAR (6, ">>>"),
	GT  (5, ">"),
	LT  (5, "<"),
	GTE (5, ">="),
	LTE (5, "<="),
	EQ  (4, "=="),
	INEQ(4, "!="),
	AND (3, "&"),
	XOR (3, "^"),
	OR  (3, "|"),
	LAND(2, "&&"),
	LOR (2, "||"),
	SET (1, "=");

	fun calc(a: Int, b: Int): Int = when(this) {
		ARR -> 0
		DOT  -> 0
		REF  -> 0
		MUL  -> a * b
		DIV  -> a / b
		ADD  -> a + b
		SUB  -> a - b
		SHL  -> a shl b
		SHR  -> a shr b
		SAR  -> a ushr b
		GT   -> if(a > b) 1 else 0
		LT   -> if(a < b) 1 else 0
		GTE  -> if(a >= b) 1 else 0
		LTE  -> if(a <= b) 1 else 0
		EQ   -> if(a == b) 1 else 0
		INEQ -> if(a != b) 1 else 0
		AND  -> a and b
		XOR  -> a xor b
		OR   -> a or b
		LAND -> if(a != 0 && b != 0) 1 else 0
		LOR  -> if(a == 0 || b == 0) 1 else 0
		SET  -> 0
	}

	fun calc(a: Long, b: Long): Long = when(this) {
		ARR  -> 0
		DOT  -> 0
		REF  -> 0
		MUL  -> a * b
		DIV  -> a / b
		ADD  -> a + b
		SUB  -> a - b
		SHL  -> a shl b.toInt()
		SHR  -> a shr b.toInt()
		SAR  -> a ushr b.toInt()
		GT   -> if(a > b) 1 else 0
		LT   -> if(a < b) 1 else 0
		GTE  -> if(a >= b) 1 else 0
		LTE  -> if(a <= b) 1 else 0
		EQ   -> if(a == b) 1 else 0
		INEQ -> if(a != b) 1 else 0
		AND  -> a and b
		XOR  -> a xor b
		OR   -> a or b
		LAND -> if(a != 0L && b != 0L) 1 else 0
		LOR  -> if(a == 0L || b == 0L) 1 else 0
		SET  -> 0
	}

}



enum class InsPrefix(val value: Int) {

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
	VAL,
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



class SrcFile(val path: Path, val relPath: Path) {

	val tokens      = ArrayList<Token>()
	val tokenLines  = IntList()
	val newlines    = BitList()
	val terminators = BitList()
	val nodes       = ArrayList<Node>()
	var invalid     = false // Set by lexer and parser
	var resolved    = false
	var resolving   = false
	val lineCount get() = if(tokenLines.size == 0) 0 else tokenLines[tokenLines.size - 1]

}



class SrcPos(val file: SrcFile, val line: Int) {
	override fun toString() = "${file.path}:$line"
}



class Reloc(
    val pos    : Int,
    val sec    : Section,
    val node   : Node,
    val width  : Width,
    val offset : Int,
    val rel    : Boolean
)



enum class Prefix(val avxValue: Int, val value: Int, val string: String?, val avxString: String) {
	NONE(0, 0, null, "NP"),
	P66(1, 0x66, "66", "66"),
	PF2(3, 0xF2, "F2", "F2"),
	PF3(2, 0xF3, "F3", "F3"),
	P9B(0, 0x9B, "9B", "9B");
}



enum class Escape(val avxValue: Int, val string: String?, val avxString: String) {
	NONE(0, null, "NE"),
	E0F(1, "0F", "0F"),
	E38(2, "0F 38", "38"),
	E3A(3, "0F 3A", "3A");
}



class DebugDirective(val name: String, val pos: Int, val sec: Section)

class EyreException(val srcPos: SrcPos?, message: String) : Exception("$srcPos -- $message")

class DllImports(val name: Name, val imports: HashMap<Name, DllImport>)

class DllDef(val name: Name, val exports: Set<Name>)