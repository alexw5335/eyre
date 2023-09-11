package eyre

import java.lang.RuntimeException

class Parser(private val context: CompilerContext) {


	private class ParserException : RuntimeException()

	private lateinit var srcFile: SrcFile

	private lateinit var nodes: MutableList<AstNode>

	private lateinit var tokens: List<Token>

	private var currentNamespace: Namespace? = null

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

	private fun AstNode.addNode() = nodes.add(this)

	private fun<T> T.addSym() where T : AstNode, T : Symbol {
		context.symbols.add(this)?.let {
			parserError(srcPos ?: context.internalError(), "Symbol redeclaration: $qualifiedName")
		}
	}

	private fun expect(symbol: SymToken) {
		if(tokens[pos++] != symbol)
			parserError(1, "Expecting '${symbol.string}'")
	}

	private fun expectTerminator() {
		if(!atTerminator)
			parserError(0, "Expecting terminator")
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

		if(currentNamespace != null)
			ScopeEnd(currentNamespace).addNode()
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
		val label = Label(currentScope, name)
		label.srcPos = srcPos
		label.addNode()
		label.addSym()
	}



	private fun parseNamespace() {
		if(currentNamespace != null)
			parserError(0, "Only one single-line namespace allowed per file")

		val srcPos = srcPos()
		val thisScope = parseScopeName()
		val namespace = Namespace(currentScope, thisScope.last, thisScope)
		namespace.srcPos = srcPos
		namespace.addNode()
		namespace.addSym()
		currentScope = thisScope
		currentNamespace = namespace
	}



	private fun parseProc() {
		val srcPos = srcPos()
		val name = id()
		val thisScope = Scopes.add(currentScope, name)
		val proc = Proc(currentScope, name, thisScope)
		proc.srcPos = srcPos
		proc.addNode()
		proc.addSym()
		expect(SymToken.LBRACE)
		parseScope()
		expect(SymToken.RBRACE)
		ScopeEnd(proc).addNode()
	}



	private fun parseEnum() {
		val srcPos = srcPos()
		val name = id()
		val thisScope = Scopes.add(currentScope, name)
		val entries = ArrayList<EnumEntry>()
		val enum = Enum(currentScope, name, thisScope, entries)
		enum.srcPos = srcPos
		enum.addNode()
		enum.addSym()

		expect(SymToken.LBRACE)

		while(pos < tokens.size) {
			if(tokens[pos] == SymToken.RBRACE) break
			val entrySrcPos = srcPos()
			val entryName = id()
			val entryValueNode = when(val token = tokens[pos]) {
				SymToken.EQUALS -> { pos++; parseExpression() }
				is IntToken -> { pos++; IntNode(token.value) }
				else -> null
			}

			val enumEntry = EnumEntry(thisScope, entryName, entryValueNode)
			enumEntry.srcPos = entrySrcPos
			entries.add(enumEntry)
			enumEntry.addSym()

			if(tokens[pos] == SymToken.COMMA)
				pos++
			else if(!atNewline && tokens[pos] !is Name)
				break
		}

		expect(SymToken.RBRACE)
	}



	private fun parseAtom(): AstNode {
		val srcPos = srcPos()
		val token = tokens[pos++]

		fun invalidToken(): Nothing = parserError(1, "unexpected token: $token")

		val node = when(token) {
			is SymToken -> if(token == SymToken.LPAREN)
				parseExpression().also { expect(SymToken.RPAREN) }
			else
				UnaryNode(token.unaryOp ?: invalidToken(), parseAtom())

			is RegToken    -> RegNode(token.value)
			is Name        -> NameNode(token)
			is IntToken    -> IntNode(token.value)
			is StringToken -> StringNode(token.value)
			is CharToken   -> IntNode(token.value.code.toLong())
			is FloatToken  -> FloatNode(token.value)
			else           -> invalidToken()
		}

		node.srcPos = srcPos
		return node
	}



	private fun parseExpression(precedence: Int = 0): AstNode {
		var left = parseAtom()

		while(true) {
			val token = tokens[pos]
			val op = (token as? SymToken)?.binaryOp ?: break
			if(op.precedence < precedence) break
			pos++
			val expression = parseExpression(op.precedence + 1)
			val srcPos = left.srcPos

			left = when(op) {
				//BinaryOp.SET -> EqualsNode(atom, expression)
				//BinaryOp.ARR -> { expect(SymToken.RBRACKET); ArrayNode(atom.asSymNode, expression) }
				//BinaryOp.DOT -> DotNode(left, expression)
				//BinaryOp.REF -> RefNode(left, expression)
				else         -> BinaryNode(op, left, expression)
			}

			left.srcPos = srcPos
		}

		return left
	}



	private fun parseOperand(): OpNode {
		val srcPos = srcPos()
		var token = tokens[pos]
		var width: Width? = null

		if(token is Name && token in Names.widths) {
			width = Names.widths[token]
			pos++
			if(!atTerminator)
				token = tokens[pos]
		}

		if(token == SymToken.LBRACKET) {
			pos++
			val value = parseExpression()
			expect(SymToken.RBRACKET)
			return OpNode.mem(width, value).also { it.srcPos = srcPos }
		}

		return when(val expression = parseExpression()) {
			is RegNode -> if(width != null)
					error("Width specifier not allowed")
				else
					OpNode.reg(expression.value).also { it.srcPos = srcPos }
			else ->
				OpNode.imm(width, expression).also { it.srcPos = srcPos }
		}
	}



	private fun parseInstruction(mnemonic: Mnemonic) {
		val srcPos = srcPos()
		pos++

		fun inner(): InsNode {
			if(atNewline || tokens[pos] == EndToken)
				return InsNode(mnemonic, OpNode.NULL, OpNode.NULL, OpNode.NULL, OpNode.NULL)

			val op1 = parseOperand()
			if(tokens[pos] != SymToken.COMMA)
				return InsNode(mnemonic, op1, OpNode.NULL, OpNode.NULL, OpNode.NULL)
			pos++

			val op2 = parseOperand()
			if(tokens[pos] != SymToken.COMMA)
				return InsNode(mnemonic, op1, op2, OpNode.NULL, OpNode.NULL,)
			pos++

			val op3 = parseOperand()
			if(tokens[pos] != SymToken.COMMA)
				return InsNode(mnemonic, op1, op2, op3, OpNode.NULL)
			pos++

			val op4 = parseOperand()
			expectTerminator()
			return InsNode(mnemonic, op1, op2, op3, op4)
		}

		val node = inner()
		node.srcPos = srcPos
		node.addNode()
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
				Keyword.ENUM      -> parseEnum()
				Keyword.NAMESPACE -> parseNamespace()
				Keyword.PROC      -> parseProc()
				else              -> context.internalError()
			}
		} else if(name in Names.mnemonics) {
			parseInstruction(Names.mnemonics[name])
		} else {
			parserError(0, "Invalid identifier: $name")
		}
	}


}