package eyre

import java.lang.RuntimeException

class Parser(private val context: CompilerContext) {


	private lateinit var srcFile: SrcFile

	private lateinit var nodes: MutableList<AstNode>

	private lateinit var tokens: List<Token>

	private var pos = 0

	private val atNewline get() = srcFile.newlines[pos]

	private val atTerminator get() = srcFile.terminators[pos]

	private fun srcPos() = SrcPos(srcFile, srcFile.tokenLines[pos])

	private var currentScope = Scopes.EMPTY

	private fun id() = tokens[pos++] as? Name ?: parserError(1, "Expecting identifier")



	private fun parserError(srcPos: SrcPos, message: String): Nothing {
		context.errors.add(EyreError(srcPos, message))
		error(message)
	}



	private fun parserError(offset: Int, message: String): Nothing =
		parserError(SrcPos(srcFile, srcFile.tokenLines[pos - offset]), message)




	private fun<T : AstNode> T.add(): T {
		nodes.add(this)
		return this
	}



	private class ParserException : RuntimeException()



	/*
	Parsing
	 */



	fun parse(srcFile: SrcFile) {
		this.srcFile = srcFile
		this.nodes = srcFile.nodes
		this.tokens = srcFile.tokens
		pos = 0

		try {
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
		} catch(_: ParserException) {
			srcFile.invalid = true
		}
	}



	/*
	Symbol parsing. Pos is always at the start of the name
	 */



	private fun parseLabel(name: Name) {
		val srcPos = srcPos()
		pos += 2
		Label(srcPos, currentScope, name).add()
	}



	private fun parseNamespace() {
		val srcPos = srcPos()
		val name = id()
		Namespace(srcPos, currentScope, name, currentScope).add()
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
				else              -> context.internalError()
			}
		} else if(name in Names.mnemonics) {

		} else {
			parserError(0, "Invalid identifier: $name")
		}
	}



}