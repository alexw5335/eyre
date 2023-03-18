package eyre

import eyre.util.IntList

class Parser(private val context: CompilerContext) {


	private lateinit var srcFile: SrcFile

	private lateinit var tokens: List<Token>

	private var pos = 0

	private var currentScope = Scopes.EMPTY

	private val nodes = ArrayList<AstNode>()

	private var currentNamespace: Namespace? = null // Only single-line namespaces

	private val scopeBuilder = IntList(8)



	/*
	Parsing utils
	 */



	private val next get() = tokens[pos]

	private val prev get() = tokens[pos - 1]

	private fun next() = tokens[pos++]

	private fun atNewline() = srcFile.newlines[pos]

	private fun atTerminator() = srcFile.terminators[pos]

	private fun id() = (tokens[pos++] as? IdToken)?.value ?: error("Expecting identifier, found: $prev")

	private fun SrcPos(offset: Int = 0) = SrcPos(srcFile, srcFile.tokenLines[pos - offset])



	/*
	Nodes and symbol creation
	 */



	private fun SymBase(srcPos: SrcPos, name: Name) = SymBase(srcPos, currentScope, name)



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

		if(token is IdToken) {
			return when(val id = token.value) {
				in Names.registers -> return RegNode(srcPos, Names.registers[id])
				Names.FS -> return SegRegNode(srcPos, SegReg.FS)
				Names.GS -> return SegRegNode(srcPos, SegReg.GS)
				else -> SymNode(srcPos, id)
			}
		}

		if(token is SymToken) {
			if(token == SymToken.LPAREN) {
				val expression = parseExpression()
				expect(SymToken.RPAREN)
				return expression
			}

			return UnaryNode(srcPos, token.unaryOp ?: error(srcPos, "Unexpected symbol: $token"), parseAtom())
		}

