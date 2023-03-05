package eyre

import eyre.util.IntList
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
	
	private fun SrcPos(pos: Int = this.pos) = SrcPos(srcFile, srcFile.tokenLines[pos])



	/*
	Node and symbol creation
	 */



	private fun<T : AstNode> T.srcPos(srcPos: SrcPos = SrcPos()): T {
		this.srcPos = srcPos
		return this
	}



	private fun addNode(node: AstNode) {
		nodes.add(node)
		nodeLines.add(srcFile.tokenLines[pos - 1])
	}



	private fun addSymbol(symbol: Symbol) {
		val existing = context.symbols.add(symbol)
		if(existing != null)
			error("Symbol redefinition: ${symbol.name}. Original: ${existing.scope}.${existing.name}, new: ${symbol.scope}.${symbol.name}")
	}



	private fun<T : Symbol> T.add(): T { addSymbol(this); return this }

	private fun<T : AstNode> T.add(): T { addNode(this); return this }



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
			ScopeEndNode().srcPos().add()

		srcFile.nodes = ArrayList(nodes)
		srcFile.nodeLines = nodeLines.array()
	}



	private fun parseAtom(): AstNode {
		val token = next()

		if(token is IdToken) {
			val id = token.value
			if(id in StringInterner.registers)
				return RegNode(StringInterner.registers[id])
			return SymNode(id).srcPos()
		}

		if(token is SymToken) {
			if(token == SymToken.LPAREN) {
				val expression = parseExpression()
				expect(SymToken.RPAREN)
				return expression
			}

			return UnaryNode(token.unaryOp ?: error("Unexpected symbol: $token"), parseAtom()).srcPos()
		}

		if(token is IntToken)
			return IntNode(token.value).srcPos()

		if(token is StringToken)
			return StringNode(token.value).srcPos()

		if(token is CharToken)
			return IntNode(token.value.code.toLong()).srcPos()

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

			atom = when(op) {
				BinaryOp.DOT -> DotNode(atom, parseExpression(op.precedence + 1) as? SymNode ?: error("Invalid node"))
				BinaryOp.REF -> RefNode(
					atom as? SymProviderNode ?: error("Invalid reference"),
					parseExpression(op.precedence + 1) as? SymNode ?: error("Invalid node")
				)
				else -> BinaryNode(op, atom, parseExpression(op.precedence + 1))
			}.srcPos()
		}

		return atom
	}
	
	
	
	private fun parseOperand(): AstNode {
		var token = next
		var width: Width? = null

		if(token is IdToken) {
			if(token.value in StringInterner.widths) {
				width = StringInterner.widths[token.value]
				if(tokens[pos + 1] == SymToken.LBRACKET)
					token = tokens[++pos]
			} else if(token.value == StringInterner.FS)
				return SegRegNode(SegReg.FS)
			else if(token.value == StringInterner.GS)
				return SegRegNode(SegReg.GS)
			else
				return parseExpression()
		}

		if(token == SymToken.LBRACKET) {
			pos++
			val value = parseExpression()
			expect(SymToken.RBRACKET)
			return MemNode(width, value)
		}

		return parseExpression()
	}



	private fun parseInstruction(mnemonic: Mnemonic): InsNode {
		if(atNewline() || next == EndToken)
			return InsNode(mnemonic, 0, null, null, null, null)

		val op1 = parseOperand().srcPos()
		if(next != SymToken.COMMA)
			return InsNode(mnemonic, 1, op1, null, null, null)
		pos++

		val op2 = parseOperand().srcPos()
		if(next != SymToken.COMMA)
			return InsNode(mnemonic, 2, op1, op2, null, null)
		pos++

		val op3 = parseOperand().srcPos()
		if(next != SymToken.COMMA)
			return InsNode(mnemonic, 3, op1, op2, op3, null)
		pos++

		val op4 = parseOperand().srcPos()
		expectTerminator()
		return InsNode(mnemonic, 4, op1, op2, op3, op4)
	}



	private fun parseNamespace() {
		val srcPos = SrcPos()
		val name = id()
		val thisScope = addScope(name)
		val namespace = Namespace(SymBase(srcPos, currentScope, name), thisScope).add()

		if(next == SymToken.LBRACE) {
			pos++
			NamespaceNode(namespace).srcPos().add()
			parseScope(thisScope)
			expect(SymToken.RBRACE)
			ScopeEndNode().srcPos().add()
		} else {
			expectTerminator()
			if(currentNamespace != null)
				ScopeEndNode().srcPos().add()
			NamespaceNode(namespace).srcPos().add()
			parseScope(thisScope)
			currentNamespace = namespace
		}
	}



	private fun parseDllImport() {
		val srcPos = SrcPos()
		val dllName = id()
		expect(SymToken.LBRACE)

		val dll = context.dlls.getOrPut(dllName) {
			DllSymbol(SymBase(srcPos, currentScope, dllName), ArrayList())
		}.add()

		while(pos < tokens.size) {
			if(next == SymToken.RBRACE) {
				pos++
				break
			}

			val srcPos2 = SrcPos()
			val importName = id()
			expectTerminator()
			if(next == SymToken.COMMA) pos++
			val importSymbol = DllImportSymbol(SymBase(srcPos2, currentScope, importName)).add()
			dll.imports.add(importSymbol)
		}
	}



	private fun parseVar() {
		val srcPos = SrcPos()
		val name = id()
		var initialiser = id()

		if(initialiser == StringInterner.RES) {
			val size = parseExpression()
			val symbol = ResSymbol(SymBase(srcPos, currentScope, name)).add()
			ResNode(symbol, size).srcPos(srcPos).add()
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
		val symbol = VarSymbol(SymBase(srcPos, currentScope, name), size).add()
		VarNode(symbol, parts).srcPos(srcPos).add()
	}



	private fun parseConst() {
		val srcPos = SrcPos()
		val name = id()
		expect(SymToken.EQUALS)
		val value = parseExpression()
		val symbol = ConstSymbol(SymBase(srcPos, currentScope, name)).add()
		ConstNode(symbol, value).add()
	}



	private fun parseEnum(isBitmask: Boolean) {
		val enumSrcPos = SrcPos()
		val enumName = id()
		var current = if(isBitmask) 1L else 0L
		val scope = addScope(enumName)

		if(tokens[pos] != SymToken.LBRACE) return
		pos++
		if(tokens[pos] == SymToken.RBRACE) return
		val entries = ArrayList<EnumEntryNode>()
		val entrySymbols = ArrayList<EnumEntrySymbol>()

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
				node = EnumEntryNode(symbol, parseExpression())
			} else {
				symbol = EnumEntrySymbol(base, entries.size, current).add()
				symbol.resolved = true
				node = EnumEntryNode(symbol, null)
				current += if(isBitmask) current else 1
			}

			entries.add(node)
			entrySymbols.add(symbol)
			node.srcPos = srcPos

			if(!atNewline() && next != SymToken.COMMA)
				break
		}

		expect(SymToken.RBRACE)
		val symbol = EnumSymbol(SymBase(enumSrcPos, currentScope, enumName), scope, entrySymbols).add()
		EnumNode(symbol, entries).add()
		for(child in entrySymbols)
			child.parent = symbol
	}



	private fun parseLabel(id: StringIntern) {
		val srcPos = SrcPos(1)
		val symbol = LabelSymbol(SymBase(srcPos, currentScope, id)).add()
		LabelNode(symbol).srcPos(srcPos).add()
	}



	private fun parseId(id: StringIntern) {
		if(next == SymToken.COLON) {
			parseLabel(id)
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
			val srcPos = SrcPos()
			val mnemonic = StringInterner.mnemonics[id]
			parseInstruction(mnemonic).add().srcPos = srcPos
		}
	}



	private fun parseScope() {
		while(pos < tokens.size) {
			when(val token = next()) {
				is IdToken           -> parseId(token.value)
				SymToken.RBRACE -> { pos--; break }
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