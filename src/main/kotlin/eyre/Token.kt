package eyre



@JvmInline
value class Token(val backing: Long) {

	constructor(type: Int, value: Int) : this(type.toLong() or (value.toLong() shl 32))

	constructor(type: TokenType, value: Int) : this(type.ordinal, value)

	constructor(sym: SymToken) : this(TokenType.SYM, sym.ordinal)

	inline val typeValue get() = (backing and 0xFF).toInt()

	inline val value get() = (backing shr 32).toInt()

	inline val type get() = TokenType.values[typeValue]

	inline val symbol get() = SymToken.values[value]

	inline val isEnd    get() = backing and 0xFF == 0L
	inline val isInt    get() = backing and 0XFF == 1L
	inline val isLong   get() = backing and 0xFF == 2L
	inline val isChar   get() = backing and 0xFF == 3L
	inline val isString get() = backing and 0xFF == 4L
	inline val isId     get() = backing and 0xFF == 5L
	inline val isSym    get() = backing and 0xFF > 5

	override fun toString() = when(type) {
		TokenType.END    -> "END"
		TokenType.CHAR   -> "CHAR    ${Char(value)}"
		TokenType.STRING -> "STRING  ${StringInterner[value]}"
		TokenType.ID     -> "ID      ${StringInterner[value]}"
		TokenType.INT    -> "INT     $value"
		TokenType.SYM    -> "SYM     ${symbol.string}"
		else             -> error("Invalid token: $type")
	}

}

object Tokens {
	val SEMICOLON = Token(SymToken.SEMICOLON)
}



enum class SymToken(
	val string      : String,
	val binaryOp    : BinaryOp? = null,
	val unaryOp     : UnaryOp?  = null,
	val firstSymbol : SymToken? = null
) {

	LEFT_PAREN    ("("),
	RIGHT_PAREN   (")"),
	PLUS          ("+", binaryOp = BinaryOp.ADD, unaryOp = UnaryOp.POS),
	MINUS         ("-", binaryOp = BinaryOp.SUB, unaryOp = UnaryOp.NEG),
	ASTERISK      ("*", binaryOp = BinaryOp.MUL),
	SLASH         ("/", binaryOp = BinaryOp.DIV),
	EQUALS        ("="),
	COMMA         (","),
	SEMICOLON     (";"),
	COLON         (":"),
	PIPE          ("|", binaryOp = BinaryOp.OR),
	AMPERSAND     ("&", binaryOp = BinaryOp.AND),
	TILDE         ("~", unaryOp = UnaryOp.NOT),
	CARET         ("^", binaryOp = BinaryOp.XOR),
	LEFT_ANGLE    ("<"),
	RIGHT_ANGLE   (">"),
	LEFT_SHIFT    ("<<", binaryOp = BinaryOp.SHL, firstSymbol = LEFT_ANGLE),
	RIGHT_SHIFT   (">>", binaryOp = BinaryOp.SHR, firstSymbol = RIGHT_ANGLE),
	LEFT_BRACKET  ("["),
	RIGHT_BRACKET ("]"),
	LEFT_BRACE    ("{"),
	RIGHT_BRACE   ("}"),
	PERIOD        (".", binaryOp = BinaryOp.DOT),
	REFERENCE     ("::", firstSymbol = COLON);

	companion object { val values = values() }
}



enum class TokenType {

	END,
	INT,
	LONG,
	CHAR,
	STRING,
	ID,
	SYM;

	companion object { val values = values() }
	
}