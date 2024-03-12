package eyre

import java.util.*
import kotlin.math.max

/**
 * The resolver has two stages:
 * - Type resolution
 * - Symbol resolution and constant calculation
 */
class Resolver(private val context: Context) {


	private var scopeStack = Stack<Sym>()

	private fun pushScope(scope: Sym) = scopeStack.push(scope)

	private fun popScope() = scopeStack.pop()

	private fun err(srcPos: SrcPos?, message: String): Nothing =
		throw EyreError(srcPos, message)

	private fun err(node: Node, message: String): Nothing =
		throw EyreError(node.srcPos, message)



	fun resolveFile(file: SrcFile) {
		if(file.invalid) return
		scopeStack.clear()

		try {
			for(node in file.nodes) resolveNodeType(node)
			for(node in file.nodes) resolveNode(node)
			for(node in file.nodes) calculateNode(node)
		} catch(e: EyreError) {
			file.invalid = true
			context.errors.add(e)
		}
	}





	/*
	Name resolution
	 */



	private fun resolveNames(srcPos: SrcPos?, names: List<Name>): Sym {
		var sym = resolveName(srcPos, names[0])
		for(i in 1 ..< names.size) {
			sym = resolveName(srcPos, sym, names[i])
		}
		return sym
	}



	private fun resolveName(srcPos: SrcPos?, scope: Sym, name: Name): Sym {
		return context.symTable.get(scope, name)
			?: err(srcPos, "Unresolved symbol: $name")
	}



	private fun resolveName(srcPos: SrcPos?, name: Name): Sym {
		context.symTable.get(null, name)?.let { return it }
		for(i in scopeStack.indices.reversed())
			context.symTable.get(scopeStack[i], name)?.let { return it }
		err(srcPos, "Unresolved symbol: $name")
	}



	/*
	Type resolution (stage 1)
	 */



	private fun resolveType(node: TypeNode): Type {
		var type = resolveNames(node.srcPos, node.names) as? Type
			?: err(node.srcPos, "Invalid type")
		for(mod in node.mods) {
			type = when(mod) {
				is TypeNode.PointerMod -> PointerType(type)
				is TypeNode.ArrayMod -> ArrayType(type)
			}
		}
		node.type = type
		return type
	}



	private fun resolveNodeType(node: Node) { when(node) {
		is ScopeEndNode  -> popScope()
		is NamespaceNode -> pushScope(node)
		is IfNode        -> pushScope(node)
		is ProcNode      -> pushScope(node)
		is DoWhileNode   -> pushScope(node)
		is WhileNode     -> pushScope(node)
		is ForNode       -> pushScope(node)

		is VarNode ->
			if(node.typeNode != null)
				node.type = resolveType(node.typeNode)
			else if(node.valueNode is StringNode)
				node.type = StringType(node.valueNode.value.length)
			else
				err(node.srcPos, "Cannot infer variable type")

		is StructNode ->
			for(member in node.members) {
				if(member.struct != null) {
					resolveNodeType(member.struct)
					member.type = member.struct
				} else if(member.typeNode != null) {
					member.type = resolveType(member.typeNode)
				} else {
					context.internalErr()
				}
			}

		else -> return
	}}




	/*
	Stage 2: Name resolution
	 */



	private fun calculateTypeNode(node: TypeNode) {
		var type = node.type
		for(i in node.mods.size - 1 downTo 0) {
			when(val mod = node.mods[i]) {
				is TypeNode.PointerMod -> type = (type as PointerType).baseType
				is TypeNode.ArrayMod -> {
					val array = type as ArrayType

					if(mod.sizeNode == null) {
						if(mod.inferredSize == -1)
							err(node.srcPos, "Array size required")
						array.count = mod.inferredSize
					} else {
						resolveNode(mod.sizeNode)
						val count = calculateInt(mod.sizeNode)
						if(!count.isImm32) err(node.srcPos, "Array size out of bounds: $count")
						array.count = count.toInt()
						type = array.baseType
					}
				}
			}
		}
	}



