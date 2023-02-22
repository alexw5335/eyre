package eyre

import eyre.util.NativeWriter

class Assembler(private val context: CompilerContext) {



	private val textWriter = NativeWriter()

	private var writer = textWriter

	private var bssSize = 0



	fun assemble() {
		for(srcFile in context.srcFiles) {
			for(node in srcFile.nodes) {
				when(node) {
					is InstructionNode -> { }
					is LabelNode -> handleLabel(node)
					else -> { }
				}
			}
		}
	}



	private fun handleLabel(node: LabelNode) {
		node.symbol.pos = writer.pos
		if(node.symbol.name == StringInterner.MAIN) {
			if(context.entryPoint != null)
				error("Redeclaration of entry point")
			context.entryPoint = node.symbol
		}
	}



	private fun assembleInstruction(node: InstructionNode) {

	}


}