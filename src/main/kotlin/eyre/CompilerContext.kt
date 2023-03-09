package eyre

import eyre.util.NativeWriter
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readLines

class CompilerContext(val srcFiles: List<SrcFile>) {


	val symbols = SymbolTable()

	val dlls = HashMap<StringIntern, DllSymbol>()

	val inbuiltDlls = ArrayList<InbuiltDll>()

	var entryPoint: PosSymbol? = null

	var textWriter = NativeWriter()

	var dataWriter = NativeWriter()

	var bssSize = 0

	val relocs = ArrayList<Reloc>()

	val linkWriter = NativeWriter()

	val sections = HashMap<Section, SectionData>()



	fun addInbuiltDll(path: Path) {
		inbuiltDlls.add(InbuiltDll(path.nameWithoutExtension, path.readLines().toHashSet()))
	}



	fun getInbuiltDllImport(name: String) {
		for(dll in inbuiltDlls) {
			if(name !in dll.exports) continue
			val dllSymbol = dlls.getOrPut(DllImport)
		}
	}


}