package eyre

class Parser(private val context: Context) {


	private lateinit var srcFile: SrcFile

	private lateinit var nodes: MutableList<Node>

	private lateinit var tokens: List<Token>

	private var currentNamespace: Namespace? = null

	private var pos = 0

	private val atNewline get() = srcFile.newlines[pos]

	private val atTerminator get() = srcFile.terminators[pos]

	private fun srcPos() = SrcPos(srcFile, srcFile.tokenLines[pos])

	private var currentScope = Scopes.EMPTY

	private fun id() = tokens[pos++] as? Name ?: err(1, "Expecting identifier")

	private fun err(srcPos: SrcPos?, message: String): Nothing =
		context.err(srcPos, message)

	private fun err(offset: Int, message: String): Nothing =
		err(SrcPos(srcFile, srcFile.tokenLines[pos - offset]), message)

	private fun Node.addNode() = nodes.add(this)

	private fun<T> T.addSym() where T : Node, T : Sym {
		context.symbols.add(this)?.let {
			err(srcPos ?: context.internalError(), "Symbol redeclaration: $qualifiedName")
		}
	}

	private fun<T> T.addNodeSym(srcPos: SrcPos): T where T : Node, T : Sym {
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

	private val arraySizes = ArrayList<Node>()



	/*
	Parsing
	 */



	fun parse(srcFile: SrcFile) {
		this.srcFile = srcFile
		this.nodes   = srcFile.nodes
		this.tokens  = srcFile.tokens

		pos = 0
		currentNamespace = null
		currentScope = Scopes.EMPTY

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
				SymToken.HASH      -> parseDirective()
				EndToken           -> break
				SymToken.SEMI -> pos++
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
		Label(currentScope.base(name)).addNodeSym(srcPos)
	}



	private fun parseTypedef() {
		val srcPos = srcPos()
		val name = id()
		expect(SymToken.EQUALS)
		val typeNode = parseType()
		expectTerminator()
		Typedef(currentScope.base(name), typeNode).addNodeSym(srcPos)
	}



	private fun parseNames(): Array<Name> {
		nameBuilder.clear()
		do { nameBuilder += id() } while(tokens[pos++] == SymToken.PERIOD)
		pos--
		return nameBuilder.toTypedArray()
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
		val namespace = Namespace(currentScope.base(thisScope, thisScope.last)).addNodeSym(srcPos)
		currentScope = thisScope
		currentNamespace = namespace
	}



	private fun parseProc() {
		val srcPos = srcPos()
		val name = id()
		val thisScope = Scopes.add(currentScope, name)

		val parts = ArrayList<Node>()

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

		val proc = Proc(currentScope.base(thisScope, name), parts).addNodeSym(srcPos)

		expect(SymToken.LBRACE)
		val prev = currentScope
		currentScope = thisScope
		parseScope()
		currentScope = prev
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

		val value = when {
			tokens[pos] == SymToken.EQUALS -> { pos++; parseExpression() }
			atTerminator -> null
			else -> err(srcPos, "Expecting variable value")
		}

		val varNode = Var(currentScope.base(name), typeNode, value, isVal).addNodeSym(srcPos)

		if(varNode.valueNode == null && typeNode == null)
			err(srcPos, "Expecting variable value and/or type")
	}



	private fun parseConst() {
		val srcPos = srcPos()
		val name = id()
		expect(SymToken.EQUALS)
		val value = parseExpression()
		val const = Const(currentScope.base(name), value).addNodeSym(srcPos)
		context.unorderedNodes.add(const)
		expectTerminator()
	}



	private fun parseDirective() {
		pos++
		val srcPos = srcPos()
		if(atNewline) err(srcPos, "Invalid directive")
		val name = id()
		val values = if(atTerminator) {
			emptyList()
		} else {
			val values = ArrayList<Node>()

			do {
				values.add(parseExpression())
				if(tokens[pos] == SymToken.COMMA) pos++
			} while(!atNewline)

			values
		}

		val node = Directive(name, values)
		node.srcPos = srcPos
		node.addNode()
	}



	private fun Scope.base(name: Name) = Base().also {
		it.scope = this
		it.name = name
	}



	private fun Scope.base(thisScope: Scope, name: Name) = Base().also {
		it.scope = this
		it.thisScope = thisScope
		it.name = name
	}



	private fun parseStruct() {
		val srcPos = srcPos()
		val structName = id()
		val thisScope = Scopes.add(currentScope, structName)
		val members = ArrayList<Member>()
		val struct = Struct(currentScope.base(thisScope, structName), members).addNodeSym(srcPos)
		context.unorderedNodes.add(struct)
		expect(SymToken.LBRACE)

		while(tokens[pos] != SymToken.RBRACE) {
			val memberSrcPos = srcPos()
			val typeNode = parseType()
			val name = id()
			val member = Member(thisScope.base(name), typeNode)
			member.srcPos = memberSrcPos
			member.parent = struct
			members.add(member)
			member.addSym()
			expectTerminator()
		}

		pos++
	}



	private fun parseEnum(isBitmask: Boolean) {
		val srcPos = srcPos()
		val name = id()
		val thisScope = Scopes.add(currentScope, name)
		val entries = ArrayList<EnumEntry>()
		val enum = Enum(currentScope.base(thisScope, name), entries, isBitmask).addNodeSym(srcPos)

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

			val enumEntry = EnumEntry(thisScope.base(entryName), entryValueNode)
			enumEntry.parent = enum
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



	private fun parseImport() {
		val srcPos = srcPos()
		val node = ImportNode(parseNames())
		node.srcPos = srcPos
		node.addNode()
	}



	private fun parseAtom(): Node {
		val srcPos = srcPos()
		val token = tokens[pos++]

		fun invalidToken(): Nothing = err(1, "unexpected token: $token")

		val node: Node = when(token) {
			is SymToken -> when(token) {
				SymToken.LPAREN ->
					parseExpression().also { expect(SymToken.RPAREN) }

				SymToken.LBRACE -> {
					val entries = ArrayList<Node>()
					while(true) {
						if(tokens[pos] == SymToken.RBRACE) break
						entries.add(parseExpression())
						if(atNewline) continue
						if(tokens[pos] != SymToken.COMMA) break
						pos++
					}
					pos++
					InitNode(entries)
				}

				SymToken.LBRACKET ->
					IndexNode(parseExpression()).also { expect(SymToken.RBRACKET) }

				else ->
					UnNode(token.unOp ?: invalidToken(), parseAtom())
			}

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



	private fun parseExpression(precedence: Int = 0): Node {
		var left = parseAtom()

		while(true) {
			val token = tokens[pos]
			val op = (token as? SymToken)?.binOp ?: break
			if(op.precedence < precedence) break
			pos++
			val expression = parseExpression(op.precedence + 1)

			fun asName() = (expression as? NameNode)?.value ?: err(expression.srcPos, "Expecting name")

			left = when(op) {
				//BinaryOp.SET -> EqualsNode(atom, expression)
				BinOp.ARR -> ArrayNode(left, expression).also { expect(SymToken.RBRACKET) }
				BinOp.DOT -> DotNode(left, asName())
				BinOp.REF -> ReflectNode(left, asName())
				else         -> BinNode(op, left, expression)
			}

			left.srcPos = expression.srcPos
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

		fun inner(): Ins {
			if(atNewline || tokens[pos] == EndToken)
				return Ins(mnemonic, OpNode.NULL, OpNode.NULL, OpNode.NULL, OpNode.NULL)

			val op1 = parseOperand()
			if(tokens[pos] != SymToken.COMMA)
				return Ins(mnemonic, op1, OpNode.NULL, OpNode.NULL, OpNode.NULL)
			pos++

			val op2 = parseOperand()
			if(tokens[pos] != SymToken.COMMA)
				return Ins(mnemonic, op1, op2, OpNode.NULL, OpNode.NULL,)
			pos++

			val op3 = parseOperand()
			if(tokens[pos] != SymToken.COMMA)
				return Ins(mnemonic, op1, op2, op3, OpNode.NULL)
			pos++

			val op4 = parseOperand()
			expectTerminator()
			return Ins(mnemonic, op1, op2, op3, op4)
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
				Keyword.ENUM      -> parseEnum(false)
				Keyword.BITMASK   -> parseEnum(true)
				Keyword.NAMESPACE -> parseNamespace()
				Keyword.PROC      -> parseProc()
				Keyword.TYPEDEF   -> parseTypedef()
				Keyword.IMPORT    -> parseImport()
				else              -> context.internalError()
			}
		} else if(name in Names.mnemonics) {
			parseInstruction(Names.mnemonics[name])
		} else {
			err(0, "Invalid identifier: $name")
		}
	}


}