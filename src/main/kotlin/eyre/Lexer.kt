package eyre

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

		addToken(StringToken(StringInterner.add(stringBuilder.toString())))
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
				in '0'..'9' -> char.code - 48L
				in 'a'..'z' -> char.code - 75L
				in 'A'..'Z' -> char.code - 107L
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
		val intern = StringInterner.add(string)
		addToken(IdToken(intern))
	}



	companion object {

		private val charMap = arrayOfNulls<Lexer.() -> Unit>(255)

		private operator fun<T> Array<T>.set(char: Char, value: T) = set(char.code, value)

		init {
			charMap['\n'] = Lexer::onNewline
			charMap[' ']  = { }
			charMap['\t'] = { }
			charMap['\r'] = { }

			for(s in SymToken.values) {
				val firstChar = s.string[0]

				if(s.string.length == 1) {
					charMap[firstChar] = {
						terminators.set(tokens.size)
						addToken(s)
					}
					continue
				}

				val secondChar = s.string[1]

				charMap[firstChar] = {
					terminators.set(tokens.size)
					if(chars[pos] == secondChar) {
						addToken(s)
						pos++
					} else
						addToken(s.firstSymbol ?: lexerError("Invalid symbol"))
				}
			}

			charMap['"']  = Lexer::resolveDoubleApostrophe
			charMap['\''] = Lexer::resolveSingleApostrophe
			charMap['/']  = Lexer::resolveSlash
			charMap['_']  = Lexer::idStart
			charMap['0']  = Lexer::zero
			for(i in 65..90)  charMap[i] = Lexer::idStart
			for(i in 97..122) charMap[i] = Lexer::idStart
			for(i in 49..57)  charMap[i] = Lexer::digit

			for(i in charMap.indices)
				if(charMap[i] == null)
					charMap[i] = { lexerError("Invalid char code: $i") }
		}
	}



}