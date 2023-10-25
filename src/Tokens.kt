package eyre

// Name (in Interns.kt) is also a token

sealed interface Token

data class IntToken(val value: Long) : Token

data class CharToken(val value: Char) : Token

data class StringToken(val value: String) : Token

data class FloatToken(val value: Double) : Token

data class RegToken(val value: Reg) : Token



enum class SymToken(
	val string : String,
	val binOp  : BinOp? = null,
	val unOp   : UnOp?  = null
) : Token {
	NEWLINE  ("\\n"),
	LPAREN   ("("),
	RPAREN   (")"),
	PLUS     ("+", BinOp.ADD, UnOp.POS),
	MINUS    ("-", BinOp.SUB, UnOp.NEG),
	STAR     ("*", BinOp.MUL),
	SLASH    ("/", BinOp.DIV),
	SET      ("=", BinOp.SET),
	COMMA    (","),
	SEMI     (";"),
	COLON    (":"),
	PIPE     ("|", BinOp.OR),
	AMP      ("&", BinOp.AND),
	LAND     ("&&", BinOp.LAND),
	LOR      ("||", BinOp.LOR),
	TILDE    ("~", null, UnOp.NOT),
	CARET    ("^", BinOp.XOR),
	LT       ("<", BinOp.LT),
	GT       (">", BinOp.GT),
	SHL      ("<<", BinOp.SHL),
	SHR      (">>", BinOp.SHR),
	SAR      (">>>", BinOp.SAR),
	LTE      ("<=", BinOp.LTE),
	GTE      (">=", BinOp.GTE),
	EQ       ("==", BinOp.EQ),
	INEQ     ("!=", BinOp.INEQ),
	BANG     ("!", null, UnOp.LNOT),
	QUESTION ("?"),
	LBRACKET ("[", BinOp.ARR),
	RBRACKET ("]"),
	LBRACE   ("{"),
	RBRACE   ("}"),
	HASH     ("#"),
	PERIOD   (".", BinOp.DOT),
	REF      ("::", BinOp.REF);
}