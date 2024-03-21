package eyre

class Lexer(private val context: Context) {


	var tokens = ArrayList<Token>(); private set

	var lineCount = 0; private set

	private lateinit var file: SrcFile

	private var pos = 0

	private var chars = CharArray(0)

	private var size = 0

	private val stringBuilder = StringBuilder()



	fun lex(file: SrcFile) {
		this.pos = 0
		this.lineCount = 1
		this.file = file

		size = file.codeSize() + 1
		if(chars.size <= size)
			chars = CharArray(size * 2)
		file.readCode(chars)
		chars[size] = Char(0)

		try {
			while(true) {
				val char = chars[pos++]
				if(char.code == 0) break
				charMap[char.code]!!()
			}
		} catch(error: EyreError) {
			file.invalid = true
			context.errors.add(error)
		}

		file.lineCount = lineCount
		add(Token(TokenType.EOF, lineCount + 1))
	}



	/*
	Util
	 */



	private fun err(message: String): Nothing {
		throw EyreError(SrcPos(file, lineCount), message)
	}

	private val Char.isNamePart get() =
		isLetterOrDigit() || this == '_'

	private fun add(token: Token) {
		tokens.add(token)
	}

	private fun add(symbol: TokenType) {
		tokens.add(Token(symbol, lineCount))
	}

	private fun addAdv(symbol: TokenType) {
		tokens.add(Token(symbol, lineCount))
		pos++
	}



	/*
	Lexing
	 */



	private fun name() {
		val startPos = pos - 1 // Assuming that first char has been skipped
		while(chars[pos].isNamePart) pos++
		val name = Name[String(chars, startPos, pos - startPos)]
		if(name.keyword != null)
			add(name.keyword!!)
		else
			add(Token(TokenType.NAME, lineCount, nameValue = name))
	}



	private fun number() {
		val startPos = pos - 1// Assuming that first char has been skipped
		while(chars[pos].isNamePart) pos++ // TODO: Fix this
		val string = String(chars, startPos, pos - startPos)
		val value = string.toLongOrNull(10) ?: err("Malformed integer: $string")
		add(Token(TokenType.INT, lineCount, intValue = value))
	}



	private fun resolveSlash() {
		if(chars[pos] == '/') {
			while(pos < size && chars[++pos] != '\n') Unit
			return
		}

		if(chars[pos] != '*') {
			add(TokenType.SLASH)
			return
		}

		var count = 1

		while(count > 0) {
			if(pos >= chars.size)
				err("Unterminated multiline comment")

			val char = chars[pos++]

			if(char == '/' && chars[pos] == '*') {
				count++
				pos++
			} else if(char == '*' && chars[pos] == '/') {
				count--
				pos++
			} else if(char == '\n') {
				lineCount++
			}
		}
	}



	private val Char.escape get() = when(this) {
		't'  -> '\t'
		'n'  -> '\n'
		'\\' -> '\\'
		'r'  -> '\r'
		'b'  -> '\b'
		'"'  -> '"'
		'\'' -> '\''
		'0'  -> Char(0)
		else -> err("Invalid escape char: $this")
	}



	private fun resolveDoubleApostrophe() {
		stringBuilder.clear()

		while(pos < size) {
			when(val char = chars[pos++]) {
				Char(0) -> err("Unterminated string literal")
				'\n'    -> err("Newline not allowed in string literal")
				'"'     -> break
				'\\'    -> stringBuilder.append(chars[pos++].escape)
				else    -> stringBuilder.append(char)
			}
		}

		add(Token(TokenType.STRING, lineCount, stringValue = stringBuilder.toString()))
	}



	private fun resolveSingleApostrophe() {
		var char = chars[pos++]

		if(char == '\\')
			char = chars[pos++].escape

		add(Token(TokenType.CHAR, lineCount, intValue = char.code.toLong()))

		if(chars[pos++] != '\'')
			err("Unterminated character literal")
	}



	/*
	Char map
	 */



	companion object {

		private val charMap = arrayOfNulls<Lexer.() -> Unit>(255)

		private operator fun<T> Array<T>.set(char: Char, value: T) = set(char.code, value)

		init {
			// Whitespace
			charMap['\n'] = { lineCount++ }
			charMap[' ']  = { }
			charMap['\t'] = { }
			charMap['\r'] = { }

			// Single symbols
			charMap['('] = { add(TokenType.LPAREN) }
			charMap[')'] = { add(TokenType.RPAREN) }
			charMap['+'] = { add(TokenType.PLUS) }
			charMap['-'] = { add(TokenType.MINUS) }
			charMap['*'] = { add(TokenType.STAR) }
			charMap['['] = { add(TokenType.LBRACK) }
			charMap[']'] = { add(TokenType.RBRACK) }
			charMap['{'] = { add(TokenType.LBRACE) }
			charMap['}'] = { add(TokenType.RBRACE) }
			charMap[';'] = { add(TokenType.SEMI) }
			charMap['^'] = { add(TokenType.CARET) }
			charMap['~'] = { add(TokenType.TILDE) }
			charMap[','] = { add(TokenType.COMMA) }
			charMap['?'] = { add(TokenType.QUEST) }
			charMap['#'] = { add(TokenType.HASH) }
			charMap['@'] = { add(TokenType.AT) }

			// Compound symbols
			charMap['&'] = { when(chars[pos]) {
				'&'  -> addAdv(TokenType.LAND)
				else -> add(TokenType.AMP)
			}}
			charMap['|'] = { when(chars[pos]) {
				'|'  -> addAdv(TokenType.LOR)
				else -> add(TokenType.PIPE)
			}}
			charMap[':'] = { when(chars[pos]) {
				':'  -> addAdv(TokenType.REF)
				else -> add(TokenType.COLON)
			}}
			charMap['<'] = { when(chars[pos]) {
				'<'  -> addAdv(TokenType.SHL)
				'='  -> addAdv(TokenType.LTE)
				else -> add(TokenType.LT)
			}}
			charMap['='] = { when(chars[pos]) {
				'='  -> addAdv(TokenType.EQ)
				else -> add(TokenType.SET)
			}}
			charMap['!'] = { when(chars[pos]) {
				'='  -> addAdv(TokenType.NEQ)
				else -> TokenType.BANG
			}}
			charMap['>'] = { when(chars[pos]) {
				'>' -> when(chars[++pos]) {
					'>'  -> addAdv(TokenType.SAR)
					else -> add(TokenType.SHR)
				}
				'='  -> addAdv(TokenType.GTE)
				else -> add(TokenType.GT)
			}}
			charMap['.'] = { when(chars[pos]) {
				'.' -> {
					pos++
					when(chars[pos]) {
						'<' -> addAdv(TokenType.UNTIL)
						else -> add(TokenType.TO)
					}
				}
				else -> add(TokenType.DOT)
			}}

			// Complex symbols
			charMap['"'] = Lexer::resolveDoubleApostrophe
			charMap['\''] = Lexer::resolveSingleApostrophe
			charMap['/'] = Lexer::resolveSlash

			// Names
			charMap['_'] = Lexer::name
			for(i in 'A'..'Z') charMap[i] = Lexer::name
			for(i in 'a'..'z') charMap[i] = Lexer::name

			// Numbers
			for(i in '0'..'9') charMap[i] = Lexer::number
			for(i in charMap.indices)
				if(charMap[i] == null)
					charMap[i] = { err("Invalid char code: $i") }
		}

	}


}