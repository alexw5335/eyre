package eyre

class Parser(private val context: CompilerContext) {


	private lateinit var srcFile: SrcFile

	private lateinit var tokens: List<Token>

	private var pos = 0

	private var currentScope = Scopes.EMPTY

	private val nodes = ArrayList<AstNode>()

	private var currentNamespace: Namespace? = null // Only single-line namespaces

	private val nameBuilder = ArrayList<Name>()



	/*
	Parsing utils
	 */



	private val next get() = tokens[pos]

	private val prev get() = tokens[pos - 1]

	private fun next() = tokens[pos++]

	private fun atNewline() = srcFile.newlines[pos]

	private fun atTerminator() = srcFile.terminators[pos]

	private fun id() = tokens[pos++] as? Name ?: error("Expecting identifier, found: $prev")

	private fun SrcPos(offset: Int = 0) = SrcPos(srcFile, srcFile.tokenLines[pos - offset])



	/*
	Nodes and symbol creation
	 */



	private fun SymBase(name: Name) = SymBase(currentScope, name)



	private fun<T : AstNode> T.add(): T {
		nodes.add(this)
		return this
	}

	private fun<T : Symbol> T.add(): T {
		context.symbols.add(this)?.let {
			error("Symbol redeclaration: $name. Original: ${it.scope}.${it.name}, new: $scope.$name")
		}
		return this
	}



	/*
	Errors
	 */



	private fun error(srcPos: SrcPos, message: String): Nothing {
		System.err.println("Parser error at ${srcFile.path}:${srcPos.line}")
		System.err.print('\t')
		System.err.println(message)
		System.err.println()
		throw RuntimeException("Parser error")
	}

	private fun error(offset: Int, message: String): Nothing = error(SrcPos(offset), message)

	private fun error(message: String): Nothing = error(SrcPos(), message)

	private fun expectTerminator() {
		if(!atTerminator())
			error("Expecting terminator")
	}

	private fun expect(symbol: SymToken) {
		if(next() != symbol)
			error("Expecting '${symbol.string}', found: $prev")
	}



	/*
	Expression parsing
	 */



	private fun parseAtom(): AstNode {
		val srcPos = SrcPos()
		val token = next()

		if(token is Name) {
			return when(token) {
				in Names.registers  -> return RegNode(Names.registers[token])
				Names.FS            -> return SegRegNode(SegReg.FS)
				Names.GS            -> return SegRegNode(SegReg.GS)
				else                -> NameNode(token)
			}
		}

		if(token is SymToken) {
			if(token == SymToken.LPAREN) {
				val expression = parseExpression()
				expect(SymToken.RPAREN)
				return expression
			}

			return UnaryNode(token.unaryOp ?: error(srcPos, "Unexpected symbol: $token"), parseAtom())
		}

		return when(token) {
			is IntToken    -> IntNode(token.value)
			is StringToken -> StringNode(token.value)
			is CharToken   -> IntNode(token.value.code.toLong())
			else           -> error(srcPos, "Unexpected token: $token")
		}
	}



	private val AstNode.asSymNode get() = this as? SymNode ?: error("Invalid node")



	private fun parseExpression(precedence: Int = 0): AstNode {
		var atom = parseAtom()

		while(true) {
			val token = next

			val op = (token as? SymToken)?.binaryOp ?: break
			if(op.precedence < precedence) break
			pos++

			if(op == BinaryOp.ARR && next == SymToken.RBRACKET) {
				pos++
				atom = ArrayNode(atom, null)
				continue
			}

			val expression = parseExpression(op.precedence + 1)

			atom = when(op) {
				BinaryOp.ARR -> { expect(SymToken.RBRACKET); ArrayNode(atom, expression) }
				BinaryOp.DOT -> DotNode(atom, expression.asSymNode)
				BinaryOp.REF -> RefNode(atom.asSymNode, expression.asSymNode)
				else         -> BinaryNode(op, atom, expression)
			}
		}

		return atom
	}



	/*
	Instruction parsing
	 */



	private fun parseOperand(): OpNode {
		var token = next
		var width: Width? = null

		if(token is Name) {
			if(token in Names.widths) {
				width = Names.widths[token]
				if(tokens[pos + 1] == SymToken.LBRACKET)
					token = tokens[++pos]
			}
		}

		if(token == SymToken.LBRACKET) {
			pos++
			val value = parseExpression()
			expect(SymToken.RBRACKET)
			return MemNode(width, value)
		}

		return (parseExpression() as? OpNode) ?: error("Invalid operand")
	}



