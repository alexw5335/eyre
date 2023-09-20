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
	val string      : String?,
	val infixString : String?,
	val precedence  : Int,
	val calculate   : (Long, Long) -> Long = { _, _ -> 0L },
	val hasSymbol   : Boolean = false
) {

	ARR (null, null, 10),
	DOT (null, ".", 10, hasSymbol = true),

	REF (null, "::", 9, hasSymbol = true),

	MUL ("*", " * ", 8, { a, b -> a * b }),
	DIV ("/", " / ", 8, { a, b -> a / b }),

	ADD ("+", " + ", 7, { a, b -> a + b }),
	SUB ("-", " - ", 7, { a, b -> a - b }),

	SHL ("<<", " << ",  6, { a, b -> a shl b.toInt() }),
	SHR (">>", " >> ", 6, { a, b -> a shr b.toInt() }),
	SAR (">>>", " >>> ", 6, { a, b -> a ushr b.toInt() }),

	GT  (">", " > ", 5, { a, b -> if(a > b) 1 else 0 }),
	LT  ("<", " < ", 5, { a, b -> if(a < b) 1 else 0 }),
	GTE (">=", " >= ", 5, { a, b -> if(a >= b) 1 else 0 }),
	LTE ("<=", " <= ", 5, { a, b -> if(a <= b) 1 else 0 }),

	EQ  ("==", " == ", 4, { a, b -> if(a == b) 1 else 0 }),
	INEQ("!=", " != ", 4, { a, b -> if(a != b) 1 else 0 }),

	AND ("&", " & ", 3, { a, b -> a and b }),
	XOR ("^", " ^ ", 3, { a, b -> a xor b }),
	OR  ("|", " | ", 3, { a, b -> a or b }),

	LAND("&&", " && ", 2, { a, b -> if(a != 0L && b != 0L) 1 else 0 }),
	LOR ("||", " || ", 2, { a, b -> if(a != 0L || b != 0L) 1 else 0 }),

	SET("=", " = ", 1)

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
	val nodes       = ArrayList<AstNode>()
	var invalid     = false // Set by lexer and parser
	var resolved    = false
	var resolving   = false

}



class SrcPos(val file: SrcFile, val line: Int) {
	override fun toString() = "${file.path}:$line"
}



class Reloc(
	val pos    : Int,
	val sec    : Section,
	val node   : AstNode,
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

class EyreException(val srcPos: SrcPos?, message: String) : Exception(message)

class DllImports(val name: Name, val imports: HashMap<Name, DllImport>)

class DllDef(val name: Name, val exports: Set<Name>)