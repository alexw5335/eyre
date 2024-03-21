package eyre

class Parser(private val context: Context) {


	private lateinit var file: SrcFile

	private lateinit var tokens: List<Token>

	private lateinit var nodes: ArrayList<Node>

	private var pos = 0

	private var scope: Sym? = null

	private var fileNamespace: NamespaceNode? = null

	private var currentFun: FunNode? = null



	/*
	Util
	 */



	private fun Node.addNode() = nodes.add(this)

	private fun Sym.addSym() = context.symTable.add(this)

	private fun Sym.addNodeSym() {
		nodes.add(this)
		context.symTable.add(this)
	}

	private fun err(srcPos: SrcPos?, message: String): Nothing =
		throw EyreError(srcPos, message)

	private fun err(node: Node, message: String): Nothing =
		throw EyreError(node.srcPos, message)

	private fun err(message: String): Nothing =
		throw EyreError(tokens[pos].srcPos(), message)

	private fun Token.srcPos() = SrcPos(file, line)

	private fun srcPos() = SrcPos(file, tokens[pos].line)

	private fun name(): Name {
		if(tokens[pos].type != TokenType.NAME)
			err("Expecting name, found: ${tokens[pos].type}")
		return tokens[pos++].nameValue
	}

	private fun expect(type: TokenType) {
		if(tokens[pos++].type != type)
			err("Expecting $type, found: ${tokens[pos-1].type}")
	}

	private fun expectNewline() {
		if(!atNewline)
			err("Expecting newline")
	}

	private val atNewline get() = tokens[pos].line != tokens[pos - 1].line



	/*
	Parsing
	 */



	fun parse(file: SrcFile, tokens: List<Token>) {
		this.file = file
		this.tokens = tokens
		this.nodes = file.nodes
		this.pos = 0
		this.fileNamespace = null

		try {
			parseScopeInner()
		} catch(error: EyreError) {
			file.invalid = true
			context.errors.add(error)
		}

		fileNamespace?.let { ScopeEndNode(it).addNode() }
	}



	private fun parseScopeInner() {
		while(pos < tokens.size) {
			when(tokens[pos].type) {
				TokenType.DLLIMPORT -> parseDllImport()
				TokenType.ENUM      -> parseEnum()
				TokenType.UNION     -> parseStruct(scope, true).addNodeSym()
				TokenType.STRUCT    -> parseStruct(scope, false).addNodeSym()
				TokenType.VAR       -> parseVar()
				TokenType.FUN       -> parseFun()
				TokenType.CONST     -> parseConst()
				TokenType.NAMESPACE -> parseNamespace()
				TokenType.NAME      -> handleName()
				TokenType.RBRACE    -> break
				TokenType.EOF       -> break
				else                -> err("Invalid token: ${tokens[pos]}")
			}
		}
	}



	/*private fun parseStatement() {
		when(tokens[pos].type) {
			//TokenType.IF   -> parseIf()
			TokenType.NAME -> handleName()
			else -> err("Invalid token: ${tokens[pos]}")
		}
	}



	private fun parseScopeOrExpr(scope: Sym) {
		val prevScope = this.scope
		this.scope = scope

		if(tokens[pos].type == TokenType.LBRACE) {
			pos++
			parseScopeInner()
			expect(TokenType.RBRACE)
		} else {
			parseStatement()
		}

		ScopeEndNode(scope).addNode()
		this.scope = prevScope
	}*/



	private fun parseScope(scope: Sym) {
		expect(TokenType.LBRACE)
		val prevScope = this.scope
		this.scope = scope
		parseScopeInner()
		this.scope = prevScope
		ScopeEndNode(scope).addNode()
		expect(TokenType.RBRACE)
	}



	private fun handleName() {
		when(val expr = parseExpr()) {
			is CallNode,
			is BinNode -> expr.addNode()
			else -> err(expr, "Invalid node: $expr")
		}
	}



	private fun parseDllImport() {
		val srcPos = tokens[pos++].srcPos()
		val dllName = name()
		expect(TokenType.DOT)
		val name = name()
		expectNewline()
		val import = context.getDllImport(dllName, name)
		DllImportNode(Base(srcPos, scope, name), dllName, import).addNodeSym()
	}



