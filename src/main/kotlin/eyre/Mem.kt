package eyre

/**
 * Represents either a memory operand or an immediate operand.
 */
class Mem {

	companion object { val NULL = Mem() }

    var node: AstNode = NullNode
    var scale = 0
    var index = Reg.NONE
    var base = Reg.NONE
    var aso = 0
    var relocs = 0
    var hasBase = false
    var hasIndex = false
    var vsib = 0
    var disp = 0L
    var width: Width? = null

    fun reset() {
        node = NullNode
		base = Reg.NONE
		index = Reg.NONE
        scale = 0
        aso = 0
        relocs = 0
        vsib = 0
        disp = 0L
        width = null
    }

    val rexX     get() = index.rex
    val rexB     get() = base.rex
    val vexX     get() = index.vexRex
    val vexB     get() = base.vexRex
    val hasReloc get() = relocs != 0
	val isImm8   get() = disp.isImm8
	val isImm16  get() = disp.isImm16
	val isImm32  get() = disp.isImm32
	val a32      get() = aso == 1

    fun swapBaseIndex() {
		val temp = index
		index = base
		base = temp
	}

}