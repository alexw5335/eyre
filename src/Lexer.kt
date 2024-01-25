package eyre

import java.nio.file.Files

class Lexer(private val context: Context) {


	private var pos = 0

	private var lineCount = 0

	private lateinit var file: SrcFile

	private var chars = CharArray(0)

	private var size = 0

	private var stringBuilder = StringBuilder()



	fun lex() {
		for(s in context.files)
			if(!s.invalid)
				lex(s)
	}



	private fun lex(file: SrcFile) {
		this.pos = 0
		this.lineCount = 1
		this.file = file

		size = Files.size(file.path).toInt() + 1
		if(chars.size <= size)
			chars = CharArray(size * 2)
		Files.newBufferedReader(file.path).use { it.read(chars, 0, size) }
		chars[size] = Char(0)

		try {
			while(true) {
				val char = chars[pos++]
				if(char.code == 0) break
				charMap[char.code]!!()
			}
		} catch(_: EyreError) {
			file.invalid = true
		}

		file.lineCount = lineCount
		lineCount++
		add(TokenType.EOF)
	}



	/*
	Util
	 */



	private fun err(message: String): Nothing = context.err(message, SrcPos(file, lineCount))

	private val Char.isNamePart get() = isLetterOrDigit() || this == '_'

	private fun add(type: TokenType, value: Int) {
		file.tokens.add(Token(type, value, lineCount))
	}

	private fun add(symbol: TokenType) {
		file.tokens.add(Token(symbol, 0, lineCount))
	}

	private fun addAdv(symbol: TokenType) {
		file.tokens.add(Token(symbol, 0, lineCount))
		pos++
	}



	/*
	Lexing
	 */



	private fun onNewline() {
		lineCount++
	}



	private fun name() {
		pos--
		val startPos = pos

		while(true) {
			val char = chars[pos]
			if(!char.isNamePart) break
			pos++
		}

		val name = Names[String(chars, startPos, pos - startPos)]
		add(TokenType.NAME, name.id)
	}



	private fun number() {
		pos--
		var size = 0
		while(chars[pos + size].isNamePart) size++
		val string = String(chars, pos, size)
		val value = string.toIntOrNull(10) ?: err("Malformed integer: $string")
		add(TokenType.INT, value)
		pos += size
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
				onNewline()
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

		add(TokenType.STRING, context.strings.size)
		context.strings.add(stringBuilder.toString())
	}



	private fun resolveSingleApostrophe() {
		var char = chars[pos++]

		if(char == '\\')
			char = chars[pos++].escape

		add(TokenType.CHAR, char.code)

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
			charMap['\n'] = Lexer::onNewline
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
			charMap['.'] = { add(TokenType.DOT) }
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

			// Complex symbols
			charMap['"'] = Lexer::resolveDoubleApostrophe
			charMap['\''] = Lexer::resolveSingleApostrophe
			charMap['/'] = Lexer::resolveSlash

			// Names
			charMap['_'] = Lexer::name
			for(i in 'Z'..'Z') charMap[i] = Lexer::name
			for(i in 'a'..'z') charMap[i] = Lexer::name

			// Numbers
			for(i in '0'..'9') charMap[i] = Lexer::number
			for(i in charMap.indices)
				if(charMap[i] == null)
					charMap[i] = { err("Invalid char code: $i") }
		}

	}



}