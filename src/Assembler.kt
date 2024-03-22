package eyre

class Assembler(private val context: Context) {


	private lateinit var file: SrcFile

	private var writer = context.textWriter

	private var section = context.textSec

	private var nodeIndex = 0

	private var currentFun: FunNode? = null

	private var currentSrcPos: SrcPos? = null

	private fun invalid(): Nothing = throw EyreError(currentSrcPos, "Invalid encoding")



	/*
	Assembly
	 */



	fun assemble(file: SrcFile) {
		this.nodeIndex = 0
		this.file = file

		try {
			assembleScope()
		} catch(e: EyreError) {
			context.errors.add(e)
			file.invalid = true
		}
	}



	private fun assembleScope() {
		while(nodeIndex < file.nodes.size) {
			val node = file.nodes[nodeIndex++]
			if(node !is ScopeEndNode)
				currentSrcPos = node.srcPos
			when(node) {
				is ScopeEndNode  -> return
				is NamespaceNode -> assembleScope()
				is FunNode       -> assembleFunction(node)
				is VarNode       -> { }
				else             -> invalid()
			}
		}
	}




	/*
	Code generation
	 */




	private fun assembleFunction(function: FunNode) {
		currentFun = function

		function.stackPos = -32
		for(local in function.locals) {
			function.stackPos -= local.size
			local.operand = MemOperand.rbp(Width.NONE, function.stackPos)
		}

		assembleScope()
	}



}