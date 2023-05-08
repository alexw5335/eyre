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
		add(EndToken)
		newlines.ensureCapacity(tokens.size)
		terminators.ensureCapacity(tokens.size)

		srcFile.tokens      = tokens
		srcFile.tokenLines  = tokenLines
		srcFile.newlines    = newlines
		srcFile.terminators = terminators
	}



	private fun add(token: Token) {
		tokens.add(token)
		tokenLines.add(lineCount)
		tokenLines[tokens.size] = lineCount
	}



	private fun addTerm(token: Token) {
		terminators.set(tokens.size)
		add(token)
	}



	private fun addAdv(symbol: Token) {
		add(symbol)
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

		add(StringToken(stringBuilder.toString()))
	}



	private fun resolveSingleApostrophe() {
		var char = chars[pos++]

		if(char == '\\')
			char = chars[pos++].escape

		add(CharToken(char))

		if(chars[pos++] != '\'')
			lexerError("Unterminated char literal")
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
			if(radix != 10) lexerError("Malformed number")
			add(FloatToken(string.toFloatOrNull()?.toDouble() ?: lexerError("Malformed float")))
		} else if(hasDotOrExponent) {
			if(radix != 10) lexerError("Malformed number")
			add(FloatToken(string.toDoubleOrNull() ?: lexerError("Malformed double")))
		} else {
			add(IntToken(string.toLongOrNull(radix) ?: lexerError("Malformed integer")))
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
			in 'A'..'Z' -> lexerError("Invalid number character: $next")
			else        -> { pos-- }
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

		val name = Names.add(String(chars, startPos, pos - startPos))

		if(name in Names.registers)
			add(RegToken(Names.registers[name]))
		else
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
			charMap[')'] = { addTerm(SymToken.RPAREN) }
			charMap['+'] = { add(SymToken.PLUS) }
			charMap['-'] = { add(SymToken.MINUS) }
			charMap['*'] = { add(SymToken.ASTERISK) }
			charMap['['] = { add(SymToken.LBRACKET) }
			charMap[']'] = { addTerm(SymToken.RBRACKET) }
			charMap['{'] = { add(SymToken.LBRACE) }
			charMap['}'] = { addTerm(SymToken.RBRACE) }
			charMap['.'] = { add(SymToken.PERIOD) }
			charMap[';'] = { addTerm(SymToken.SEMICOLON) }
			charMap['^'] = { add(SymToken.CARET) }
			charMap['~'] = { add(SymToken.TILDE) }
			charMap[','] = { add(SymToken.COMMA) }
			charMap['?'] = { add(SymToken.QUESTION) }
			charMap['#'] = { add(SymToken.HASH) }

			// Compound symbols
			charMap['&'] = { when(chars[pos]) {
				'&'  -> addAdv(SymToken.LOGIC_AND)
				else -> add(SymToken.AMPERSAND)
			}}
			charMap['|'] = { when(chars[pos]) {
				'|'  -> addAdv(SymToken.LOGIC_OR)
				else -> add(SymToken.PIPE)
			}}
			charMap[':'] = { when(chars[pos]) {
				':'  -> addAdv(SymToken.REFERENCE)
				else -> add(SymToken.COLON)
			}}
			charMap['<'] = { when(chars[pos]) {
				'<'  -> addAdv(SymToken.SHL)
				'='  -> addAdv(SymToken.LTE)
				else -> add(SymToken.LT)
			}}
			charMap['='] = { when(chars[pos]) {
				'='  -> addAdv(SymToken.EQUALITY)
				else -> add(SymToken.EQUALS)
			}}
			charMap['!'] = { when(chars[pos]) {
				'='  -> addAdv(SymToken.INEQUALITY)
				else -> SymToken.EXCLAMATION
			}}
			charMap['>'] = { when(chars[pos]) {
				'>' -> when(chars[++pos]) {
					'>'  -> addAdv(SymToken.SAR)
					else -> add(SymToken.SHR)
				}
				'='  -> addAdv(SymToken.GTE)
				else -> addTerm(SymToken.GT)
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
					charMap[i] = { lexerError("Invalid char code: $i") }
		}
	}



}