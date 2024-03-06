package eyre

class Parser(private val context: Context) {


	private lateinit var file: SrcFile

	private lateinit var tokens: List<Token>

	private lateinit var nodes: ArrayList<Node>

	private var pos = 0

	private var scope: Sym? = null

	private var fileNamespace: NamespaceNode? = null

	private var anonCount = 0

	private var currentProc: ProcNode? = null



	/*
	Util
	 */



	private fun at(type: TokenType) = tokens[pos].type == type

	private fun anon() = Name["\$${anonCount++}"]

	private fun Node.addNode() = nodes.add(this)

	private fun Sym.addSym() = context.symTable.add(this)

	private fun Sym.addNodeSym() {
		nodes.add(this)
		context.symTable.add(this)
	}

	private fun err(srcPos: SrcPos?, message: String): Nothing =
		throw EyreError(srcPos, message)

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
				TokenType.DO        -> parseDoWhile()
				TokenType.FOR       -> parseFor()
				TokenType.IF        -> parseIf()
				TokenType.ENUM      -> parseEnum()
				TokenType.UNION     -> parseStruct(scope, true).addNodeSym()
				TokenType.STRUCT    -> parseStruct(scope, false).addNodeSym()
				TokenType.VAR       -> parseVar()
				TokenType.PROC      -> parseProc()
				TokenType.CONST     -> parseConst()
				TokenType.NAMESPACE -> parseNamespace()
				TokenType.NAME      -> if(tokens[pos + 1].type == TokenType.COLON) parseLabel() else handleName()
				TokenType.RBRACE    -> break
				TokenType.EOF       -> break
				else                -> err("Invalid token: ${tokens[pos]}")
			}
		}
	}



	private fun parseStatement() {
		when(tokens[pos].type) {
			TokenType.IF   -> parseIf()
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
	}



	private fun parseScope(scope: Sym) {
		expect(TokenType.LBRACE)
		val prevScope = this.scope
		this.scope = scope
		parseScopeInner()
		this.scope = prevScope
		ScopeEndNode(scope).addNode()
		expect(TokenType.RBRACE)
	}



	private fun parseLabel() {
		val srcPos = tokens[pos].srcPos()
		val name = tokens[pos].nameValue
		pos += 2
		LabelNode(Base(srcPos, scope, name)).addNodeSym()
	}



	private fun handleName() {
		val srcPos = tokens[pos].srcPos()
		val name = tokens[pos].nameValue

		if(name.type == Name.Type.MNEMONIC) {
			pos++
			parseInstruction(srcPos, name.mnemonic)
		} else {
			when(val expr = parseExpr()) {
				is CallNode,
				is BinNode -> expr.addNode()
				else -> err(expr.srcPos, "Invalid node: $expr")
			}
		}
	}



	private fun parseCondition(): Node {
		expect(TokenType.LPAREN)
		val expr = parseExpr()
		expect(TokenType.RPAREN)
		return expr
	}



	private fun parseFor() {
		/*val srcPos = tokens[pos++].srcPos()
		expect(TokenType.LPAREN)
		val indexName = name()
		expect(TokenType.COMMA)
		val range = parseExpr()
		expect(TokenType.RPAREN)
		val node = ForNode(Base(srcPos, scope, anon()), range)
		node.addNode()
		val index = VarNode(Base(srcPos, node, indexName), null, null, IntTypes.DWORD, 4)
		index.addSym()
		node.index = index
		parseScopeOrExpr(node)*/
	}



	private fun parseDoWhile() {
		val srcPos = tokens[pos++].srcPos()
		val node = DoWhileNode(Base(srcPos, scope, anon()))
		node.addNode()
		parseScope(node)
		expect(TokenType.WHILE)
		node.condition = parseCondition()
	}



	private fun parseIf() {
		val srcPos = tokens[pos++].srcPos()
		var parent = IfNode(Base(srcPos, scope, anon()), parseCondition(), null)
		parent.addNode()
		parseScopeOrExpr(parent)

		while(true) {
			if(tokens[pos].type == TokenType.ELIF) {
				val srcPos2 = tokens[pos++].srcPos()
				val next = IfNode(Base(srcPos2, scope, anon()), parseCondition(), parent)
				next.addNode()
				parseScopeOrExpr(next)
				parent.next = next
				parent = next
			} else if(tokens[pos].type == TokenType.ELSE) {
				val srcPos2 = tokens[pos++].srcPos()
				val next = IfNode(Base(srcPos2, scope, anon()), null, parent)
				next.addNode()
				parent.next = next
				parseScopeOrExpr(next)
				break
			} else
				break
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

		val typeNode = if(tokens[pos].type == TokenType.COLON) {
			pos++
			parseType()
		} else null

		val valueNode = if(tokens[pos].type == TokenType.SET) {
			pos++
			parseExpr()
		} else null

		if(valueNode is InitNode && typeNode != null && typeNode.mods.size == 1) {
			val mod = typeNode.mods[0]
			if(mod is TypeNode.ArrayMod && mod.sizeNode == null)
				mod.inferredSize = valueNode.elements.size
		}

		VarNode(Base(srcPos, scope, name), typeNode, valueNode, currentProc).addNodeSym()
	}



	private fun parseProc() {
		val srcPos = tokens[pos++].srcPos()
		val name = name()
		if(currentProc != null)
			err(srcPos, "Nested procedures not supported")
		val node = ProcNode(Base(srcPos, scope, name))
		node.addNodeSym()
		currentProc = node
		parseScope(node)
		currentProc = null
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
	Instruction parsing
	 */



	private fun parseInstruction(srcPos: SrcPos, mnemonic: Mnemonic) {
		if(currentProc == null)
			err(srcPos, "Instruction must be inside a function")

		var op1: OpNode? = null
		var op2: OpNode? = null
		var op3: OpNode? = null

		fun commaOrNewline(): Boolean {
			if(atNewline)
				return false
			if(tokens[pos++].type != TokenType.COMMA)
				err(srcPos, "Expecting newline or comma")
			return true
		}

		if(!atNewline) {
			op1 = parseOperand()
			if(commaOrNewline()) {
				op2 = parseOperand()
				if(commaOrNewline()) {
					op3 = parseOperand()
					expectNewline()
				}
			}
		}

		expectNewline()
		InsNode(Base(srcPos), mnemonic, op1, op2, op3).addNode()
	}



	private fun parseMemOp(): MemNode {
		val srcPos = srcPos()
		val base: Reg
		val index: Reg
		var scale = 0L

		if(tokens[pos].type == TokenType.REG) {
			base = tokens[pos++].regValue
			if(!base.isR64)
				err("Invalid memory operand (only R64 allowed)")

			if(tokens[pos].type == TokenType.STAR) {
				if(tokens[++pos].type != TokenType.INT)
					err("Invalid memory operand")
				index = base
				scale = tokens[pos++].intValue
			} else if(tokens[pos].type == TokenType.PLUS && tokens[pos + 1].type == TokenType.REG) {
				pos++
				index = tokens[pos++].regValue
				if(!index.isR64)
					err("Invalid memory operand (only R64 allowed)")
				if(tokens[pos++].type != TokenType.STAR || tokens[pos].type != TokenType.INT)
					err("Invalid memory operand")
				scale = tokens[pos++].intValue
			}
		}

		if(scale !in 1..8)
			err("Invalid memory operand scale")

		val node = if(tokens[pos].type == TokenType.RBRACK)
			parseExpr()
		else
			null

		return MemNode(Base(srcPos), )
	}



	private fun parseOperand(): OpNode {
		val token = tokens[pos]
		val srcPos = token.srcPos()

		return if(token.nameValue.type == Name.Type.WIDTH) {
			pos++
			expect(TokenType.LBRACK)
			val child = parseExpr()
			expect(TokenType.RBRACK)
			OpNode(Base(srcPos), OpType.MEM, token.nameValue.width, child, Reg.NONE)
		} else if(token.type == TokenType.LBRACK) {
			pos++
			val child = parseExpr()
			expect(TokenType.RBRACK)
			OpNode(Base(srcPos), OpType.MEM, Width.NONE, child, Reg.NONE)
		} else if(token.type == TokenType.REG) {
			pos++
			val reg = token.regValue
			OpNode(Base(srcPos), reg.opType, reg.width, null, reg)
		} else {
			OpNode(Base(srcPos), OpType.IMM, Width.NONE, parseExpr(), Reg.NONE)
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

		return when(token.type) {
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

			TokenType.REG    -> RegNode(base, token.regValue)
			TokenType.NAME   -> NameNode(base, token.nameValue)
			TokenType.INT    -> IntNode(base, token.intValue)
			TokenType.STRING -> StringNode(base, token.stringValue)
			TokenType.CHAR   -> IntNode(base, token.intValue)
			TokenType.LPAREN -> parseExpr().also { expect(TokenType.RPAREN) }
			else -> {
				val op = token.type.unOp ?: err(srcPos, "Invalid atom: $token")
				if(op.precedence < precedence) {

				}
				val child = parseExpr(op.precedence)
				UnNode(base, op, child)
			}
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
					err(left.srcPos, "Invalid binary operator token: $token")
			val op = token.type.binOp
				?: break
			if(op.precedence < precedence)
				break
			pos++
			val base = Base(left.srcPos)

			if(op == BinOp.INV) {
				val elements = ArrayList<Node>()
				while(tokens[pos].type != TokenType.RPAREN) {
					elements.add(parseExpr())
					if(tokens[pos].type == TokenType.COMMA)
						pos++
				}
				pos++
				left = CallNode(base, left, elements)
				currentProc?.let { if(elements.size > it.mostParams) it.mostParams = elements.size }
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