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
			else
				BinaryNode(op, atom, parseExpression(op.precedence + 1))
		}

		return atom
	}
	
	
	
	private fun parseOperand(): AstNode {
		var token = next
		var width: Width? = null
		
		if(token is IdToken) {
			if(token.value in StringInterner.widths)
				width = StringInterner.widths[token.value]
			if(tokens[pos + 1] == SymToken.LEFT_BRACKET)
				token = tokens[++pos]
		}

		if(token == SymToken.LEFT_BRACKET) {
			pos++
			val value = parseExpression()
			expect(SymToken.RIGHT_BRACKET)
			return MemNode(width, value)
		}

		return parseExpression()
	}



	private fun parseInstruction(mnemonic: Mnemonic): InstructionNode {
		if(atTerminator())
			return InstructionNode(mnemonic, 0, null, null, null, null)

		val op1 = parseOperand()
		if(next != SymToken.COMMA)
			return InstructionNode(mnemonic, 1, op1, null, null, null)
		pos++

		val op2 = parseOperand()
		if(next != SymToken.COMMA)
			return InstructionNode(mnemonic, 2, op1, op2, null, null)
		pos++

		val op3 = parseOperand()
		if(next != SymToken.COMMA)
			return InstructionNode(mnemonic, 3, op1, op2, op3, null)
		pos++

		val op4 = parseOperand()
		expectTerminator()
		return InstructionNode(mnemonic, 4, op1, op2, op3, op4)
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

		val thisScope = addScope(dllName)

		val dllSymbol = context.dlls.firstOrNull { it.name == dllName }
			?: DllSymbol(currentScope, dllName, thisScope, ArrayList()).also(context.dlls::add)

		addSymbol(dllSymbol)

		while(pos < tokens.size) {
			if(next == SymToken.RIGHT_BRACE) {
				pos++
				break
			}

			val importName = id()
			expectTerminator()
			if(next == SymToken.COMMA) pos++
			val importSymbol = DllImportSymbol(thisScope, importName, Section.IDATA, 0)
			addSymbol(importSymbol)
			dllSymbol.symbols.add(importSymbol)
		}
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