package eyre

object Natives {

	external fun init()

	external fun disassemble(pData: Long, offset: Int, length: Int, addr: Long, pIns: Long): Int

	external fun disassembleAndPrint(pData: Long, length: Int, addr: Long)
}