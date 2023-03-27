package eyre

import eyre.util.BitList
import eyre.util.IntList
import java.nio.file.Files
import java.util.*

class Lexer {


	private lateinit var srcFile: SrcFile

	private var chars = CharArray(0)

	private var size = 0
	
	private var lineCount = 0
	
	private var pos = 0

	private var stringBuilder = StringBuilder()

	private var tokens = ArrayList<Token>()

	private var tokenLines = IntList(512)

	private var terminators = BitList(512)

	private var newlines = BitList(512)

	private val Char.isIdentifierPart get() = isLetterOrDigit() || this == '_'



	fun lex(srcFile: SrcFile) {
		pos = 0
		lineCount = 1
		terminators.array.clear()
		newlines.array.clear()
		tokens.clear()

		this.srcFile = srcFile
		size = Files.size(srcFile.path).toInt() + 1

		Arrays.fill(chars, Char(0))

		if(chars.size <= size)
			chars = CharArray(size * 2)

		Files.newBufferedReader(srcFile.path).use {
			it.read(chars, 0, size)
		}

		chars[size] = Char(0)

		while(true) {
			val char = chars[pos++]
			if(char.code == 0) break
			charMap[char.code]!!()
		}

		terminators.set(tokens.size)
		addToken(EndToken)
		newlines.ensureCapacity(tokens.size)
		terminators.ensureCapacity(tokens.size)

		srcFile.tokens      = tokens
		srcFile.tokenLines  = tokenLines
		srcFile.newlines    = newlines
		srcFile.terminators = terminators
	}



	private fun addToken(token: Token) {
		tokens.add(token)
		tokenLines.add(lineCount)
		tokenLines[tokens.size] = lineCount
	}



	private fun addSymbol(symbol: SymToken) {
		terminators.set(tokens.size)
		addToken(symbol)
	}



	private fun addSymbolAdv(symbol: SymToken) {
		addSymbol(symbol)
		pos++
	}



	private fun lexerError(string: String): Nothing {
		System.err.println("Lexer error at ${srcFile.path}:$lineCount")
		System.err.print('\t')
		System.err.println(string)
		System.err.println()
		error("Lexer error")
	}



	private fun onNewline() {
		terminators.set(tokens.size)
		newlines.set(tokens.size)
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
		else -> lexerError("Invalid escape char: $this")
	}



	private fun resolveDoubleApostrophe() {
		stringBuilder.clear()

		while(true) {
			when(val char = chars[pos++]) {
				Char(0) -> lexerError("Unterminated string literal")
				'\n'    -> lexerError("Newline not allowed in string literal")
				'"'     -> break
				'\\'    -> stringBuilder.append(chars[pos++].escape)
				else    -> stringBuilder.append(char)
			}
		}

		addToken(StringToken(Names.add(stringBuilder.toString())))
	}



	private fun resolveSingleApostrophe() {
		var char = chars[pos++]

		if(char == '\\')
			char = chars[pos++].escape

		addToken(CharToken(char))

		if(chars[pos++] != '\'')
			lexerError("Unterminated char literal")
	}



