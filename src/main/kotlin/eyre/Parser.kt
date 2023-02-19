package eyre

import java.util.*

class Parser {


	private lateinit var srcFile: SrcFile

	private val lexer = Lexer()

	private var pos = 0

	private var scopeStack = IntArray(64)

	private var scopeStackSize = 0

	private var currentScope: Scope = ScopeInterner.GLOBAL



	/*
	Parsing utils
	 */



	private val next get() = Token(lexer.tokens[pos])

	private val prev get() = Token(lexer.tokens[pos - 1])



	fun parse(srcFile: SrcFile) {
		this.srcFile = srcFile
		lexer.lex(srcFile)
	}



	private fun parseScope(scope: Scope) {
		val prev = currentScope
		currentScope = scope

		while(true) {
			val token = next

			if(token == Token(SymToken.SEMICOLON)) {
				pos++
				continue
			}

			if(token == Token(SymToken.RIGHT_BRACE)) {
				break
			}

			if(token == Token(TokenType.END, 0)) {
				pos++
				break
			}

/*			if(token == )

			if(type == TokenType.SYM) {
				if(token == SymToken.SEMICOLON.ordinal) {
					pos++

				}
				val sym = token.symbol

				if(sym == SymToken.SEMICOLON) {
					pos++
				} else if(sym == SymToken.RIGHT_BRACE) {
					break
				} else {

				}
			}*/
		}
	}



	fun addScope(name: StringIntern): Scope {
		if(scopeStackSize >= scopeStack.size)
			scopeStack = scopeStack.copyOf(scopeStackSize * 2)
		scopeStack[scopeStackSize++] = name.id
		val hash = currentScope.hash * 31 + name.id
		return ScopeInterner.add(scopeStack.copyOf(scopeStackSize), hash)
	}






}