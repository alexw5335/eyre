package eyre

import java.util.*

class Parser(private val context: CompilerContext) {


	private lateinit var srcFile: SrcFile
	
	private lateinit var tokens: List<Token>

	private var pos = 0

	private var scopeStack = IntArray(64)

	private var scopeStackSize = 0

	private var currentScope: ScopeIntern = ScopeInterner.EMPTY

	private val nodes = ArrayList<AstNode>()

	private val nodeLines = IntList()

	private var currentNamespace: Namespace? = null // Only single-line namespaces



	/*
	Parsing utils
	 */



	private val next get() = tokens[pos]

	private val prev get() = tokens[pos - 1]

	private fun next() = tokens[pos++]

	private fun atNewline() = srcFile.newlines[pos]

	private fun atTerminator() = srcFile.terminators[pos]

	private fun id() = (tokens[pos++] as? IdToken)?.value
		?: error("Expecting identifier, found: $prev")



	private fun addNode(node: AstNode) {
		nodes.add(node)
		nodeLines.add(srcFile.tokenLines[pos - 1])
	}



	private fun addSymbol(symbol: Symbol) {
		val existing = context.symbols.add(symbol)
		if(existing != null)
			error("Symbol redefinition: ${symbol.name}")
		when(symbol) {
			is ConstIntSymbol -> if(!symbol.resolved) context.constSymbols.add(symbol)
			is EnumSymbol     -> context.constSymbols.add(symbol)
		}
	}



	/*
	Errors
	 */



	private fun error(offset: Int, message: String): Nothing {
		System.err.println("Parser error at ${srcFile.path}:${srcFile.tokenLines[pos - offset]}")
		System.err.print('\t')
		System.err.println(message)
		System.err.println()
		throw RuntimeException("Parser error")
	}

	private fun error(message: String): Nothing {
		error(1, message)
	}

	private fun expectTerminator() {
		if(!atTerminator())
			error("Expecting terminator")
	}

	private fun expect(symbol: SymToken) {
		if(next() != symbol)
			error("Expecting '${symbol.string}', found: $prev")
	}



	/*
	Parsing
	 */



	fun parse(srcFile: SrcFile) {
		this.srcFile = srcFile
		this.tokens = srcFile.tokens
		scopeStackSize = 0
		nodes.clear()

		parseScope()

		if(currentNamespace != null)
			addNode(ScopeEndNode)

		srcFile.nodes = ArrayList(nodes)
		srcFile.nodeLines = nodeLines.array()
	}



	private fun parseAtom(): AstNode {
		val token = next()

		if(token is IdToken) {
			val id = token.value
			if(id in StringInterner.registers)
				return RegNode(StringInterner.registers[id])
			return SymNode(id)
		}

		if(token is SymToken) {
			if(token == SymToken.LEFT_PAREN) {
				val expression = parseExpression()
				expect(SymToken.RIGHT_PAREN)
				return expression
			}

			return UnaryNode(token.unaryOp ?: error("Unexpected symbol: $token"), parseAtom())
		}

		if(token is IntToken)
			return IntNode(token.value)

		if(token is StringToken)
			return StringNode(token.value)

		if(token is CharToken)
			return IntNode(token.value.code.toLong())

		error("Unexpected token: $token")
	}



	private fun parseExpression(precedence: Int = 0): AstNode {
		var atom = parseAtom()

		while(true) {
			val token = next

			if(token !is SymToken)
				if(!atTerminator())
					error("Use a semicolon to separate expressions that are on the same line")
				else
					break

			if(token == SymToken.SEMICOLON) break

			val op = token.binaryOp ?: break
			if(op.precedence < precedence) break
			pos++

			atom = if(op == BinaryOp.DOT)
				DotNode(atom, parseExpression(op.precedence + 1) as? SymNode ?: error("Invalid node"))
			else if(op == BinaryOp.REF)
				RefNode(
					atom as? SymProviderNode ?: error("Invalid reference"),
					parseExpression(op.precedence + 1) as? SymNode ?: error("Invalid node")
				)
			else
				BinaryNode(op, atom, parseExpression(op.precedence + 1))
		}

		return atom
	}
	
	
	
