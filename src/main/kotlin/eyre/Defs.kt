package eyre

import eyre.util.BitList
import eyre.util.IntList
import java.nio.file.Path



enum class Section {

	/** initialised | code, execute | read */
	TEXT,

	/** initialised, read | write */
	DATA,

	/** initialised, read */
	RDATA,

	/** uninitialised, read | write */
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



class SectionData(var size: Int, var rva: Int, var pos: Int)



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
	val pos    : Int,
	val sec    : Section,
	val node   : AstNode,
	val width  : Width,
	val offset : Int,
	val rel    : Boolean
)



class DllImports(val name: Name, val imports: HashMap<Name, DllImportSymbol>)

class DllDef(val name: Name, val exports: Set<Name>)



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



class DebugDirective(
	val name : String,
	val pos  : Int,
	val sec  : Section
)