package eyre.gen

import eyre.*

data class ParsedEnc(
	val mnemonic : Mnemonic,
	val prefix   : Prefix,
	val escape   : Escape,
	val opcode   : Int,
	val ext      : Int,
	val mask     : Int,
	val rw       : Int,
	val o16      : Int,
	val a32      : Int,
	val opreg    : Boolean,
	val ops      : List<Op>,
	val pseudo   : Int,
	val vex      : Boolean,
	val vexw     : VexW,
	val vexl     : VexL,
) {
	val op1 = ops.getOrElse(0) { Op.NONE }
	val op2 = ops.getOrElse(1) { Op.NONE }
	val op3 = ops.getOrElse(2) { Op.NONE }
	val op4 = ops.getOrElse(3) { Op.NONE }

	val actualExt = ext.coerceAtLeast(0)

	val opEnc: OpEnc = when {
		op1.type.isMem && op3.type.isReg -> OpEnc.MVR
		op1.type.isMem && (op2.type.isReg || op2.type.isNone) -> OpEnc.MRV
		op1.type.isReg && op2.type.isMem -> OpEnc.RMV
		op1.type.isReg && op2.type.isReg && hasExt -> OpEnc.VMR
		op1.type.isReg && (op2.type.isReg || op2.type.isMem) -> OpEnc.RMV
		else -> OpEnc.RVM
	}

	val opcode1 = opcode and 0xFF
	val opcode2 = opcode shr 8
	val hasExt get() = ext >= 0
	fun withOp(index: Int, op: Op) = ArrayList(ops).also { it[index] = op }

	val autoOps: AutoOps

	init {
		var width = 0
		var vsib = 0
		for(op in ops) when(op) {
			Op.M8    -> width = 1
			Op.M16   -> width = 2
			Op.M32   -> width = 3
			Op.M64   -> width = 4
			Op.M80   -> width = 5
			Op.M128  -> width = 6
			Op.M256  -> width = 7
			Op.M512  -> width = 8
			Op.VM32X -> { width = 3; vsib = 1 }
			Op.VM64X -> { width = 4; vsib = 1 }
			Op.VM32Y -> { width = 3; vsib = 2 }
			Op.VM64Y -> { width = 4; vsib = 2 }
			Op.VM32Z -> { width = 3; vsib = 3 }
			Op.VM64Z -> { width = 4; vsib = 3 }
			else     -> continue
		}

		autoOps = AutoOps(
			op1.type.ordinal,
			op2.type.ordinal,
			op3.type.ordinal,
			op4.type.ordinal,
			width,
			vsib,
			if(op2 == Op.ST0) 1 else 0
		)
	}

}