	private fun parseEnum() {
		val srcPos = tokens[pos++].srcPos()
		val enum = EnumNode(Base(srcPos, scope, name()))
		enum.addNodeSym()
		expect(TokenType.LBRACE)

		while(true) {
			if(tokens[pos].type == TokenType.RBRACE)
				break
			val entrySrcPos = tokens[pos].srcPos()
			val entryName = name()

			val valueNode = if(tokens[pos].type == TokenType.SET) {
				pos++
				parseExpr()
			} else
				null

			val entry = EnumEntryNode(Base(entrySrcPos, enum, entryName), valueNode)
			entry.addSym()
			enum.entries.add(entry)

			if(tokens[pos].type == TokenType.COMMA)
				pos++
		}

		pos++
	}



	private fun parseStruct(parent: Sym?, isUnion: Boolean): StructNode {
		val srcPos = tokens[pos++].srcPos()

		val name = if(tokens[pos].type == TokenType.NAME)
			tokens[pos++].nameValue
		else
			Name.NONE

		val struct = StructNode(Base(srcPos, parent, name), isUnion)
		val scope: Sym

		if(parent is StructNode) {
			val member = MemberNode(Base(struct.srcPos, parent, struct.name), null, struct)
			parent.members.add(member)
			scope = parent
		} else {
			if(name.isNull)
				err(srcPos, "Top-level struct cannot be anonymous")
			context.symTable.add(struct)
			scope = struct
		}

		expect(TokenType.LBRACE)

		while(tokens[pos].type != TokenType.RBRACE) {
			when(tokens[pos].type) {
				TokenType.UNION -> parseStruct(scope, true)
				TokenType.STRUCT -> parseStruct(scope, false)
				else -> {
					val memberSrcPos = srcPos()
					val memberTypeNode = parseType()
					val memberName = name()
					val member = MemberNode(Base(memberSrcPos, scope, memberName), memberTypeNode, null)
					member.addSym()
					struct.members.add(member)
					expectNewline()
				}
			}
		}

		pos++

		return struct
	}



	private fun parseVar() {
		val srcPos = tokens[pos++].srcPos()
		val name = name()
		val typeNode: TypeNode?

		if(tokens[pos].type == TokenType.COLON) {
			pos++
			typeNode = parseType()
		} else {
			typeNode = null
		}

		val valueNode = if(tokens[pos].type == TokenType.SET) {
			pos++
			parseExpr()
		} else null

		if(valueNode is InitNode && typeNode != null && typeNode.mods.size == 1) {
			val mod = typeNode.mods[0]
			if(mod is TypeNode.ArrayMod && mod.sizeNode == null)
				mod.inferredSize = valueNode.elements.size
		}

		val mem = if(currentFun == null) GlobalMem() else StackMem()
		val node = VarNode(Base(srcPos, scope, name), typeNode, valueNode, mem)
		node.addNodeSym()
		currentFun?.locals?.add(node)
	}



	private fun parseFun() {
		val srcPos = tokens[pos++].srcPos()
		val name = name()
		if(currentFun != null)
			err(srcPos, "Nested functions not supported")
		val node = FunNode(Base(srcPos, scope, name))
		node.addNodeSym()
		expect(TokenType.LPAREN)
		while(true) {
			if(tokens[pos].type == TokenType.RPAREN) {
				pos++
				break
			}
			val paramSrcPos = srcPos()
			val paramName = name()
			expect(TokenType.COLON)
			val paramType = parseType()
			val param = VarNode(Base(paramSrcPos, node, paramName), paramType, null, StackMem())
			param.addSym()
			node.params.add(param)
			if(tokens[pos].type != TokenType.COMMA) {
				expect(TokenType.RPAREN)
				break
			} else {
				pos++
			}
		}
		node.returnTypeNode = if(tokens[pos].type == TokenType.COLON) {
			pos++
			parseType()
		} else null
		currentFun = node
		parseScope(node)
		currentFun = null
	}



	private fun parseConst() {
		val srcPos = tokens[pos++].srcPos()
		val name = name()
		expect(TokenType.SET)
		val valueNode = parseExpr()
		ConstNode(Base(srcPos, scope, name), valueNode).addNodeSym()
		expectNewline()
	}