	private fun resolveSlash() {
		if(chars[pos] == '/') {
			while(pos < size && chars[++pos] != '\n') Unit
			return
		}

		if(chars[pos] != '*') {
			addToken(SymToken.SLASH)
			return
		}

		var count = 1

		while(count > 0) {
			if(pos >= chars.size)
				lexerError("Unterminated multiline comment")

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

	private fun readBinary(): Long {
		var value = 0L

		while(true) {
			val mask = when(chars[pos++]) {
				'0'  -> 0L
				'1'  -> 1L
				'_'  -> continue
				else -> break
			}

			if(value and (1L shl 63) != 0L)
				lexerError("Integer literal out of range")
			value = (value shl 1) or mask
		}

		pos--
		return value
	}



	private fun readDecimal(): Long {
		var value = 0L

		while(true) {
			val mask = when(val char = chars[pos++]) {
				in '0'..'9' -> char.code - 48L
				'_'         -> continue
				else        -> break
			}

			if(value and (0xFFL shl 56) != 0L)
				lexerError("Integer literal out of range")
			value = (value * 10) + mask
		}

		pos--
		return value
	}



	private fun readHex(): Long {
		var value = 0L

		while(true) {
			val mask = when(val char = chars[pos++]) {
				'_'         -> continue
				in '0'..'9' -> char.code - '0'.code.toLong()
				in 'a'..'z' -> char.code - 'a'.code.toLong() + 10
				in 'A'..'Z' -> char.code - 'A'.code.toLong() + 10
				else        -> break
			}

			if(value and (0b1111L shl 60) != 0L)
				lexerError("Integer literal out of range")
			value = (value shl 4) or mask
		}

		pos--
		return value
	}



	private fun digit() {
		pos--
		addToken(IntToken(readDecimal()))
		if(chars[pos].isLetterOrDigit()) lexerError("Invalid char in number literal: '${chars[pos]}'")
	}



	private fun zero() {
		if(chars[pos].isDigit()) {
			addToken(IntToken(readDecimal()))
			return
		}

		if(!chars[pos].isLetter()) {
			addToken(IntToken(0))
			return
		}

		when(val base = chars[pos++]) {
			'x'  -> addToken(IntToken(readHex()))
			'b'  -> addToken(IntToken(readBinary()))
			else -> lexerError("Invalid integer base: $base")
		}
	}



	private fun idStart() {
		pos--

		val startPos = pos

		while(true) {
			val char = chars[pos]
			if(!char.isIdentifierPart) break
			pos++
		}

		val string = String(chars, startPos, pos - startPos)
		addToken(Names.add(string))
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
			charMap['('] = { addSymbol(SymToken.LPAREN) }
			charMap[')'] = { addSymbol(SymToken.RPAREN) }
			charMap['+'] = { addSymbol(SymToken.PLUS) }
			charMap['-'] = { addSymbol(SymToken.MINUS) }
			charMap['*'] = { addSymbol(SymToken.ASTERISK) }
			charMap['['] = { addSymbol(SymToken.LBRACKET) }
			charMap[']'] = { addSymbol(SymToken.RBRACKET) }
			charMap['{'] = { addSymbol(SymToken.LBRACE) }
			charMap['}'] = { addSymbol(SymToken.RBRACE) }
			charMap['.'] = { addSymbol(SymToken.PERIOD) }
			charMap[';'] = { addSymbol(SymToken.SEMICOLON) }
			charMap['^'] = { addSymbol(SymToken.CARET) }
			charMap['~'] = { addSymbol(SymToken.TILDE) }
			charMap[','] = { addSymbol(SymToken.COMMA) }
			charMap['?'] = { addSymbol(SymToken.QUESTION) }
			charMap['#'] = { addSymbol(SymToken.HASH) }

			// Compound symbols
			charMap['&'] = { when(chars[pos]) {
				'&'  -> addSymbolAdv(SymToken.LOGIC_AND)
				else -> addSymbol(SymToken.AMPERSAND)
			} }
			charMap['|'] = { when(chars[pos]) {
				'|'  -> addSymbolAdv(SymToken.LOGIC_OR)
				else -> addSymbol(SymToken.PIPE)
			} }
			charMap[':'] = { when(chars[pos]) {
				':'  -> addSymbolAdv(SymToken.REFERENCE)
				else -> addSymbol(SymToken.COLON)
			} }
			charMap['<'] = { when(chars[pos]) {
				'<'  -> addSymbolAdv(SymToken.SHL)
				'='  -> addSymbolAdv(SymToken.LTE)
				else -> addSymbol(SymToken.LT)
			} }
			charMap['='] = { when(chars[pos]) {
				'='  -> addSymbolAdv(SymToken.EQUALITY)
				else -> addSymbol(SymToken.EQUALS)
			} }
			charMap['!'] = { when(chars[pos]) {
				'='  -> addSymbolAdv(SymToken.INEQUALITY)
				else -> SymToken.EXCLAMATION
			} }
			charMap['>'] = { when(chars[pos]) {
				'>' -> when(chars[++pos]) {
					'>'  -> addSymbolAdv(SymToken.SAR)
					else -> addSymbol(SymToken.SHR)
				}
				'='  -> addSymbolAdv(SymToken.GTE)
				else -> addSymbol(SymToken.GT)
			} }

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
					charMap[i] = { lexerError("Invalid char code: $i") }
		}
	}



}