	private fun parseOperand(): AstNode {
		var token = next
		var width: Width? = null

		if(token is IdToken) {
			if(token.value in StringInterner.widths) {
				width = StringInterner.widths[token.value]
				if(tokens[pos + 1] == SymToken.LEFT_BRACKET)
					token = tokens[++pos]
			} else if(token.value == StringInterner.FS)
				return SegRegNode(SegReg.FS)
			else if(token.value == StringInterner.GS)
				return SegRegNode(SegReg.GS)
			else
				return parseExpression()
		}

		if(token == SymToken.LEFT_BRACKET) {
			pos++
			val value = parseExpression()
			expect(SymToken.RIGHT_BRACKET)
			return MemNode(width, value)
		}

		return parseExpression()
	}



	private fun parseInstruction(mnemonic: Mnemonic): InsNode {
		if(atNewline() || next == EndToken)
			return InsNode(mnemonic, 0, null, null, null, null)

		val op1 = parseOperand()
		if(next != SymToken.COMMA)
			return InsNode(mnemonic, 1, op1, null, null, null)
		pos++

		val op2 = parseOperand()
		if(next != SymToken.COMMA)
			return InsNode(mnemonic, 2, op1, op2, null, null)
		pos++

		val op3 = parseOperand()
		if(next != SymToken.COMMA)
			return InsNode(mnemonic, 3, op1, op2, op3, null)
		pos++

		val op4 = parseOperand()
		expectTerminator()
		return InsNode(mnemonic, 4, op1, op2, op3, op4)
	}



	private fun parseNamespace() {
		val name = id()
		val thisScope = addScope(name)
		val namespace = Namespace(currentScope, name, thisScope)
		addSymbol(namespace)

		if(next == SymToken.LEFT_BRACE) {
			pos++
			addNode(NamespaceNode(namespace))
			parseScope(thisScope)
			expect(SymToken.RIGHT_BRACE)
			addNode(ScopeEndNode)
		} else {
			expectTerminator()
			if(currentNamespace != null)
				addNode(ScopeEndNode)
			addNode(NamespaceNode(namespace))
			parseScope(thisScope)
			currentNamespace = namespace
		}
	}



	private fun parseDllImport() {
		val dllName = id()
		expect(SymToken.LEFT_BRACE)

		val dllSymbol = context.dlls.firstOrNull { it.name == dllName }
			?: DllSymbol(currentScope, dllName, ArrayList()).also(context.dlls::add)

		addSymbol(dllSymbol)

		while(pos < tokens.size) {
			if(next == SymToken.RIGHT_BRACE) {
				pos++
				break
			}

			val importName = id()
			expectTerminator()
			if(next == SymToken.COMMA) pos++
			val importSymbol = DllImportSymbol(currentScope, importName, Section.IDATA, 0)
			addSymbol(importSymbol)
			dllSymbol.imports.add(importSymbol)
		}
	}



	private fun parseVar() {
		val name = id()
		var initialiser = id()

		if(initialiser == StringInterner.RES) {
			val size = parseExpression()
			val symbol = ResSymbol(currentScope, name, Section.DATA, 0, 0)
			addSymbol(symbol)
			addNode(ResNode(symbol, size))
			return
		}

		val parts = ArrayList<VarPart>()
		var size = 0

		while(true) {
			if(initialiser !in StringInterner.varWidths) break
			val width = StringInterner.varWidths[initialiser]
			val values = ArrayList<AstNode>()

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

			parts.add(VarPart(width, values))
			initialiser = (tokens[pos++] as? IdToken)?.value ?: break
		}

		pos--
		if(parts.isEmpty()) error("Expecting variable initialiser")
		val symbol = VarSymbol(currentScope, name, Section.DATA, 0, size)
		addSymbol(symbol)
		val node = VarNode(symbol, parts)
		addNode(node)
	}