	private fun parseNamespace() {
		val srcPos = tokens[pos++].srcPos()
		var node = NamespaceNode(Base(srcPos, scope, name()))
		node.addSym()
		while(tokens[pos].type == TokenType.DOT) {
			pos++
			node = NamespaceNode(Base(srcPos, node, name()))
			node.addSym()
		}
		node.addNode()

		if(tokens[pos].type == TokenType.LBRACE) {
			pos++
			parseScope(node)
		} else {
			if(scope != null)
				err(srcPos, "Single-line namespace must be top-level")
			if(fileNamespace != null)
				err(srcPos, "Only one single-line namespace allowed per file")
			scope = node
			fileNamespace = node
		}
	}



	/*
	Expression parsing
	 */



	private fun parseType(): TypeNode {
		val srcPos = tokens[pos].srcPos()
		val names = ArrayList<Name>()

		do {
			names.add(name())
		} while(tokens[pos++].type == TokenType.DOT)
		pos--

		if(atNewline)
			return TypeNode(Base(srcPos), names, emptyList())

		val mods = ArrayList<TypeNode.Mod>()

		while(true) {
			when(tokens[pos].type) {
				TokenType.STAR -> {
					pos++
					mods += TypeNode.PointerMod
				}
				TokenType.LBRACK -> {
					pos++
					if(tokens[pos].type == TokenType.RBRACK) {
						pos++
						mods += TypeNode.ArrayMod(null)
					} else {
						val sizeNode = parseExpr()
						expect(TokenType.RBRACK)
						mods += TypeNode.ArrayMod(sizeNode)
					}
				}
				else -> break
			}
		}

		return TypeNode(Base(srcPos), names, mods)
	}



	private fun parseAtom(precedence: Int = 0): Node {
		val token = tokens[pos++]
		val srcPos = token.srcPos()
		val base = Base(srcPos)

		val node = when(token.type) {
			TokenType.LBRACE -> {
				val elements = ArrayList<Node>()
				while(tokens[pos].type != TokenType.RBRACE) {
					elements.add(parseExpr())
					if(tokens[pos].type == TokenType.COMMA)
						pos++
				}
				pos++
				InitNode(Base(srcPos), elements)
			}
			TokenType.LPAREN -> parseExpr().also { expect(TokenType.RPAREN) }
			TokenType.NAME   -> NameNode(base, token.nameValue)
			TokenType.INT    -> IntNode(base, token.intValue)
			TokenType.STRING -> StringNode(base, token.stringValue)
			TokenType.CHAR   -> IntNode(base, token.intValue)
			else -> {
				val op = token.type.unOp ?: err(srcPos, "Invalid atom: $token")
				//if(op.precedence < precedence) { }
				val child = parseExpr(op.precedence)
				UnNode(base, op, child)
			}
		}

		return when(tokens[pos].type.unOp) {
			UnOp.INC_PRE -> UnNode(base, UnOp.INC_POST, node).also { pos++ }
			UnOp.DEC_PRE -> UnNode(base, UnOp.DEC_POST, node).also { pos++ }
			else         -> node
		}
	}



	private fun parseExpr(precedence: Int = 0): Node {
		var left = parseAtom(precedence)

		while(true) {
			val token = tokens[pos]
			if(token.type == TokenType.ELSE || token.type == TokenType.ELIF) break
			if(!token.type.isSym)
				if(atNewline)
					break
				else
					err(left, "Invalid binary operator token: $token")
			val op = token.type.binOp ?: break
			if(op.precedence < precedence) break
			pos++
			val base = Base(left.srcPos)
			if(op == BinOp.CALL) {
				val elements = ArrayList<Node>()
				while(tokens[pos].type != TokenType.RPAREN) {
					elements.add(parseExpr())
					if(tokens[pos].type == TokenType.COMMA)
						pos++
				}
				pos++
				left = CallNode(base, left, elements)
				currentFun?.let { if(elements.size > it.mostParams) it.mostParams = elements.size }
			} else {
				val right = parseExpr(op.precedence + 1)
				left = when(op) {
					BinOp.ARR -> ArrayNode(base, left, right).also { expect(TokenType.RBRACK) }
					BinOp.DOT -> DotNode(base, left, right)
					BinOp.REF -> RefNode(base, left, right)
					else      -> BinNode(base, op, left, right)
				}
			}
		}

		return left
	}


}