		return when(token) {
			is IntToken    -> IntNode(srcPos, token.value)
			is StringToken -> StringNode(srcPos, token.value)
			is CharToken   -> IntNode(srcPos, token.value.code.toLong())
			else           -> error(srcPos, "Unexpected token: $token")
		}
	}



	private fun parseExpression(precedence: Int = 0): AstNode {
		var atom = parseAtom()

		while(true) {
			val token = next

			if(token !is SymToken)
				if(!atTerminator())
					error("Unexpected token: $token")
				else
					break

			if(token == SymToken.SEMICOLON)
				break

			val op = token.binaryOp ?: break
			if(op.precedence < precedence) break
			pos++

			val pos = SrcPos(1)

			val expression = parseExpression(op.precedence + 1)

			atom = when(op) {
				BinaryOp.DOT -> DotNode(
					pos,
					atom,
					expression as? SymNode ?: error("Invalid node")
				)

				BinaryOp.REF -> RefNode(
					pos,
					atom as? SymProviderNode ?: error("Invalid reference"),
					expression as? SymNode ?: error("Invalid node")
				)

				else -> BinaryNode(
					pos,
					op,
					atom,
					expression
				)
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

		if(token is IdToken) {
			if(token.value in Names.widths) {
				width = Names.widths[token.value]
				if(tokens[pos + 1] == SymToken.LBRACKET)
					token = tokens[++pos]
			}
		}

		if(token == SymToken.LBRACKET) {
			pos++
			val value = parseExpression()
			expect(SymToken.RBRACKET)
			return MemNode(SrcPos(2), width, value)
		}

		return (parseExpression() as? OpNode) ?: error("Invalid operand")
	}



	private fun parseInstruction(prefix: Prefix?, mnemonic: Mnemonic): InsNode {
		val srcPos = SrcPos(1)
		if(atNewline() || next == EndToken)
			return InsNode(srcPos, prefix, mnemonic, 0, null, null, null, null)

		val op1 = parseOperand()
		if(next != SymToken.COMMA)
			return InsNode(srcPos, prefix, mnemonic, 1, op1, null, null, null)
		pos++

		val op2 = parseOperand()
		if(next != SymToken.COMMA)
			return InsNode(srcPos, prefix, mnemonic, 2, op1, op2, null, null)
		pos++

		val op3 = parseOperand()
		if(next != SymToken.COMMA)
			return InsNode(srcPos, prefix, mnemonic, 3, op1, op2, op3, null)
		pos++

		val op4 = parseOperand()
		expectTerminator()
		return InsNode(srcPos, prefix, mnemonic, 4, op1, op2, op3, op4)
	}



	/*
	Node parsing
	 */



	private fun parseNamespace() {
		val srcPos    = SrcPos()
		val thisScope = parseScopeName()
		val name      = thisScope.last
		val namespace = Namespace(SymBase(srcPos, currentScope, name), thisScope).add()
		val node      = NamespaceNode(srcPos, namespace)

		if(next == SymToken.LBRACE) {
			pos++
			node.add()
			parseScope(thisScope)
			expect(SymToken.RBRACE)
			ScopeEndNode(SrcPos(), namespace).add()
		} else {
			expectTerminator()
			if(currentNamespace != null)
				ScopeEndNode(SrcPos(), currentNamespace!!).add()
			node.add()
			parseScope(thisScope)
			currentNamespace = namespace
		}
	}



	private fun parseLabel(id: Name) {
		val srcPos = SrcPos(1)
		pos++
		val symbol = LabelSymbol(SymBase(srcPos, currentScope, id)).add()
		LabelNode(srcPos, symbol).add()
	}



	private fun parseConst() {
		val srcPos = SrcPos()
		val name = id()
		expect(SymToken.EQUALS)
		val value = parseExpression()
		val symbol = ConstSymbol(SymBase(srcPos, currentScope, name)).add()
		ConstNode(srcPos, symbol, value).add()
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
		val srcPos = SrcPos()
		val name = id()
		var initialiser = id()

		if(initialiser == Names.RES) {
			val size = parseExpression()
			val symbol = ResSymbol(SymBase(srcPos, currentScope, name)).add()
			ResNode(srcPos, symbol, size).add()
			return
		}

		val parts = ArrayList<VarPart>()
		var size = 0

		while(true) {
			if(initialiser !in Names.varWidths) break
			val width = Names.varWidths[initialiser]
			val values = ArrayList<AstNode>()
			val srcPos2 = SrcPos(1)

			while(true) {
				val component = parseExpression()
				values.add(component)

				size += if(component is StringNode)
					width.bytes * component.value.string.length
				else
					width.bytes

				if(tokens[pos] != SymToken.COMMA) break
				pos++
			}

			parts.add(VarPart(srcPos2, width, values))
			initialiser = (tokens[pos++] as? IdToken)?.value ?: break
		}

		pos--
		if(parts.isEmpty())
			error("Expecting variable initialiser")
		val symbol = VarSymbol(SymBase(srcPos, currentScope, name), size).add()
		VarNode(srcPos, symbol, parts).add()
	}



	private fun parseEnum(isBitmask: Boolean) {
		val enumSrcPos = SrcPos()
		val enumName = id()
		var current = if(isBitmask) 1L else 0L
		val scope = Scopes.add(currentScope, enumName)

		if(tokens[pos] != SymToken.LBRACE) return
		pos++
		if(tokens[pos] == SymToken.RBRACE) return
		val entries = java.util.ArrayList<EnumEntryNode>()
		val entrySymbols = java.util.ArrayList<EnumEntrySymbol>()

		while(pos < tokens.size) {
			if(tokens[pos] == SymToken.RBRACE) break

			val srcPos = SrcPos()
			val name = id()
			val symbol: EnumEntrySymbol
			val node: EnumEntryNode

			val base = SymBase(srcPos, scope, name)
			val token = tokens[pos]

			if(token == SymToken.EQUALS) {
				pos++
				symbol = EnumEntrySymbol(base, entries.size, 0).add()
				symbol.resolved = false
				node = EnumEntryNode(srcPos, symbol, parseExpression())
			} else if(token is IntToken) {
				pos++
				symbol = EnumEntrySymbol(base, entries.size, token.value).add()
				symbol.resolved = true
				node = EnumEntryNode(srcPos, symbol, null)
			} else {
				symbol = EnumEntrySymbol(base, entries.size, current).add()
				symbol.resolved = true
				node = EnumEntryNode(srcPos, symbol, null)
				current += if(isBitmask) current else 1
			}

			entries.add(node)
			entrySymbols.add(symbol)

			if(!atNewline() && next != SymToken.COMMA && next !is IdToken)
				break
		}

		expect(SymToken.RBRACE)
		val symbol = EnumSymbol(SymBase(enumSrcPos, currentScope, enumName), scope, entrySymbols).add()
		EnumNode(enumSrcPos, symbol, entries).add()
	}



	private fun parseHash() {
		val srcPos = SrcPos()

		when(val directive = id()) {
			Names.DEBUG -> {
				val name = next() as? StringToken ?: error("Expecting string literal")
				val symbol = DebugLabelSymbol(SymBase(srcPos, currentScope, name.value))
				DebugLabelNode(srcPos, symbol).add()
				context.debugLabels.add(symbol)
			}
			else -> error("Invalid directive: $directive")
		}
	}



	private fun parseProc() {
		val srcPos = SrcPos()
		val name = id()
		val scope = createScope(name)
		val symbol = ProcSymbol(SymBase(srcPos, currentScope, name), scope).add()
		ProcNode(srcPos, symbol).add()
		expect(SymToken.LBRACE)
		parseScope(scope)
		expect(SymToken.RBRACE)
		ScopeEndNode(SrcPos(), symbol).add()
	}



	private fun parseImport() {
		val srcPos = SrcPos()
		val value = parseExpression()
		if(value !is SymProviderNode) error("Expecting symbol reference")
		ImportNode(srcPos, value).add()
	}



	private fun parseStruct() {
		val structSrcPos = SrcPos()
		val structName = id()
		val scope = createScope(structName)
		val memberSymbols = ArrayList<MemberSymbol>()
		val memberNodes = ArrayList<MemberNode>()
		var structSize = 0

		expect(SymToken.LBRACE)

		while(true) {
			val srcPos = SrcPos()

			val offset = (next() as? IntToken)?.value ?: error("Expecting struct member offset")
			if(offset !in 0..Int.MAX_VALUE) error("Struct member offset out of bounds")

			if(next !is IntToken) {
				structSize = offset.toInt()
				expect(SymToken.RBRACE)
				break
			}

			val size = (next() as? IntToken)?.value ?: error("Expecting struct member size")
			if(size !in 0..Int.MAX_VALUE) error("Struct member size out of bounds")

			val name = id()
			val symbol = MemberSymbol(SymBase(srcPos, scope, name), offset.toInt(), size.toInt()).add()
			val node = MemberNode(srcPos, symbol)
			symbol.resolved = true
			memberSymbols.add(symbol)
			memberNodes.add(node)
			expectTerminator()
		}

		val symbol = StructSymbol(SymBase(structSrcPos, structName), scope, memberSymbols, structSize).add()
		StructNode(structSrcPos, symbol, memberNodes).add()
		for(s in memberSymbols) s.parent = symbol
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
			ScopeEndNode(SrcPos(), currentNamespace!!).add()

		srcFile.nodes = ArrayList(nodes)
	}



	/*
	Scope
	 */



	private fun parseScopeName(): Scope {
		scopeBuilder.reset()

		do {
			scopeBuilder.add(id().id)
		} while(next() == SymToken.PERIOD)

		pos--

		return Scopes.add(currentScope, scopeBuilder.array, scopeBuilder.size)
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
				is IdToken       -> parseId(token.value)
				SymToken.RBRACE  -> { pos--; break }
				SymToken.HASH    -> parseHash()
				EndToken         -> break
				is SymToken      -> if(token != SymToken.SEMICOLON) error(1, "Invalid symbol: ${token.string}")
				else             -> error(1, "Invalid token: $token")
			}
		}
	}


}