package smalltalk.vm.primitive;

import smalltalk.vm.VirtualMachine;

import java.text.DecimalFormat;

/** Backing class for Smalltalk Float. */
public class STFloat extends STObject {
	public final float v;

	public STFloat(VirtualMachine vm, float v) {
		super(vm.lookupClass("Float"));
		this.v = v;
	}

	public static STObject perform(BlockContext ctx, int nArgs, Primitive primitive) {
		VirtualMachine vm = ctx.vm;
		int firstArg = ctx.sp - nArgs + 1;
		STObject receiverObj = ctx.stack[firstArg - 1];
		STFloat receiver = (STFloat)receiverObj;
		STObject result = vm.nil();
		float v;
		STObject ropnd;
		switch (primitive){
			case Float_ADD:
				ropnd = ctx.stack[firstArg]; // get right operand (first arg)
				ctx.sp--; // pop ropnd
				ctx.sp--; // pop receiver
				v = receiver.v + ((STFloat)ropnd).v;
				result = new STFloat(vm, v);
				break;
			case Float_SUB:
				ropnd = ctx.stack[firstArg]; // get right operand (first arg)
				ctx.sp--; // pop ropnd
				ctx.sp--; // pop receiver
				v = receiver.v - ((STFloat)ropnd).v;
				result = new STFloat(vm, v);
				break;
			case Float_MULT:
				ropnd = ctx.stack[firstArg]; // get right operand (first arg)
				ctx.sp--; // pop ropnd
				ctx.sp--; // pop receiver
				v = receiver.v * ((STFloat)ropnd).v;
				result = new STFloat(vm, v);
				break;
			case Float_DIV:
				ropnd = ctx.stack[firstArg]; // get right operand (first arg)
				ctx.sp--; // pop ropnd
				ctx.sp--; // pop receiver
				v = receiver.v / ((STFloat)ropnd).v;
				result = new STFloat(vm, v);
				break;
			case Float_LT:
				ropnd = ctx.stack[firstArg];
				ctx.sp--;
				ctx.sp--;
				result = vm.newBoolean(receiver.v < ((STFloat)ropnd).v);
				break;
			case Float_LE:
				ropnd = ctx.stack[firstArg];
				ctx.sp--;
				ctx.sp--;
				result = vm.newBoolean(receiver.v <= ((STFloat)ropnd).v);
				break;
			case Float_GT:
				ropnd = ctx.stack[firstArg];
				ctx.sp--;
				ctx.sp--;
				result = vm.newBoolean(receiver.v > ((STFloat)ropnd).v);
				break;
			case Float_GE:
				ropnd = ctx.stack[firstArg];
				ctx.sp--;
				ctx.sp--;
				result = vm.newBoolean(receiver.v >= ((STFloat)ropnd).v);
				break;
			case Float_EQ:
				ropnd = ctx.stack[firstArg];
				ctx.sp--;
				ctx.sp--;
				result = vm.newBoolean(receiver.v == ((STFloat)ropnd).v);
				break;
			case Float_ASINTEGER:
				ctx.sp--;
				result = new STInteger(vm,((Float)receiver.v).intValue());
				break;
			default:
				break;
		}
		return result;
	}

	@Override
	public String toString() {
		DecimalFormat df = new DecimalFormat("#.#####");
		return df.format(v);
	}
}
