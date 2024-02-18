package eyre



data class Token(
	val type: TokenType,
	val line: Int,
	val intValue: Long = 0L,
	val stringValue: String = "",
	val nameValue: Name = Name.NONE,
	val regValue: Reg = Reg.NONE,
) {
	val isSym get() = type >= TokenType.EOF
	override fun toString() = buildString {
		append("Token(line $line, ")
		when(type) {
			TokenType.NAME   -> append("name = $nameValue")
			TokenType.STRING -> append("string = $stringValue")
			TokenType.INT    -> append("int = $intValue")
			TokenType.CHAR   -> append("char = ${Char(intValue.toInt())}")
			TokenType.REG    -> append("reg = $regValue")
			else             -> append("sym = ${type.string}")
		}
		append(')')
	}
}



enum class TokenType(
	val string: String,
	val binOp: BinOp? = null,
	val unOp: UnOp? = null
) {

	/*IF        ("if"),
	ELIF      ("elif"),
	ELSE      ("else"),
	WHILE     ("while"),
	FOR       ("for"),
	VAR       ("var"),
	NAMESPACE ("namespace"),
	FUN       ("fun"),
	STRUCT    ("struct"),
	UNION     ("union"),
	ENUM      ("enum"),
	PROC      ("proc"),
	TYPEDEF   ("typedef"),
	CONST     ("const"),*/

	NAME    ("name"),
	STRING  ("string"),
	INT     ("int"),
	CHAR    ("char"),
	REG     ("reg"),
	EOF     ("eof"),

	LPAREN  ("(", BinOp.INV),
	RPAREN  (")"),
	PLUS    ("+", BinOp.ADD, UnOp.POS),
	MINUS   ("-", BinOp.SUB, UnOp.NEG),
	STAR    ("*", BinOp.MUL, UnOp.DEREF),
	SLASH   ("/", BinOp.DIV),
	SET     ("=", BinOp.SET),
	COMMA   (","),
	SEMI    (";"),
	COLON   (":"),
	PIPE    ("|", BinOp.OR),
	AMP     ("&", BinOp.AND, UnOp.ADDR),
	LAND    ("&&", BinOp.LAND),
	LOR     ("||", BinOp.LOR),
	TILDE   ("~", null, UnOp.NOT),
	CARET   ("^", BinOp.XOR),
	LT      ("<", BinOp.LT),
	GT      (">", BinOp.GT),
	SHL     ("<<", BinOp.SHL),
	SHR     (">>", BinOp.SHR),
	SAR     (">>>", BinOp.SAR),
	LTE     ("<=", BinOp.LTE),
	GTE     (">=", BinOp.GTE),
	EQ      ("==", BinOp.EQ),
	NEQ     ("!=", BinOp.INEQ),
	BANG    ("!", null, UnOp.LNOT),
	QUEST   ("?"),
	LBRACK  ("[", BinOp.ARR),
	RBRACK  ("]"),
	LBRACE  ("{"),
	RBRACE  ("}"),
	HASH    ("#"),
	DOT     (".", BinOp.DOT),
	REF     ("::", BinOp.REF),
	AT      ("@");
}