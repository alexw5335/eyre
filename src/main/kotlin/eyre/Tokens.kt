package eyre



sealed interface Token

object EndToken : Token { override fun toString() = "END OF FILE" }

data class IntToken(val value: Long) : Token

data class CharToken(val value: Char) : Token

data class StringToken(val value: StringIntern) : Token

data class IdToken(val value: StringIntern) : Token

object ErrorToken : Token { override fun toString() = "ERROR" }



enum class SymToken(
	val string   : String,
	val binaryOp : BinaryOp? = null,
	val unaryOp  : UnaryOp?  = null
) : Token {

	LPAREN     ("("),
	RPAREN     (")"),
	PLUS       ("+", BinaryOp.ADD, UnaryOp.POS),
	MINUS      ("-", BinaryOp.SUB, UnaryOp.NEG),
	ASTERISK   ("*", BinaryOp.MUL),
	SLASH      ("/", BinaryOp.DIV),
	EQUALS     ("="),
	COMMA      (","),
	SEMICOLON  (";"),
	COLON      (":"),
	PIPE       ("|", BinaryOp.OR),
	AMPERSAND  ("&", BinaryOp.AND),
	LOGIC_AND  ("&&", BinaryOp.LAND),
	LOGIC_OR   ("||", BinaryOp.LOR),
	TILDE      ("~", null, UnaryOp.NOT),
	CARET      ("^", BinaryOp.XOR),
	LT         ("<", BinaryOp.LT),
	GT         (">", BinaryOp.GT),
	SHL        ("<<", BinaryOp.SHL),
	SHR        (">>", BinaryOp.SHR),
	SAR        (">>>", BinaryOp.SAR),
	LTE        ("<=", BinaryOp.LTE),
	GTE        (">=", BinaryOp.GTE),
	EQUALITY   ("==", BinaryOp.EQ),
	INEQUALITY ("!=", BinaryOp.INEQ),
	EXCLAMATION("!", null, UnaryOp.LNOT),
	QUESTION   ("?"),
	LBRACKET   ("["),
	RBRACKET   ("]"),
	LBRACE     ("{"),
	RBRACE     ("}"),
	PERIOD     (".", BinaryOp.DOT),
	REFERENCE  ("::", BinaryOp.REF);

}