package eyre

import java.lang.RuntimeException

class Parser(private val context: CompilerContext) {


	private class ParserException : RuntimeException()

	private enum class State {
		NONE, PROC;
	}




	private lateinit var srcFile: SrcFile

	private lateinit var nodes: MutableList<AstNode>

	private lateinit var tokens: List<Token>

	private var state = State.NONE

	private var pos = 0

	private val atNewline get() = srcFile.newlines[pos]

	private val atTerminator get() = srcFile.terminators[pos]

	private fun srcPos() = SrcPos(srcFile, srcFile.tokenLines[pos])

	private var currentScope = Scopes.EMPTY

	private fun id() = tokens[pos++] as? Name ?: parserError(1, "Expecting identifier")



	private fun parserError(srcPos: SrcPos, message: String): Nothing {
		context.errors.add(EyreError(srcPos, message))
		throw ParserException()
	}



	private fun parserError(offset: Int, message: String): Nothing =
		parserError(SrcPos(srcFile, srcFile.tokenLines[pos - offset]), message)




	private fun<T : AstNode> T.add(): T {
		nodes.add(this)
		return this
	}




	/*
	Parsing
	 */



	fun parse(srcFile: SrcFile) {
		this.srcFile = srcFile
		this.nodes = srcFile.nodes
		this.tokens = srcFile.tokens
		pos = 0

		try {
			parseScope()
		} catch(_: ParserException) {
			srcFile.invalid = true
		}
	}



	private fun parseScope() {
		while(true) {
			when(val token = tokens[pos]) {
				is Name            -> parseName(token)
				SymToken.RBRACE    -> break
				SymToken.HASH      -> { }
				EndToken           -> break
				SymToken.SEMICOLON -> pos++
				is SymToken        -> parserError(1, "Invalid symbol: ${token.string}")
				else               -> parserError(1, "Invalid token: $token")
			}
		}
	}




	/*
	Symbol parsing. Pos is always at the start of the name
	 */



	private fun parseScopeName(): Scope {
		var scope = Scopes.add(currentScope, id())

		while(tokens[pos] == SymToken.PERIOD) {
			pos++
			scope = Scopes.add(scope, id())
		}

		return scope
	}



	private fun parseLabel(name: Name) {
		val srcPos = srcPos()
		pos += 2
		Label(srcPos, currentScope, name).add()
	}



	private fun parseNamespace() {
		val srcPos = srcPos()
		val thisScope = parseScopeName()
		Namespace(srcPos, currentScope, thisScope.last, thisScope).add()
		currentScope = thisScope
	}



	private fun parseProc() {
		val srcPos = srcPos()
		val name = id()
		val thisScope = Scopes.add(currentScope, name)
		val node = Proc(srcPos, currentScope, name, thisScope).add()
		if(tokens[pos++] != SymToken.LBRACE) parserError(1, "Expecting opening brace (${node.qualifiedName})")
		parseScope()
		if(tokens[pos++] != SymToken.RBRACE) parserError(1, "No closing brace (${node.qualifiedName})")
	}



	/**
	 * Pos is before the [name] token.
	 */
	private fun parseName(name: Name) {
		if(tokens[pos+1] == SymToken.COLON) {
			parseLabel(name)
			return
		}

		if(name in Names.keywords) {
			pos++
			when(Names.keywords[name]) {
				Keyword.NAMESPACE -> parseNamespace()
				Keyword.PROC      -> parseProc()
				else              -> context.internalError()
			}
		} else if(name in Names.mnemonics) {

		} else {
			parserError(0, "Invalid identifier: $name")
		}
	}



}