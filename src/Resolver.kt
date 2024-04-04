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

	private fun err(node: Node, message: String = "Unspecified error"): Nothing =
		throw EyreError(node.srcPos, message)



	fun resolveFile(file: SrcFile) {
		if(file.resolved || file.invalid) return
		scopeStack.clear()
		file.resolving = true

		try {
			for(node in file.nodes) resolveNodeType(node)
			for(node in file.nodes) resolveNode(node)
		} catch(e: EyreError) {
			file.invalid = true
			context.errors.add(e)
			return
		}

		file.resolving = false
		file.resolved = true
	}



	private fun checkResolveFile(srcNode: Node, node: Node) {
		if(node.resolved) return
		val file = node.srcPos?.file ?: context.internalErr()
		if(file.resolving)
			err(srcNode.srcPos, "Cyclic resolution")
		resolveFile(file)
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
		is FunNode       -> {
			pushScope(node)
			for(param in node.params)
				param.type = resolveType(param.typeNode!!)
			node.returnType = node.returnTypeNode?.let(::resolveType) ?: VoidType
		}
		is VarNode -> node.type = node.typeNode?.let(::resolveType) ?: err(node.srcPos, "Missing type node")
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
	Reference resolution
	 */



	private fun resolveTypeNode(node: TypeNode) {
		var type = node.type
		for(i in node.mods.size - 1 downTo 0) {
			when(val mod = node.mods[i]) {
				is TypeNode.PointerMod -> type = (type as PointerType).type
				is TypeNode.ArrayMod -> {
					val array = type as ArrayType

					if(mod.sizeNode == null) {
						if(mod.inferredSize == -1)
							err(node.srcPos, "Array size required")
						array.count = mod.inferredSize
					} else {
						resolveNode(mod.sizeNode)
						val count = resolveInt(mod.sizeNode)
						if(!count.isImm32) err(node.srcPos, "Array size out of bounds: $count")
						array.count = count.toInt()
						type = array.type
					}
				}
			}
		}
	}



	private fun resolveNode(node: Node) { when(node) {
		is ScopeEndNode  -> popScope()
		is NamespaceNode -> pushScope(node)
		is NameNode      -> resolveNameNode(node)
		is StructNode    -> resolveStruct(node)
		is EnumNode      -> resolveEnum(node)
		is DotNode       -> resolveDotNode(node)
		is ArrayNode     -> resolveArrayNode(node)
		is VarNode       -> {
			node.typeNode?.let(::resolveTypeNode)
			node.valueNode?.let(::resolveNode)
		}
		is InitNode -> node.elements.forEach(::resolveNode)
		is CallNode -> resolveCallNode(node)
		is ConstNode -> {
			//resolveNode(node.valueNode)
			//node.intValue = resolveInt(node.valueNode)
			//node.resolved = true
		}
		is FunNode -> {
			if(node.name == Name.MAIN)
				context.entryPoint = node
			pushScope(node)
		}
		is DllImportNode -> Unit

		is UnNode -> {
			resolveNode(node.child)
			node.exprType = IntTypes.I32

		}
		is BinNode -> {
			resolveNode(node.left)
			resolveNode(node.right)
			node.exprType = IntTypes.I32
		}
		is IntNode -> node.isLeaf = true

		else -> context.internalErr("Unhandled node: $node")
	}}



	private fun resolveNameNode(node: NameNode): Sym {
		val sym = resolveName(node.srcPos, node.name)
		node.exprSym = sym
		node.exprType = exprType(sym)
		return sym
	}



	private fun exprType(sym: Sym): Type? = when(sym) {
		is VarNode -> sym.type
		is MemberNode -> sym.type
		else -> null
	}



	private fun resolveCallNode(node: CallNode) {
		val left = node.left
		resolveNode(left)
		node.args.forEach(::resolveNode)
		val receiver = left.exprSym as? FunNode ?: err(node.left)
		node.receiver = receiver
		node.exprType = receiver.returnType
	}



	private fun resolveArrayNode(node: ArrayNode) {
		val left = node.left
		resolveNode(left)
		val receiver = left.exprType ?: err(left)
		if(receiver !is ArrayType) err(left)
		node.exprType = receiver.type
	}



	private fun resolveDotNode(node: DotNode) {
		val left = node.left
		val right = (node.right as? NameNode)?.name ?: err(node.right)
		resolveNode(left)
		if(left.exprType != null) {
			var receiver = left.exprType!!

			if(receiver is PointerType) {
				node.type = DotNode.Type.DEREF
				receiver = receiver.type
			} else
				node.type = DotNode.Type.MEMBER

			val member = resolveName(node.srcPos, receiver, right) as? MemberNode
				?: err(node)
			node.exprType = member.type
			node.member = member
		} else {
			node.type = DotNode.Type.SYM
			val sym = node.left.exprSym ?: err(node.left)
			node.exprType = exprType(sym)
			node.exprSym = sym
		}
	}




	private fun resolveInt(node: Node): Long {
		fun sym(sym: Sym?): Long = when(sym) {
			null             -> err(node.srcPos, "Unresolved symbol")
			is ConstNode     -> { checkResolveFile(node, sym); sym.intValue }
			is MemberNode    -> { checkResolveFile(node, sym); sym.offset.toLong() }
			is EnumEntryNode -> { checkResolveFile(node, sym); sym.intValue }
			else             -> err(node.srcPos, "Invalid int node: $node, $sym")
		}

		return when(node) {
			is IntNode  -> node.value
			is UnNode   -> node.calc(::resolveInt)
			is BinNode  -> node.calc(::resolveInt)
			is NameNode -> sym(node.exprSym)
			is DotNode  -> sym(node.exprSym)
			else        -> err(node.srcPos, "Invalid int node: $node")
		}
	}



	private fun resolveEnum(enum: EnumNode) {
		pushScope(enum)

		var current = 0L
		var max = 0L

		for(entry in enum.entries) {
			entry.intValue = if(entry.valueNode != null) {
				resolveNode(entry.valueNode)
				resolveInt(entry.valueNode)
			} else {
				current
			}

			max = max.coerceAtLeast(entry.intValue)

			current = entry.intValue + 1
			entry.resolved = true
		}

		enum.resolved = true

		enum.size = when {
			max >= Short.MAX_VALUE -> 4
			max >= Byte.MAX_VALUE -> 2
			else -> 1
		}

		enum.alignment = enum.size

		popScope()
	}



	private fun resolveStruct(struct: StructNode) {
		for(member in struct.members)
			if(member.typeNode != null)
				resolveTypeNode(member.typeNode)
			else
				resolveStruct(member.struct!!)

		var structSize = 0
		var structAlignment = 0

		for((index, member) in struct.members.withIndex()) {
			val type = member.type
			member.index = index

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