	private fun resolveNode(node: Node) { when(node) {
		is ScopeEndNode  -> popScope()
		is NamespaceNode -> pushScope(node)
		is NameNode      -> node.sym = resolveName(node.srcPos, node.value)
		is UnNode        -> resolveNode(node.child)
		is DotNode       -> resolveDotNode(node)
		is ArrayNode     -> resolveArrayNode(node)
		is RefNode       -> node.receiver = resolveSymNode(node.left)
		is ConstNode     -> resolveNode(node.valueNode)
		is MemNode       -> resolveNode(node.child)
		is ImmNode       -> resolveNode(node.child)
		is StructNode    -> resolveStruct(node)

		is EnumNode -> {
			pushScope(node)
			for(entry in node.entries)
				entry.valueNode?.let(::resolveNode)
			popScope()
		}

		is VarNode -> {
			when(val atNode = node.atNode) {
				is RegNode -> node.operand = RegOperand(atNode.reg)
				else -> node.operand = MemOperand()
			}

			node.typeNode?.let(::resolveTypeNode)
			node.atNode?.let(::resolveNode)
			node.valueNode?.let(::resolveNode)
			if(node.type is StringType) {
				if(node.valueNode !is StringNode)
					err(node.srcPos, "Invalid string")
				node.size = node.valueNode.value.length
			} else {
				node.size = node.type.size
			}
		}
		is InitNode -> node.elements.forEach(::resolveNode)
		is CallNode -> {
			resolveNode(node.left)
			node.elements.forEach(::resolveNode)
		}
		is BinNode -> {
			resolveNode(node.left)
			resolveNode(node.right)
		}
		is ProcNode -> {
			if(node.name == Name.MAIN)
				context.entryPoint = node
			pushScope(node)
		}
		is InsNode -> {
			node.op1?.let(::resolveNode)
			node.op2?.let(::resolveNode)
			node.op3?.let(::resolveNode)
		}
		is DoWhileNode -> {
			pushScope(node)
			resolveNode(node.condition)
		}
		is WhileNode -> {
			pushScope(node)
			resolveNode(node.condition)
		}
		is ForNode -> {
			resolveNode(node.range)
			pushScope(node)
		}
		is IfNode -> {
			node.condition?.let(::resolveNode)
			pushScope(node)
		}
		is StringNode -> {
			val sym = StringLitSym(node.value)
			node.litSym = sym
			context.stringLiterals.add(sym)
		}
		is RegNode,
		is LabelNode,
		is DllImportNode,
		is IntNode -> Unit
		else -> context.internalErr("Unhandled node: $node")
	}}



	private fun resolveTypeNode(node: TypeNode) {
		for(mod in node.mods)
			if(mod is TypeNode.ArrayMod && mod.sizeNode != null)
				resolveNode(mod.sizeNode)
	}



	private fun resolveDotNode(node: DotNode): Sym {
		val receiver = resolveSymNode(node.left)
		if(node.right !is NameNode)
			err(node.right.srcPos, "Invalid name node: ${node.right}")

		fun invalidReceiver(): Nothing = err(node.srcPos, "Invalid receiver")

		if(receiver is TypedSym && receiver is Pos) {
			if(receiver.type !is StructNode) invalidReceiver()
			val member = resolveName(node.srcPos, receiver.type, node.right.value)
			if(member !is MemberNode) context.internalErr()
			val sym = PosRefSym(receiver, receiver.type) { member.offset }
			node.sym = sym
			return sym
		} else {
			val sym = resolveName(node.srcPos, receiver, node.right.value).also { node.sym = it }
			node.sym = sym
			return sym
		}
	}



	private fun resolveSymNode(node: Node): Sym = when(node) {
		is NameNode  -> resolveName(node.srcPos, node.value).also { node.sym = it }
		is DotNode   -> resolveDotNode(node)
		is ArrayNode -> resolveArrayNode(node)
		else         -> err(node.srcPos, "Invalid receiver node: $node")
	}



	private fun resolveStruct(struct: StructNode) {
		for(member in struct.members)
			if(member.typeNode != null)
				resolveTypeNode(member.typeNode)
			else
				resolveStruct(member.struct!!)
	}




	/*
	Stage 3: Constant calculation
	 */



	// Assuming unresolved
	private fun calculateNode(node: Node) {
		when(node) {
			is EnumNode -> calculateEnum(node)
			is MemNode -> node.operand.disp = calculateMem(node, true, node.operand).toInt()
			is ImmNode -> node.operand.value = calculateImm(node, true, node.operand)
			is RefNode -> calculateRefNode(node)
			is StructNode -> calculateStruct(node)
			is ConstNode -> {
				node.intValue = calculateInt(node.valueNode)
				node.resolved = true
			}
		}
	}



	private var currentMem: MemOperand? = null
	private var currentImm: ImmOperand? = null

	private fun calculateImm(node: Node, regValid: Boolean, imm: ImmOperand): Long {
		fun reloc(pos: Pos): Long {
			if(imm.reloc != null || !regValid)
				err(node, "Only one relocation allowed")
			imm.reloc = pos
			return 0
		}

		fun sym(sym: Sym?): Long = when(sym) {
			null            -> err(node, "Invalid symbol: $sym")
			is IntSym       -> sym.intValue
			is ProcNode     -> reloc(sym)
			is LabelNode    -> reloc(sym)
			is StringLitSym -> reloc(sym)
			else            -> err(node, "Invalid symbol: $sym")
		}

		return when(node) {
			is IntNode    -> node.value
			is UnNode     -> node.calc(regValid) { n, v -> calculateImm(n, v, imm) }
			is BinNode    -> node.calc(regValid) { n, v -> calculateImm(n, v, imm) }
			is DotNode    -> sym(node.sym)
			is NameNode   -> sym(node.sym)
			is StringNode -> sym(node.litSym)
			is ArrayNode  -> sym(node.sym)
			is RefNode    -> { if(node.receiver!!.unResolved) calculateRefNode(node); node.intValue }
			else          -> err(node, "Invalid node: $node")
		}
	}



