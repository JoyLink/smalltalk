package smalltalk.vm.primitive;

import smalltalk.vm.VirtualMachine;

public class STCharacter extends STObject {
	public final int c;

	public STCharacter(VirtualMachine vm, int c) {
		super(vm.lookupClass("Character"));
		this.c = c;
	}

	public static STObject perform(BlockContext ctx, int nArgs, Primitive primitive) {
		VirtualMachine vm = ctx.vm;
		int firstArg = ctx.sp - nArgs + 1;
		STObject receiverObj = ctx.stack[firstArg - 1];
		//   STCharacter receiver = (STCharacter)receiverObj;
		STObject result = vm.nil();
		STObject ropnd;
		switch (primitive){
			case Character_ASINTEGER:
				ctx.sp--;
				result = new STInteger( vm, ((STCharacter)receiverObj).c);
				break;
			case Character_Class_NEW:
				ropnd = ctx.stack[firstArg];
				ctx.sp--;
				ctx.sp--;
				result = new STCharacter(vm,((STInteger)ropnd).v);
				break;
			default:
				break;
		}
		return result;
	}

	@Override
	public String toString() {
		return "$"+(char)c;
	}
}
