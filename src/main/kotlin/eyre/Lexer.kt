package eyre

import java.nio.file.Files

class Lexer {


	private lateinit var srcFile: SrcFile

	private var chars = CharArray(0)

	private var size = 0
	
	private var lineCount = 0
	
	private var pos = 0

	private var stringBuilder = StringBuilder()

	private val Char.isIdentifierPart get() = isLetterOrDigit() || this == '_'



	var terminators = BitList()
		private set

	var newlines = BitList()
		private set

	var tokenLines = ShortArray(256)
		private set

	var tokens = LongArray(256)
		private set

	var tokenCount = 0
		private set



	private fun addToken(type: TokenType, value: Int) {
		if(tokenCount >= tokenLines.size) {
			tokens = tokens.copyOf(tokenCount shl 2)
			tokenLines = tokenLines.copyOf(tokenCount shl 2)
		}

		tokens[tokenCount] = type.ordinal.toLong() or (value.toLong() shl 32)
		tokenLines[tokenCount++] = lineCount.toShort()
	}



	private fun addSym(symbol: SymToken) = addToken(TokenType.SYM, symbol.ordinal)



	fun lex(srcFile: SrcFile) {
		pos = 0
		tokenCount = 0
		lineCount = 1
		terminators.array.clear()
		newlines.array.clear()

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

		terminators.set(tokenCount)
		addToken(TokenType.END, 0)
		newlines.ensureCapacity(tokenCount)
		terminators.ensureCapacity(tokenCount)
	}



	private fun lexerError(string: String): Nothing {
		System.err.println("Lexer error at ${srcFile.path}:$lineCount")
		System.err.print('\t')
		System.err.println(string)
		System.err.println()
		error("Lexer error")
	}



	private fun onNewline() {
		terminators.set(tokenCount)
		newlines.set(tokenCount)
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

		addToken(TokenType.STRING, StringInterner.add(stringBuilder.toString()).id)
	}



	private fun resolveSingleApostrophe() {
		var char = chars[pos++]

		if(char == '\\')
			char = chars[pos++].escape

		addToken(TokenType.CHAR, char.code)

		if(chars[pos++] != '\'')
			lexerError("Unterminated char literal")
	}



	private fun resolveSlash() {
		if(chars[pos] == '/') {
			while(pos < size && chars[++pos] != '\n') Unit
			return
		}

		if(chars[pos] != '*') {
			addSym(SymToken.SLASH)
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
		addToken(TokenType.INT, readDecimal().toInt())
		if(chars[pos].isLetterOrDigit()) lexerError("Invalid char in number literal: '${chars[pos]}'")
	}



	private fun zero() {
		if(chars[pos].isDigit()) {
			addToken(TokenType.INT, readDecimal().toInt())
			return
		}

		if(!chars[pos].isLetter()) {
			addToken(TokenType.INT, 0)
			return
		}

		when(val base = chars[pos++]) {
			'x'  -> addToken(TokenType.INT, readHex().toInt())
			'b'  -> addToken(TokenType.INT, readBinary().toInt())
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
		addToken(TokenType.ID, intern.id)
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
						terminators.set(tokenCount)
						addToken(TokenType.SYM, s.ordinal)
					}
					continue
				}

				val secondChar = s.string[1]

				charMap[firstChar] = {
					terminators.set(tokenCount)
					if(chars[pos] == secondChar) {
						addSym(s)
						pos++
					} else
						addSym(s.firstSymbol ?: lexerError("Invalid symbol"))
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