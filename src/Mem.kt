package eyre

class Mem(
	var type: Type = Type.SIB,
	var base: Reg = Reg.NONE,
	var index: Reg = Reg.NONE,
	var scale: Int = 0,
	var disp: Int = 0,
	var reloc: SecPos? = null,
	var immWidth: Width = Width.NONE
) {

	val rexX get() = index.rexX
	val rexB get() = base.rexB

	enum class Type {
		RIP,
		RBP,
		RSP,
		SIB;
	}

	fun rsp(disp: Int) = also { type = Type.RSP; this.disp = disp }
	fun rbp(disp: Int) = also { type = Type.RSP; this.disp = disp }
	fun sib(base: Reg, index: Reg, scale: Int, disp: Int) = also {
		this.type = Type.SIB
		this.base = base
		this.index = index
		this.scale = scale
		this.disp = disp
	}
	fun sib(base: Reg, disp: Int) = also {
		this.type = Type.SIB
		this.base = base
		this.index = Reg.NONE
		this.scale = 0
		this.disp = disp
	}
	fun rip(reloc: SecPos) = also { type = Type.RIP; this.reloc = reloc }
	fun loc(loc: VarLoc) = also {
		when(loc) {
			is GlobalVarLoc -> { type = Type.RIP; reloc = loc.reloc }
			is StackVarLoc -> { type = Type.RBP; disp = loc.disp }
			is RspVarLoc -> { type = Type.RSP; disp = loc.disp }
		}
	}

	companion object {
		fun rsp(disp: Int) = Mem(Type.RSP, disp = disp)
		fun rbp(disp: Int) = Mem(Type.RBP, disp = disp)
		fun sib(base: Reg, disp: Int) = Mem(Type.SIB, base = base, disp = disp)
		fun rip(pos: SecPos) = Mem(Type.RIP, reloc = pos)
		fun loc(loc: VarLoc) = Mem().also { it.loc(loc) }
	}

}