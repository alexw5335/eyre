package eyre

class Compiler(private val context: CompilerContext) {

	fun compile() {
		val parser = Parser()

		for(srcFile in context.srcFiles)
			parser.parse(srcFile)
	}

}