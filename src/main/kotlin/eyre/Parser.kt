package eyre

class Parser(private val context: CompilerContext) {


	private lateinit var srcFile: SrcFile

	private lateinit var nodes: MutableList<AstNode>

	private lateinit var tokens: List<Token>

	private var currentNamespace: Namespace? = null

	private var pos = 0

	private val atNewline get() = srcFile.newlines[pos]

	private val atTerminator get() = srcFile.terminators[pos]

	private fun srcPos() = SrcPos(srcFile, srcFile.tokenLines[pos])

	private var currentScope = Scopes.EMPTY

	private fun id() = tokens[pos++] as? Name ?: err(1, "Expecting identifier")

	private fun err(srcPos: SrcPos, message: String): Nothing =
		context.err(srcPos, message)

	private fun err(offset: Int, message: String): Nothing =
		err(SrcPos(srcFile, srcFile.tokenLines[pos - offset]), message)

	private fun AstNode.addNode() = nodes.add(this)

	private fun<T> T.addSym() where T : AstNode, T : Symbol {
		context.symbols.add(this)?.let {
			err(srcPos ?: context.internalError(), "Symbol redeclaration: $qualifiedName")
		}
	}

	private fun<T> T.addNodeSym(srcPos: SrcPos): T where T : AstNode, T : Symbol {
		this.srcPos = srcPos
		nodes.add(this)
		context.symbols.add(this)?.let {
			err(srcPos, "Symbol redeclaration: $qualifiedName")
		}
		return this
	}

	private fun expect(symbol: SymToken) {
		if(tokens[pos++] != symbol)
			err(1, "Expecting '${symbol.string}'")
	}

	private fun expectTerminator() {
		if(!atTerminator)
			err(0, "Expecting terminator")
	}

	private val nameBuilder = ArrayList<Name>()

	private val arraySizes = ArrayList<AstNode>()



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
		} catch(_: EyreException) {
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
				is SymToken        -> err(1, "Invalid symbol: ${token.string}")
				else               -> err(1, "Invalid token: $token")
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
		Label(currentScope, name).addNodeSym(srcPos)
	}



	private fun parseTypedef() {
		val srcPos = srcPos()
		val name = id()
		expect(SymToken.EQUALS)
		val typeNode = parseType()
		expectTerminator()
		Typedef(currentScope, name, typeNode).addNodeSym(srcPos)
	}



	private fun parseType(): TypeNode {
		val srcPos = srcPos()
		var name: Name? = null
		var names: Array<Name>? = null

		if(tokens[pos + 1] != SymToken.PERIOD) {
			name = id()
		} else {
			nameBuilder.clear()
			do { nameBuilder += id() } while(tokens[pos++] == SymToken.PERIOD)
			pos--
			names = nameBuilder.toTypedArray()
		}

		while(tokens[pos] == SymToken.LBRACKET) {
			pos++
			arraySizes.add(parseExpression())
			expect(SymToken.RBRACKET)
		}

		val node = if(arraySizes.isNotEmpty()) {
			val sizes = arraySizes.toTypedArray()
			arraySizes.clear()
			TypeNode(name, names, sizes)
		} else {
			TypeNode(name, names, null)
		}

		node.srcPos = srcPos

		return node
	}



	private fun parseNamespace() {
		if(currentNamespace != null)
			err(0, "Only one single-line namespace allowed per file")
		val srcPos = srcPos()
		val thisScope = parseScopeName()
		val namespace = Namespace(currentScope, thisScope.last, thisScope).addNodeSym(srcPos)
		currentScope = thisScope
		currentNamespace = namespace
	}



	private fun parseProc() {
		val srcPos = srcPos()
		val name = id()
		val thisScope = Scopes.add(currentScope, name)

		val parts = ArrayList<AstNode>()

		if(tokens[pos] == SymToken.LPAREN) {
			pos++

			while(true) {
				if(tokens[pos] == SymToken.RPAREN) break
				parts.add(parseExpression())
				if(tokens[pos] != SymToken.COMMA) break
				pos++
			}

			expect(SymToken.RPAREN)
		}

		val proc = Proc(currentScope, name, thisScope, parts).addNodeSym(srcPos)

		expect(SymToken.LBRACE)
		parseScope()
		expect(SymToken.RBRACE)
		ScopeEnd(proc).addNode()
	}



	private fun parseVar(isVal: Boolean) {
		val srcPos = srcPos()
		val name = id()

		val typeNode = if(tokens[pos] == SymToken.COLON) {
			pos++
			parseType()
		} else {
			null
		}

		val first = tokens[pos]

		if(first is Name && first in Names.varWidths) {
			val parts = ArrayList<VarDb.Part>()
			var size = 0

			while(true) {
				val width = Names.varWidths[id()]
				val nodes = ArrayList<AstNode>()

				while(true) {
					val component = parseExpression()
					nodes.add(component)

					size += when(component) {
						is StringNode -> width.bytes * component.value.length
						else -> width.bytes
					}

					if(tokens[pos] != SymToken.COMMA) break
					pos++
				}

				parts.add(VarDb.Part(width, nodes))
				if(tokens[pos] !is Name || tokens[pos] as Name !in Names.varWidths)
					break
			}

			if(parts.isEmpty()) err(srcPos, "Empty initialiser")
			val node = VarDb(currentScope, name, typeNode, parts).addNodeSym(srcPos)
			node.section = if(isVal) Section.RDATA else Section.DATA
			expectTerminator()
			return
		} else if(atTerminator) {
			val node = VarRes(currentScope, name, typeNode).addNodeSym(srcPos)
			node.section = Section.BSS
		} else {
			err(srcPos, "Expecting variable value")
		}
	}



	private fun parseConst() {
		val srcPos = srcPos()
		val name = id()
		expect(SymToken.EQUALS)
		val value = parseExpression()
		val const = Const(currentScope, name, value).addNodeSym(srcPos)
		context.unorderedNodes.add(const)
		expectTerminator()
	}



	private fun parseStruct() {
		val srcPos = srcPos()
		val structName = id()
		val thisScope = Scopes.add(currentScope, structName)
		val members = ArrayList<Member>()
		val struct = Struct(currentScope, structName, thisScope, members).addNodeSym(srcPos)

		expect(SymToken.LBRACE)

		while(tokens[pos] != SymToken.RBRACE) {
			val type = parseType()
			val name = id()
			members.add(Member(thisScope, name, struct, type))
			expectTerminator()
		}

		pos++
	}



	private fun parseEnum() {
		val srcPos = srcPos()
		val name = id()
		val thisScope = Scopes.add(currentScope, name)
		val entries = ArrayList<EnumEntry>()
		val enum = Enum(currentScope, name, thisScope, entries).addNodeSym(srcPos)

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

			val enumEntry = EnumEntry(thisScope, entryName, enum, entryValueNode)
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

		fun invalidToken(): Nothing = err(1, "unexpected token: $token")

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
			left = BinaryNode(op, left, expression)
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
				Keyword.STRUCT    -> parseStruct()
				Keyword.CONST     -> parseConst()
				Keyword.VAR       -> parseVar(false)
				Keyword.VAL       -> parseVar(true)
				Keyword.ENUM      -> parseEnum()
				Keyword.NAMESPACE -> parseNamespace()
				Keyword.PROC      -> parseProc()
				Keyword.TYPEDEF   -> parseTypedef()
				else              -> context.internalError()
			}
		} else if(name in Names.mnemonics) {
			parseInstruction(Names.mnemonics[name])
		} else {
			err(0, "Invalid identifier: $name")
		}
	}


}