	private fun parseInstruction(prefix: Prefix?, mnemonic: Mnemonic): InsNode {
		if(atNewline() || next == EndToken)
			return InsNode(prefix, mnemonic, 0, null, null, null, null)

		val op1 = parseOperand()
		if(next != SymToken.COMMA)
			return InsNode(prefix, mnemonic, 1, op1, null, null, null)
		pos++

		val op2 = parseOperand()
		if(next != SymToken.COMMA)
			return InsNode(prefix, mnemonic, 2, op1, op2, null, null)
		pos++

		val op3 = parseOperand()
		if(next != SymToken.COMMA)
			return InsNode(prefix, mnemonic, 3, op1, op2, op3, null)
		pos++

		val op4 = parseOperand()
		expectTerminator()
		return InsNode(prefix, mnemonic, 4, op1, op2, op3, op4)
	}



	/*
	Node parsing
	 */



	private fun parseNamespace() {
		val thisScope = parseScopeName()
		val name      = thisScope.last
		val namespace = Namespace(SymBase(name), thisScope).add()
		val node      = NamespaceNode(namespace)

		if(next == SymToken.LBRACE) {
			pos++
			node.add()
			parseScope(thisScope)
			expect(SymToken.RBRACE)
			ScopeEndNode(namespace).add()
		} else {
			expectTerminator()
			if(currentNamespace != null)
				ScopeEndNode(currentNamespace!!).add()
			node.add()
			parseScope(thisScope)
			currentNamespace = namespace
		}
	}



	private fun parseLabel(id: Name) {
		pos++
		val symbol = LabelSymbol(SymBase(id)).add()
		LabelNode(symbol).add()
	}



	private fun parseConst() {
		val name = id()
		expect(SymToken.EQUALS)
		val value = parseExpression()
		expectTerminator()
		val symbol = ConstSymbol(SymBase(name)).add()
		ConstNode(symbol, value).add()
	}



	private fun parseHash() {
		TODO()
	}



	private fun parseId(id: Name) {
		if(next == SymToken.COLON) {
			parseLabel(id)
			return
		}

		if(id in Names.keywords) {
			when(Names.keywords[id]) {
				Keyword.NAMESPACE -> parseNamespace()
				Keyword.VAR       -> parseVar()
				Keyword.CONST     -> parseConst()
				Keyword.ENUM      -> parseEnum(false)
				Keyword.BITMASK   -> parseEnum(true)
				Keyword.TYPEDEF   -> parseTypedef()
				Keyword.PROC      -> parseProc()
				Keyword.IMPORT    -> parseImport()
				Keyword.STRUCT    -> parseStruct()
				else              -> error("Invalid keyword: $id")
			}
			return
		}

		if(id in Names.prefixes) {
			val next = id()
			if(next !in Names.mnemonics) error("Invalid prefix: $next")
			parseInstruction(Names.prefixes[id], Names.mnemonics[next]).add()
			return
		}

		if(id in Names.mnemonics) {
			parseInstruction(null, Names.mnemonics[id]).add()
			return
		}

		error("Unexpected identifier: $id")
	}



	private fun parseVar() {
		val name = id()
		expect(SymToken.COLON)
		val type = parseExpression()

		if(atTerminator()) {
			val symbol = VarResSymbol(SymBase(name)).add()
			VarResNode(symbol, type).add()
			return
		}

		TODO()
	}



	private fun parseEnum(isBitmask: Boolean) {
		val enumName = id()
		val scope = Scopes.add(currentScope, enumName)

		if(tokens[pos] != SymToken.LBRACE) return
		pos++
		if(tokens[pos] == SymToken.RBRACE) return
		val entries = ArrayList<EnumEntryNode>()
		val entrySymbols = ArrayList<EnumEntrySymbol>()

		while(pos < tokens.size) {
			if(tokens[pos] == SymToken.RBRACE) break

			val name = id()
			val symbol = EnumEntrySymbol(SymBase(scope, name), entries.size, 0).add()

			val node = when(val token = tokens[pos]) {
				SymToken.EQUALS -> { pos++; parseExpression() }
				is IntToken     -> { pos++; IntNode(token.value) }
				else            -> null
			}

			entries.add(EnumEntryNode(symbol, node))
			entrySymbols.add(symbol)

			if(!atNewline() && next != SymToken.COMMA && next !is Name)
				break
		}

		expect(SymToken.RBRACE)
		val symbol = EnumSymbol(SymBase(enumName), scope, entrySymbols, isBitmask).add()
		EnumNode(symbol, entries).add()

		for(s in entrySymbols)
			s.parent = symbol
	}



