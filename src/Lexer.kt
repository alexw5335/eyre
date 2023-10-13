package eyre

import java.nio.file.Files

class Lexer(val context: Context) {


	private lateinit var srcFile: SrcFile

	private var chars = CharArray(0)

	private var size = 0

	private var lineCount = 0

	private var pos = 0

	private var stringBuilder = StringBuilder()

	private val Char.isIdentifierPart get() = isLetterOrDigit() || this == '_'

	private var hasError = false



	fun lex(srcFile: SrcFile) {
		pos = 0
		lineCount = 1

		this.srcFile = srcFile
		size = Files.size(srcFile.path).toInt() + 1
		if(chars.size <= size)
			chars = CharArray(size * 2)
		Files.newBufferedReader(srcFile.path).use {
			it.read(chars, 0, size)
		}
		chars[size] = Char(0)

		try {
			while(!srcFile.invalid) {
				val char = chars[pos++]
				if(char.code == 0) break
				charMap[char.code]!!()
			}
		} catch(_: EyreError) {
			srcFile.invalid = true
		}

		srcFile.lineCount = lineCount
		add(SymToken.NEWLINE)
	}



	private fun add(token: Token) = srcFile.tokens.add(token)



	private fun addAdv(symbol: Token) {
		add(symbol)
		pos++
	}



	private fun err(message: String): Nothing =
		context.err(SrcPos(srcFile, lineCount), message)



	private fun onNewline() {
		add(SymToken.NEWLINE)
		lineCount++
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

		while(!hasError) {
			when(val char = chars[pos++]) {
				Char(0) -> err("Unterminated string literal")
				'\n'    -> err("Newline not allowed in string literal")
				'"'     -> break
				'\\'    -> stringBuilder.append(chars[pos++].escape)
				else    -> stringBuilder.append(char)
			}
		}

		add(StringToken(stringBuilder.toString()))
	}



	private fun resolveSingleApostrophe() {
		var char = chars[pos++]

		if(char == '\\')
			char = chars[pos++].escape

		add(CharToken(char))

		if(chars[pos++] != '\'')
			err("Unterminated character literal")
	}



	private fun resolveSlash() {
		if(chars[pos] == '/') {
			while(pos < size && chars[++pos] != '\n') Unit
			return
		}

		if(chars[pos] != '*') {
			add(SymToken.SLASH)
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



	private fun number(radix: Int) {
		val start = pos

		var hasDotOrExponent = false

		while(true) {
			when(chars[pos]) {
				'.',
				'e' -> { pos++; hasDotOrExponent = true }
				'_',
				in '0'..'9',
				in 'A'..'Z',
				in 'a'..'z' -> pos++
				else -> break
			}
		}

		val string = String(chars, start, pos - start)

		if(string.last() == 'f' || string.last() == 'F' && radix != 16) {
			if(radix != 10)
				err("Malformed float")

			add(FloatToken(string.toFloatOrNull()?.toDouble() ?: 0.0.also { err("Malformed float") }))
		} else if(hasDotOrExponent) {
			if(radix != 10)
				err("Malformed float")
			add(FloatToken(string.toDoubleOrNull() ?: 0.0.also { err("Malformed float") }))
		} else {
			add(IntToken(string.toLongOrNull(radix) ?: 0L.also { err("Malformed integer") }))
		}
	}



	private fun zero() {
		when(val next = chars[pos++]) {
			'b', 'B'    -> number(2)
			'o', 'O'    -> number(8)
			'd', 'D'    -> number(10)
			'x', 'X'    -> number(16)
			'f', 'F'    -> add(FloatToken(0.0))
			'.'         -> { pos -= 2; number(10) }
			in '0'..'9' -> number(10)
			in 'a'..'z',
			in 'A'..'Z' -> err("Invalid number character: $next")
			else        -> { pos -= 2; number(10) }
		}
	}



	private fun digit() {
		pos--
		number(10)
	}



	private fun idStart() {
		pos--

		val startPos = pos

		while(true) {
			val char = chars[pos]
			if(!char.isIdentifierPart) break
			pos++
		}

		val name = Name.add(String(chars, startPos, pos - startPos))

		Name.regs[name]?.let {
			add(RegToken(it))
			return
		}

		add(name)
	}



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
			charMap['('] = { add(SymToken.LPAREN) }
			charMap[')'] = { add(SymToken.RPAREN) }
			charMap['+'] = { add(SymToken.PLUS) }
			charMap['-'] = { add(SymToken.MINUS) }
			charMap['*'] = { add(SymToken.STAR) }
			charMap['['] = { add(SymToken.LBRACKET) }
			charMap[']'] = { add(SymToken.RBRACKET) }
			charMap['{'] = { add(SymToken.LBRACE) }
			charMap['}'] = { add(SymToken.RBRACE) }
			charMap['.'] = { add(SymToken.PERIOD) }
			charMap[';'] = { add(SymToken.SEMI) }
			charMap['^'] = { add(SymToken.CARET) }
			charMap['~'] = { add(SymToken.TILDE) }
			charMap[','] = { add(SymToken.COMMA) }
			charMap['?'] = { add(SymToken.QUESTION) }
			charMap['#'] = { add(SymToken.HASH) }

			// Compound symbols
			charMap['&'] = { when(chars[pos]) {
				'&'  -> addAdv(SymToken.LAND)
				else -> add(SymToken.AMP)
			}}
			charMap['|'] = { when(chars[pos]) {
				'|'  -> addAdv(SymToken.LOR)
				else -> add(SymToken.PIPE)
			}}
			charMap[':'] = { when(chars[pos]) {
				':'  -> addAdv(SymToken.REF)
				else -> add(SymToken.COLON)
			}}
			charMap['<'] = { when(chars[pos]) {
				'<'  -> addAdv(SymToken.SHL)
				'='  -> addAdv(SymToken.LTE)
				else -> add(SymToken.LT)
			}}
			charMap['='] = { when(chars[pos]) {
				'='  -> addAdv(SymToken.EQ)
				else -> add(SymToken.SET)
			}}
			charMap['!'] = { when(chars[pos]) {
				'='  -> addAdv(SymToken.INEQ)
				else -> SymToken.BANG
			}}
			charMap['>'] = { when(chars[pos]) {
				'>' -> when(chars[++pos]) {
					'>'  -> addAdv(SymToken.SAR)
					else -> add(SymToken.SHR)
				}
				'='  -> addAdv(SymToken.GTE)
				else -> add(SymToken.GT)
			}}

			// Complex symbols
			charMap['"']  = Lexer::resolveDoubleApostrophe
			charMap['\''] = Lexer::resolveSingleApostrophe
			charMap['/']  = Lexer::resolveSlash

			// Identifiers
			charMap['_']  = Lexer::idStart
			for(i in 65..90)  charMap[i] = Lexer::idStart
			for(i in 97..122) charMap[i] = Lexer::idStart

			// Number literals
			charMap['0']  = Lexer::zero
			for(i in 49..57)  charMap[i] = Lexer::digit

			// Invalid chars
			for(i in charMap.indices)
				if(charMap[i] == null)
					charMap[i] = { err("Invalid char code: $i") }
		}
	}



}