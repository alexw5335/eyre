package eyre

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.io.path.relativeTo


fun main() {
	Eyre.compile("samples")
	//Eyre.testRandomExpressions()
}



object Eyre {


	fun compile(dir: String, buildPath: Path = Paths.get("build")) {
		val dirPath = Paths.get(dir)
		val files = Files
			.walk(dirPath)
			.toList()
			.filter { it.extension == "eyre" }
			.map { ProjectSrcFile(it.relativeTo(dirPath).toString(), it) }
		Compiler(Context(buildPath, files)).compile()
	}



/*	fun testExpression(expr: String) {
		val file = VirtualSrcFile("virtual.eyre", expr)
		val compiler = Compiler(Context(Paths.get("build"), listOf(file)))
		compiler.parseFile(file)
		compiler.assembler.testExpr(file.nodes.single())
	}



	fun testRandomExpressions() {
		val context = Context(Paths.get(""), emptyList())
		val assembler = Assembler(context)
		for(i in 0 ..< 10)
			if(assembler.testExpr(assembler.randomExpr()))
				break
	}*/


}