	private fun parseConst() {
		val name = id()
		expect(SymToken.EQUALS)
		val value = parseExpression()
		val symbol = ConstIntSymbol(currentScope, name, false, 0L)
		val node = ConstNode(symbol, value)
		symbol.node = node
		addSymbol(symbol)
		addNode(node)
	}



	private fun parseEnum(isBitmask: Boolean) {
		val enumName = id()
		var current = if(isBitmask) 1L else 0L
		val scope = addScope(enumName)

		if(tokens[pos] != SymToken.LEFT_BRACE) return
		pos++
		if(tokens[pos] == SymToken.RIGHT_BRACE) return
		val entries = ArrayList<EnumEntryNode>()
		val entrySymbols = ArrayList<EnumEntrySymbol>()

		while(pos < tokens.size) {
			if(tokens[pos] == SymToken.RIGHT_BRACE) break

			val name = id()

			val symbol: EnumEntrySymbol
			val entry: EnumEntryNode

			if(tokens[pos] == SymToken.EQUALS) {
				pos++
				symbol = EnumEntrySymbol(scope, name, entries.size, false, 0L)
				entry = EnumEntryNode(symbol, parseExpression())
			} else {
				symbol = EnumEntrySymbol(scope, name, entries.size, true, current)
				entry = EnumEntryNode(symbol, NullNode)
				current += if(isBitmask) current else 1
			}

			addSymbol(symbol)
			entries.add(entry)
			entrySymbols.add(symbol)

			if(!atNewline() && (tokens[pos] != SymToken.COMMA || tokens[++pos] !is IdToken)) break
		}

		expect(SymToken.RIGHT_BRACE)
		val symbol = EnumSymbol(currentScope, enumName, scope, entrySymbols)
		addSymbol(symbol)
		val node = EnumNode(symbol, entries)
		addNode(node)
		symbol.node = node
		for(child in entrySymbols) child.parent = symbol
	}



	private fun parseId(id: StringIntern) {
		if(next == SymToken.COLON) {
			pos++
			val symbol = LabelSymbol(currentScope, id, Section.TEXT, 0)
			addSymbol(symbol)
			addNode(LabelNode(symbol))
			return
		}

		if(id in StringInterner.keywords) {
			when(StringInterner.keywords[id]) {
				Keyword.NAMESPACE -> parseNamespace()
				Keyword.DLLIMPORT -> parseDllImport()
				Keyword.VAR       -> parseVar()
				Keyword.CONST     -> parseConst()
				Keyword.ENUM      -> parseEnum(false)
				Keyword.BITMASK   -> parseEnum(true)
				else              -> error("Invalid keyword: $id")
			}
		}

		if(id in StringInterner.mnemonics) {
			val mnemonic = StringInterner.mnemonics[id]
			addNode(parseInstruction(mnemonic))
		}
	}



	private fun parseScope() {
		while(pos < tokens.size) {
			when(val token = next()) {
				is IdToken           -> parseId(token.value)
				SymToken.RIGHT_BRACE -> { pos--; break }
				is SymToken          -> if(token != SymToken.SEMICOLON) error(1, "Invalid symbol: ${token.string}")
				EndToken             -> break
				else                 -> error(1, "Invalid token: $token")
			}
		}
	}



	private fun parseScope(scope: ScopeIntern) {
		val prevScope = currentScope
		currentScope = scope
		scopeStackSize++
		parseScope()
		scopeStackSize--
		currentScope = prevScope
	}



	private fun addScope(name: StringIntern): ScopeIntern {
		if(scopeStackSize >= scopeStack.size)
			scopeStack = scopeStack.copyOf(scopeStackSize * 2)
		scopeStack[scopeStackSize] = name.id
		val hash = currentScope.hash * 31 + name.id
		return ScopeInterner.add(scopeStack.copyOf(scopeStackSize + 1), hash)
	}


}