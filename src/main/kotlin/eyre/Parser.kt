package eyre

import eyre.util.IntList

class Parser(private val context: CompilerContext) {


	private lateinit var srcFile: SrcFile

	private lateinit var tokens: List<Token>

	private var pos = 0

	private var currentScope = ScopeInterner.EMPTY

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
				in StringInterner.registers -> return RegNode(srcPos, StringInterner.registers[id])
				StringInterner.FS -> return SegRegNode(srcPos, SegReg.FS)
				StringInterner.GS -> return SegRegNode(srcPos, SegReg.GS)
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
			if(token.value in StringInterner.widths) {
				width = StringInterner.widths[token.value]
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
			ScopeEndNode(SrcPos()).add()
		} else {
			expectTerminator()
			if(currentNamespace != null)
				ScopeEndNode(SrcPos()).add()
			node.add()
			parseScope(thisScope)
			currentNamespace = namespace
		}
	}



	private fun parseLabel(id: StringIntern) {
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



	private fun parseId(id: StringIntern) {
		if(next == SymToken.COLON) {
			parseLabel(id)
			return
		}

		if(id in StringInterner.keywords) {
			when(StringInterner.keywords[id]) {
				Keyword.NAMESPACE -> parseNamespace()
				Keyword.VAR       -> parseVar()
				Keyword.CONST     -> parseConst()
				Keyword.ENUM      -> parseEnum(false)
				Keyword.BITMASK   -> parseEnum(true)
				Keyword.PROC      -> parseProc()
				else              -> error("Invalid keyword: $id")
			}
			return
		}

		if(id in StringInterner.prefixes) {
			val next = id()
			if(next !in StringInterner.mnemonics) error("Invalid prefix: $next")
			parseInstruction(StringInterner.prefixes[id], StringInterner.mnemonics[next]).add()
			return
		}

		if(id in StringInterner.mnemonics) {
			parseInstruction(null, StringInterner.mnemonics[id]).add()
			return
		}

		error("Unexpected identifier: $id")
	}



	private fun parseVar() {
		val srcPos = SrcPos()
		val name = id()
		var initialiser = id()

		if(initialiser == StringInterner.RES) {
			val size = parseExpression()
			val symbol = ResSymbol(SymBase(srcPos, currentScope, name)).add()
			ResNode(srcPos, symbol, size).add()
			return
		}

		val parts = ArrayList<VarPart>()
		var size = 0

		while(true) {
			if(initialiser !in StringInterner.varWidths) break
			val width = StringInterner.varWidths[initialiser]
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
		val scope = ScopeInterner.add(currentScope, enumName)

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

			if(tokens[pos] == SymToken.EQUALS) {
				pos++
				symbol = EnumEntrySymbol(base, entries.size, 0).add()
				symbol.resolved = false
				node = EnumEntryNode(srcPos, symbol, parseExpression())
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
			StringInterner.DEBUG -> {
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
		val scope = ScopeInterner.add(currentScope, name)
		val symbol = ProcSymbol(SymBase(srcPos, currentScope, name), scope).add()
		ProcNode(srcPos, symbol).add()
		expect(SymToken.LBRACE)
		parseScope(scope)
		expect(SymToken.RBRACE)
		ScopeEndNode(SrcPos()).add()
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
			ScopeEndNode(SrcPos()).add()

		srcFile.nodes = ArrayList(nodes)
	}



	/*
	Scope
	 */



	private fun parseScopeName(): ScopeIntern {
		scopeBuilder.reset()

		do {
			scopeBuilder.add(id().id)
		} while(next() == SymToken.PERIOD)

		pos--

		return ScopeInterner.add(currentScope, scopeBuilder.array, scopeBuilder.size)
	}



	private fun parseScope(scope: ScopeIntern) {
		val prevScope = currentScope
		currentScope = scope
		parseScopeInternal()
		currentScope = prevScope
	}



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