	private fun calculateMem(node: Node, regValid: Boolean, mem: MemOperand): Long {
		fun reloc(pos: Pos): Long {
			if(mem.reloc != null || !regValid)
				err(node, "Only one relocation allowed")
			mem.reloc = pos
			return 0
		}

		fun sym(sym: Sym?): Long = when(sym) {
			null            -> err(node, "Invalid symbol: $sym")
			is IntSym       -> sym.intValue
			is ProcNode     -> reloc(sym)
			is LabelNode    -> reloc(sym)
			is StringLitSym -> reloc(sym)
			is VarNode      -> TODO()
			else            -> err(node, "Invalid symbol: $sym")
		}

		return when(node) {
			is IntNode -> node.value
			is UnNode -> node.calc(regValid) { n, v -> calculateMem(n, v, mem) }
			is RegNode -> {
				if(mem.base.isValid || !regValid)
					err(node, "Invalid memory operand")
				mem.base = node.reg
				0
			}
			is BinNode -> if(node.op == BinOp.MUL && node.left is RegNode && node.right is IntNode) {
				if(mem.index.isValid || !regValid)
					err(node, "Invalid memory operand")
				mem.index = node.left.reg
				mem.scale = node.right.value.toInt()
				0
			} else
				node.calc(regValid) { n, v -> calculateMem(n, v, mem) }
			is DotNode    -> sym(node.sym)
			is NameNode   -> sym(node.sym)
			is StringNode -> sym(node.litSym)
			is ArrayNode  -> sym(node.sym)
			is RefNode    -> { if(node.receiver!!.unResolved) calculateRefNode(node); node.intValue }
			else          -> err(node, "Invalid node: $node")

		}
	}




	private fun calculateInt(node: Node): Long {
		fun sym(sym: Sym?): Long {
			if(sym == null) err(node.srcPos, "Unresolved symbol")
			if(sym !is IntSym) err(node.srcPos, "Invalid symbol")
			if(!sym.resolved) calculateNode(sym)
			return sym.intValue
		}

		return when(node) {
			is IntNode  -> node.value
			is UnNode   -> node.calc(::calculateInt)
			is BinNode  -> node.calc(::calculateInt)
			is NameNode -> sym(node.sym)
			is DotNode  -> sym(node.sym)
			is RefNode  -> { if(node.receiver!!.unResolved) calculateRefNode(node); node.intValue }
			else        -> err(node.srcPos, "Invalid int node: $node")
		}
	}



	private fun calculateRefNode(node: RefNode) {
		val right = (node.right as? NameNode)?.value ?: err(node, "Expecting name")
		val receiver = node.receiver!!
		if(!receiver.resolved) calculateNode(receiver)
		when(right) {
			Name.COUNT -> when(receiver) {
				is EnumNode -> node.intValue = receiver.entries.size.toLong()
				else -> err(node, "::count not valid for receiver $receiver")
			}
			Name.SIZE -> when(receiver) {
				is StructNode  -> node.intValue = receiver.size.toLong()
				is VarNode     -> node.intValue = receiver.size.toLong()
				is EnumNode    -> node.intValue = receiver.size.toLong()
				else -> err(node, "::size not valid for receiver $receiver")
			}
			else -> err(node, "Invalid reference ::$right")
		}
	}



	private fun resolveArrayNode(node: ArrayNode): Sym {
		val receiver = resolveSymNode(node.left) as? VarNode ?: err(node.srcPos, "Invalid receiver")
		val type = receiver.type as? ArrayType ?: err(node.srcPos, "Invalid receiver")
		val count = calculateInt(node.right)
		if(!count.isImm32) err(node.srcPos, "Array index out of bounds")
		TODO()
		/*if(receiver.loc is) {
			val sym = PosRefSym(receiver.loc as Pos, type.baseType) { count.toInt() * type.baseType.size }
			node.sym = sym
			return sym
		} else {
			err(node.srcPos, "Not yet implemented")
		}*/
	}



	private fun calculateEnum(enum: EnumNode) {
		var current = 0L
		var max = 0L

		for(entry in enum.entries) {
			entry.intValue = if(entry.valueNode != null)
				calculateInt(entry.valueNode)
			else
				current

			max = max.coerceAtLeast(entry.intValue)

			current = entry.intValue + 1
			entry.resolved = true
		}

		enum.size = when {
			max >= Short.MAX_VALUE -> 4
			max >= Byte.MAX_VALUE -> 2
			else -> 1
		}

		enum.alignment = enum.size
		enum.resolved = true
	}



	private fun calculateStruct(struct: StructNode) {
		var structSize = 0
		var structAlignment = 0

		for((index, member) in struct.members.withIndex()) {
			val type = member.type
			member.index = index
			member.typeNode?.let(::calculateTypeNode)

			if(struct.isUnion) {
				member.offset = 0
				structSize = max(structSize, type.size)
			} else {
				structSize = (structSize + type.alignment - 1) and -type.alignment
				member.offset = structSize
				structSize += type.size
			}

			if(structAlignment < 8 && type.alignment > structAlignment)
				structAlignment = type.alignment
		}

		structSize = (structSize + structAlignment - 1) and -structAlignment
		struct.size = structSize
		struct.alignment = structAlignment
	}


}