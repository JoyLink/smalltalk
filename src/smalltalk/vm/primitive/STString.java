package smalltalk.vm.primitive;

import smalltalk.vm.VirtualMachine;


public class STString extends STObject {
	public final String s;

	public STString(VirtualMachine vm, char c) {
		this(vm, String.valueOf(c));
	}

	public STString(VirtualMachine vm, String s) {
		super(vm.lookupClass("String"));
		this.s = s;
	}

	public static STObject perform(BlockContext ctx, int nArgs, Primitive primitive) {
		VirtualMachine vm = ctx.vm;
		vm.assertNumOperands(nArgs+1); // ensure args + receiver
		int firstArg = ctx.sp - nArgs + 1;
		STObject receiverObj = ctx.stack[firstArg - 1];
		STObject ropnd;
		STObject result = vm.nil();
		switch ( primitive ) {
			case String_Class_NEW:
				ropnd = ctx.pop();
				ctx.pop();
				if(ropnd instanceof STCharacter){
					result = new STString(vm,(char)((STCharacter)ropnd).c);
				}else {
					result = new STString(vm,((STString)ropnd).s);
				}
				break;
			case String_CAT:
				ropnd = ctx.stack[firstArg];
				ctx.sp--;
				ctx.sp--;
				result = new STString(vm,receiverObj.toString()+ropnd.toString());
				break;
			case String_EQ:
				ropnd = ctx.stack[firstArg];
				ctx.sp--;
				ctx.sp--;
				result = vm.newBoolean(((STString)ropnd).s.equals(((STString)receiverObj).s));
				break;
			case String_ASARRAY:
				String str = ((STString)receiverObj).s;
				STObject[] charArray = new STObject[str.length()];
				for (int i=0;i<str.length();i++){
					charArray[i]=new STCharacter(vm,str.charAt(i));
				}
				result = new STArray(vm,charArray);
				break;
			default:
				break;
		}
		return result;
	}

	public STString asString() { return this; }
	public String toString() { return s; }
}