	private fun parseProc() {
		val name = id()
		val scope = createScope(name)
		val symbol = ProcSymbol(SymBase(name), scope).add()
		ProcNode(symbol).add()
		expect(SymToken.LBRACE)
		parseScope(scope)
		expect(SymToken.RBRACE)
		ScopeEndNode(symbol).add()
	}



	private fun parseImport() {
		ImportNode(parseName()).add()
	}



	private fun parseInt(): Int {
		val value = (next() as? IntToken)?.value ?: error("Expecting integer")
		if(value !in 0..Int.MAX_VALUE) error("Signed 32-bit integer out of range")
		return value.toInt()
	}



	private fun parseStruct() {
		val structName = id()
		val scope = createScope(structName)
		val memberSymbols = ArrayList<MemberSymbol>()
		val memberNodes = ArrayList<MemberNode>()
		var structSize = 0

		expect(SymToken.LBRACE)

		var manual = false
		var offset = 0

		while(next != SymToken.RBRACE) {
			if(tokens[pos + 1] == SymToken.COLON) {
				if(memberNodes.isNotEmpty() && !manual)
					error("All members of a manual struct must specify an offset")
				manual = true
				offset = parseInt()
				pos++
			}  else if(tokens[pos + 1] == SymToken.SEMICOLON) {
				if(!manual) error("Struct size can only be specified by manual structs")
				structSize = parseInt()
				pos++
				break
			} else if(manual) {
				error("All members of a manual struct must specify an offset")
			}

			val symbol: MemberSymbol
			val node: MemberNode

			val type = parseExpression()
			val name = id()
			symbol   = MemberSymbol(SymBase(scope, name), offset, 0, VoidType).add()
			node     = MemberNode(symbol, type)

			memberSymbols += symbol
			memberNodes += node
			expectTerminator()
		}

		pos++

		val symbol = StructSymbol(
			SymBase(structName),
			scope,
			memberSymbols,
			structSize,
			manual
		).add()

		for(member in memberSymbols)
			member.parent = symbol

		StructNode(symbol, memberNodes).add()

		for(s in memberSymbols)
			s.parent = symbol
	}



	private fun parseTypedef() {
		val name = id()
		expect(SymToken.EQUALS)
		val value = parseExpression()
		expectTerminator()
		val symbol = TypedefSymbol(SymBase(name), VoidType).add()
		TypedefNode(symbol, value).add()
	}



	/*
	Parsing
	 */



	fun parse(srcFile: SrcFile) {
		this.srcFile = srcFile
		this.tokens = srcFile.tokens
		pos = 0
		currentNamespace = null
		nodes.clear()

		parseScopeInternal()

		if(currentNamespace != null)
			ScopeEndNode(currentNamespace!!).add()

		srcFile.nodes = ArrayList(nodes)
	}



	/*
	Scope
	 */



	private fun parseName(): SymNode {
		if(tokens[pos + 1] != SymToken.PERIOD)
			return NameNode(id())
		nameBuilder.clear()
		do { nameBuilder += id() } while(next() == SymToken.PERIOD)
		pos--
		return NamesNode(nameBuilder.toTypedArray())
	}



	private fun parseScopeName(): Scope {
		var scope = currentScope

		do {
			scope = Scopes.add(scope, id())
		} while(next() == SymToken.PERIOD)

		pos--

		return scope
	}



	private fun parseScope(scope: Scope) {
		val prevScope = currentScope
		currentScope = scope
		parseScopeInternal()
		currentScope = prevScope
	}



	private fun createScope(name: Name) =
		Scopes.add(currentScope, name)



	private fun parseScopeInternal() {
		while(pos < tokens.size) {
			when(val token = next()) {
				is Name          -> parseId(token)
				SymToken.RBRACE  -> { pos--; break }
				SymToken.HASH    -> parseHash()
				EndToken         -> break
				is SymToken      -> if(token != SymToken.SEMICOLON) error(1, "Invalid symbol: ${token.string}")
				else             -> error(1, "Invalid token: $token")
			}
		}
	}


}