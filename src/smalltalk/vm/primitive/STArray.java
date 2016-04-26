package smalltalk.vm.primitive;

import smalltalk.vm.VirtualMachine;

/** */
public class STArray extends STObject {
	public final STObject[] elements;

	public STArray(VirtualMachine vm, int n, STObject fill) {
		super(vm.lookupClass("Array"));
		elements = new STObject[n];
		for (int i=0;i<n;i++){
			elements[i] = fill;
		}
	}

	public STArray(VirtualMachine vm, STObject[] stObjects) {
		super(vm.lookupClass("Array"));
		elements = stObjects;
	}

	public static STObject perform(BlockContext ctx, int nArgs, Primitive primitive) {
		VirtualMachine vm = ctx.vm;
		int firstArg = ctx.sp - nArgs + 1;
		STObject receiverObj = ctx.stack[firstArg - 1];
		STObject result = vm.nil();
		STObject mopnd;
		STObject ropnd;
		switch ( primitive ){
			case Array_Class_NEW:
				ropnd = ctx.stack[firstArg];
				ctx.sp--;
				ctx.sp--;
				result = new STArray(vm,((STInteger)ropnd).v,vm.nil());
				break;
			case Array_SIZE:
				ctx.pop();
				result = new STInteger(vm,((STArray)receiverObj).elements.length);
				break;
			case Array_AT:
				ropnd = ctx.pop();
				receiverObj = ctx.pop();
				result = ((STArray)receiverObj).elements[((STInteger)ropnd).v-1];
				break;
			case Array_AT_PUT:
				ropnd = ctx.pop();
				mopnd = ctx.pop();
				receiverObj = ctx.pop();
				((STArray)receiverObj).elements[((STInteger)mopnd).v - 1] = ropnd;
				break;
			default:
				break;
		}
		return result;
	}

	@Override
	public String toString() {
		String arrayStr = "";
		for(STObject o:elements){
			arrayStr+=(o.toString()+". ");
		}
		arrayStr = arrayStr.substring(0,arrayStr.length()-2);
		return  "{"+arrayStr+"}";
	}
}
