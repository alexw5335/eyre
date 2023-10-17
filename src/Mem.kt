package eyre

class Mem {

	companion object { val NULL = Mem() }

	val isNull get() = this == NULL
	val isNotNull get() = this != NULL

	var node: Node = NullNode
	var scale = 0
	var index = Reg.NONE
	var base = Reg.NONE
	var aso = 0
	var relocs = 0
	val hasBase get() = base != Reg.NONE
	val hasIndex get() = index != Reg.NONE
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
		disp = 0
		width = null
	}

	val rexX     get() = index.rex
	val rexB     get() = base.rex
	val vexX     get() = index.vexRex
	val vexB     get() = base.vexRex
	val hasReloc get() = relocs != 0
	val isImm8   get() = disp.isImm8
	val isImm16  get() = disp.isImm16
	val a32      get() = aso == 1

}