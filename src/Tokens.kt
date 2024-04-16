package eyre



class Token(
	val type: TokenType,
	val line: Int,
	val intValue: Long = 0L,
	val stringValue: String = "",
	val nameValue: Name = Name.NONE,
) {
	override fun toString() = buildString {
		append("Token(line $line, ")
		when(type) {
			TokenType.NAME   -> append("name = $nameValue")
			TokenType.STRING -> append("string = $stringValue")
			TokenType.INT    -> append("int = $intValue")
			TokenType.CHAR   -> append("char = ${Char(intValue.toInt())}")
			else             -> append(type.string)
		}
		append(')')
	}
}



enum class TokenType(
	val string: String,
	val binOp: BinOp? = null,
	val unOp: UnOp? = null
) {
	FOR("for"),
	DO("do"),
	WHILE("while"),
	IF("if"),
	ELIF("elif"),
	ELSE("else"),
	VAR("var"),
	NAMESPACE("namespace"),
	STRUCT("struct"),
	UNION("union"),
	ENUM("enum"),
	FUN("fun"),
	CONST("const"),
	DLLIMPORT("dllimport"),
	RETURN("return"),

	NAME("name"),
	STRING("string"),
	INT("int"),
	CHAR("char"),
	EOF("eof"),

	LPAREN  ("(", BinOp.CALL),
	RPAREN  (")"),
	PLUS    ("+", BinOp.ADD, UnOp.POS),
	MINUS   ("-", BinOp.SUB, UnOp.NEG),
	STAR    ("*", BinOp.MUL, UnOp.DEREF),
	SLASH   ("/", BinOp.DIV),
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
	LTE     ("<=", BinOp.LTE),
	GTE     (">=", BinOp.GTE),
	EQ      ("==", BinOp.EQ),
	NEQ     ("!=", BinOp.NEQ),
	BANG    ("!", null, UnOp.LNOT),
	QUEST   ("?"),
	LBRACK  ("[", BinOp.ARR),
	RBRACK  ("]"),
	LBRACE  ("{"),
	RBRACE  ("}"),
	HASH    ("#"),
	DOT     (".", BinOp.DOT),
	REF     ("::", BinOp.REF),
	AT      ("@"),
	TO      ("..", BinOp.TO),
	UNTIL   ("..<", BinOp.UNTIL),
	INC     ("++", null, UnOp.INC_PRE),
	DEC     ("--", null, UnOp.DEC_PRE),
	VARARG  ("..."),
	SET     ("=", BinOp.SET),
	SET_MUL ("*=", BinOp.SET_MUL),
	SET_DIV ("/=", BinOp.SET_DIV),
	SET_ADD ("+=", BinOp.SET_ADD),
	SET_SUB ("-=", BinOp.SET_SUB),
	SET_XOR ("^=", BinOp.SET_XOR),
	SET_OR  ("|=", BinOp.SET_OR),
	SET_AND ("&=", BinOp.SET_AND),
	SET_SHL ("<<=", BinOp.SET_SHL),
	SET_SHR (">>=", BinOp.SET_SHR);

	val isKeyword get() = this < NAME
	val isSym get() = this > EOF

}