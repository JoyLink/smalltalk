package smalltalk.vm.primitive;

import smalltalk.vm.VirtualMachine;

/** */
public class STBoolean extends STObject {
	public final boolean b;

	public STBoolean(VirtualMachine vm, boolean b) {
		super(vm.lookupClass("Boolean"));
		this.b = b;
	}

	public static STObject perform(BlockContext ctx, int nArgs, Primitive primitive) {
		VirtualMachine vm = ctx.vm;
		int firstArg = ctx.sp - nArgs + 1;
		STObject receiverObj = ctx.stack[firstArg - 1];
		STBoolean receiver = (STBoolean)receiverObj;
		STObject result = vm.nil();
		STObject ropnd;
		STObject mopnd;
		BlockContext blCtx;
		switch (primitive){
			case Boolean_IFTRUE:
				ropnd = ctx.stack[firstArg];
				ctx.sp--;
				ctx.sp--;
				if(receiver.b){
					blCtx = new BlockContext(vm,(BlockDescriptor)ropnd);
					vm.pushContext(blCtx);
					result = null;
				}else
					result=vm.nil();
				break;
			case Boolean_IFTRUE_IFFALSE:
				mopnd = ctx.stack[firstArg];
				ropnd = ctx.stack[firstArg+1];
				ctx.sp-=3;
				if(receiver.b){
					blCtx = new BlockContext(vm,(BlockDescriptor)mopnd);
				}else{
					blCtx = new BlockContext(vm,(BlockDescriptor)ropnd);
				}
				vm.pushContext(blCtx);
				result = null;
				break;
			case Boolean_NOT:
				ctx.sp--;
				result = vm.newBoolean(!receiver.b);
				break;
			default:
				break;
		}
		return result;
	}

	@Override
	public String toString() {
		return String.valueOf(b);
	}
}
