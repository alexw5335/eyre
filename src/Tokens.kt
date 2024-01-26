package eyre



data class Token(val type: TokenType, val value: Int, val line: Int) {
	val regVal get() = Reg.entries[value]
	val isSym get() = type >= TokenType.EOF
	val nameValue get() = Names[value]
	fun stringValue(context: Context) = context.strings[value]

	override fun toString() = if(type == TokenType.NAME)
		"Token(NAME (${nameValue.string}), value=$value, line=$line)"
	else
		"Token($type, value=$value, line=$line)"

}



enum class TokenType(
	val string: String,
	val binOp: BinOp? = null,
	val unOp: UnOp? = null
) {
	// value is intern id
	NAME("name"),
	// value is index into string table
	STRING("string"),
	// value is 32-bit integer
	INT("int"),
	// value is 32-bit integer
	CHAR("char"),
	// value is Reg enum ordinal
	REG("reg"),

	// Symbols, no value
	// Note: Token::isSym depends on this enum's ordering
	EOF     ("EOF"),
	LPAREN  ("(", BinOp.INV),
	RPAREN  (")"),
	PLUS    ("+", BinOp.ADD, UnOp.POS),
	MINUS   ("-", BinOp.SUB, UnOp.NEG),
	STAR    ("*", BinOp.MUL),
	SLASH   ("/", BinOp.DIV),
	SET     ("=", BinOp.SET),
	COMMA   (","),
	SEMI    (";"),
	COLON   (":"),
	PIPE    ("|", BinOp.OR),
	AMP     ("&", BinOp.AND),
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