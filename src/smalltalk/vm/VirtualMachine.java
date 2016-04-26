package smalltalk.vm;

import org.antlr.symtab.ClassSymbol;
import org.antlr.symtab.Symbol;

import org.antlr.symtab.Utils;
import smalltalk.compiler.STClass;
import smalltalk.compiler.STSymbolTable;
import smalltalk.vm.exceptions.*;
import smalltalk.vm.primitive.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A VM for a subset of Smalltalk.
 * <p>
 * 3 HUGE simplicity factors in this implementation: we ignore GC,
 * efficiency, and don't expose execution contexts to smalltalk programmers.
 * <p>
 * Because of the shared {@link SystemDictionary#objects} list (ThreadLocal)
 * in SystemDictionary, each VirtualMachine must run in its own thread
 * if you want multiple.
 */
public class VirtualMachine {
      /**
       * The dictionary of global objects including class meta objects
       */
      public final SystemDictionary systemDict; // singleton

      /**
       * "This is the active context itself. It is either a BlockContext
       * or a BlockContext." BlueBook p 605 in pdf.
       */
      public BlockContext ctx;

      /**
       * Trace instructions and show stack during exec?
       */
      public boolean trace = false;

      public VirtualMachine(STSymbolTable symtab) {

            systemDict = new SystemDictionary(this);
            for (Symbol s : symtab.GLOBALS.getSymbols()) {
                  if (s instanceof ClassSymbol) {
                        systemDict.define(s.getName(),
                                new STMetaClassObject(this, (STClass) s));
                  }
            }
            STObject transcript = new STObject(systemDict.lookupClass("TranscriptStream"));
            systemDict.define("Transcript", transcript);
            systemDict.define("TRUE", new STBoolean(this, true));
            systemDict.define("FALSE", new STBoolean(this, false));
            systemDict.define("NIL", new STNil(this));
            // create system dictionary and predefined Transcript
            // convert symbol table ClassSymbols to STMetaClassObjects
      }

      /**
       * look up MainClass>>main and execute it
       */
      public STObject execMain() {
            STMetaClassObject mainMetaClassObject = systemDict.lookupClass("MainClass");
            if (null != mainMetaClassObject) {
                  STObject mainObject = new STObject(mainMetaClassObject);
                  STCompiledBlock main = mainMetaClassObject.resolveMethod("main");
                  return exec(mainObject, main);
            } else return nil();
      }

      /**
       * Begin execution of the bytecodes in method relative to a receiver
       * (self) and within a particular VM. exec() creates an initial
       * method context to simulate a call to the method passed in.
       * <p>
       * Return the value left on the stack after invoking the method,
       * or return self/receiver if there's nothing on the stack.
       */
      public STObject exec(STObject self, STCompiledBlock method) {
            ctx = null;
            int firstArg;
            int secondArg;
            BlockContext initialContext = new BlockContext(this, method, self);
            pushContext(initialContext);
            while (ctx.ip < ctx.compiledBlock.bytecode.length) {
                  if ( trace ) traceInstr();
                  ctx.prev_ip = ctx.ip;
                  int op = ctx.compiledBlock.bytecode[ctx.ip++];
                  switch (op) {
                        case Bytecode.BLOCK:
                              int blockValue = consumeShort(ctx.ip);
                              BlockDescriptor bd = new BlockDescriptor(ctx.enclosingMethodContext.compiledBlock.blocks[blockValue], ctx);
                              ctx.push(bd);
                              break;

                        case Bytecode.BLOCK_RETURN:
                              STObject s = ctx.pop();
                              popContext();
                              ctx.push(s);
                              break;

                        case Bytecode.DBG:
                              int DbgValue = consumeShort(ctx.ip);
                              ctx.currentFile = ctx.compiledBlock.literals[DbgValue];
                              int DbgValue2 = consumeInt(ctx.ip);
                              ctx.currentLine = DbgValue2>>8;
                              ctx.currentCharPos = ( DbgValue2<<24)>>24;
                              break;

                        case Bytecode.FALSE:
                              ctx.push(newBoolean(false));
                              break;

                        case Bytecode.NIL:
                              ctx.push(nil());
                              break;

                        case Bytecode.PUSH_CHAR:
                              ctx.push(newChar());
                              break;

                        case Bytecode.PUSH_INT:
                              int push_int = consumeInt(ctx.ip);
                              ctx.push(newInteger(push_int));
                              break;

                        case Bytecode.PUSH_FLOAT:
                              Float push_float = Float.intBitsToFloat(consumeInt(ctx.ip));
                              ctx.push(newFloat(push_float));
                              break;

                        case Bytecode.PUSH_FIELD:
                              ctx.push(ctx.receiver.fields[consumeShort(ctx.ip)]);
                              break;

                        case Bytecode.PUSH_LOCAL:
                              firstArg = consumeShort(ctx.ip);
                              secondArg = consumeShort(ctx.ip);
                              BlockContext tmpCtx = ctx;
                              while (firstArg>0) {
                                    tmpCtx = tmpCtx.enclosingContext;
                                    firstArg--;
                              }
                              ctx.push(tmpCtx.locals[secondArg]);
                              break;

                        case Bytecode.PUSH_LITERAL:
                              int literalIndex = consumeShort(ctx.ip);
                              ctx.push(newString(ctx.compiledBlock.literals[literalIndex]));
                              break;

                        case Bytecode.PUSH_GLOBAL:
                              literalIndex = consumeShort(ctx.ip);
                              ctx.push(systemDict.lookup(ctx.compiledBlock.literals[literalIndex]));
                              break;

                        case Bytecode.PUSH_ARRAY:
                              int arrayIndex = consumeShort(ctx.ip);
                              ctx.push(newArray(this, arrayIndex));
                              break;

                        case Bytecode.POP:
                              ctx.pop();
                              break;

                        case Bytecode.RETURN:
                              STObject retValue = ctx.pop();
                              if (ctx.enclosingMethodContext.enclosingContext != BlockContext.RETURNED) {
                                    ctx = ctx.enclosingMethodContext;
                                    ctx.enclosingContext = BlockContext.RETURNED;
                                    popContext();
                                    if (ctx == null) {
                                          return retValue;
                                    } else {
                                          ctx.push(retValue);
                                    }
                              } else {
                                    error("BlockCannotReturn", ctx.compiledBlock.enclosingClass.getName() + ">>" +
                                            ctx.compiledBlock.name + " can't trigger return again from method " +
                                            ctx.enclosingMethodContext.compiledBlock.qualifiedName);
                              }
                              break;

                        case Bytecode.SELF:
                              ctx.push(ctx.receiver);
                              break;

                        case Bytecode.STORE_FIELD:
                              int fieldsValue = consumeShort(ctx.ip);
                              ctx.receiver.fields[fieldsValue] = ctx.top();
                              break;

                        case Bytecode.STORE_LOCAL:
                              firstArg = consumeShort(ctx.ip);
                              secondArg = consumeShort(ctx.ip);
                              tmpCtx = ctx;
                              while (firstArg > 0) {
                                    tmpCtx = tmpCtx.enclosingContext;
                                    firstArg--;
                              }
                              tmpCtx.locals[secondArg] = ctx.top();
                              break;

                        case Bytecode.SEND:
                              int nArgs = consumeShort(ctx.ip);
                              int Index = consumeShort(ctx.ip);
                              STObject recv = ctx.stack[ctx.sp - nArgs];
                              String msgName = ctx.compiledBlock.literals[Index];
                              STCompiledBlock blk = recv.getSTClass().resolveMethod(msgName);
                              if(!(recv instanceof STMetaClassObject) && blk.isClassMethod){
                                          throw new ClassMessageSentToInstance(msgName+" is a class method sent to instance of "+recv.metaclass.getName(), getVMStackString());
                              }
                              if (blk.isPrimitive()) {
                                    STObject result = blk.primitive.perform(ctx, nArgs);
                                    if (result != null) ctx.push(result);
                              } else {
                                    if (recv instanceof  STMetaClassObject && !blk.isClassMethod) {
                                          throw  new MessageNotUnderstood(msgName+" is an instance method sent to class object "+ ((STMetaClassObject) recv).getName(), getVMStackString());
                                    }
                                    BlockContext newCtx = new BlockContext(this, blk, recv);
                                    newCtx.enclosingMethodContext = newCtx;
                                    for (int index = nArgs; index > 0; index--) newCtx.locals[index - 1] = ctx.pop();
                                    ctx.pop();
                                    pushContext(newCtx);
                              }
                              break;

                        case Bytecode.SEND_SUPER:
                              firstArg = consumeShort(ctx.ip);//
                              STObject receiver = ctx.stack[ctx.sp - firstArg];
                              secondArg = consumeShort(ctx.ip);
                              String msg = ctx.compiledBlock.literals[secondArg];
                              STCompiledBlock methodBlk;
                              methodBlk = receiver.getSTClass().superClass.resolveMethod(msg);
                              if (methodBlk.isClassMethod && !(receiver instanceof STMetaClassObject)) {
                                    error("ClassMessageSentToInstance", msg + " is a class method sent to instance of " + receiver.getSTClass().getName());
                              } else if (!methodBlk.isClassMethod && receiver instanceof STMetaClassObject) {
                                    error("MessageNotUnderstood", msg + " is an instance method sent to class object " + receiver.getSTClass().getName());
                              }

                              if (methodBlk.isPrimitive()) {
                                    STObject result = methodBlk.primitive.perform(ctx, firstArg);
                                    if (result != null) {
                                          ctx.push(result);
                                    }
                              } else {
                                    BlockContext currentCtx = new BlockContext(this, methodBlk, receiver);
                                    for (int i = firstArg - 1; i >= 0; i--) {
                                          currentCtx.locals[i] = ctx.pop();
                                    }
                                    ctx.pop();
                                    pushContext(currentCtx);
                              }
                              break;

                        case Bytecode.TRUE:
                              ctx.push(newBoolean(true));
                              break;

                  }
                  if ( trace ) traceStack();
            }
            return ctx != null ? ctx.receiver : null;
      }

      private STObject newArray(VirtualMachine vm, int num) {
            STObject[] stObjects = new STObject[num];
            for (int i = num - 1; i >= 0; i--) {
                  stObjects[i] = ctx.pop();
            }
            return new STArray(vm, stObjects);
      }

      public void error(String type, String msg) throws VMException {
            error(type, null, msg);
      }

      public void error(String type, Exception e, String msg) throws VMException {
            String stack = getVMStackString();
            switch (type) {
                  case "MessageNotUnderstood":
                        throw new MessageNotUnderstood(msg, stack);
                  case "ClassMessageSentToInstance":
                        throw new ClassMessageSentToInstance(msg, stack);
                  case "IndexOutOfRange":
                        throw new IndexOutOfRange(msg, stack);
                  case "BlockCannotReturn":
                        throw new BlockCannotReturn(msg, stack);
                  case "StackUnderflow":
                        throw new StackUnderflow(msg, stack);
                  case "UndefinedGlobal":
                        throw new UndefinedGlobal(msg, stack);
                  case "MismatchedBlockArg":
                        throw new MismatchedBlockArg(msg, stack);
                  case "InternalVMException":
                        throw new InternalVMException(e, msg, stack);
                  case "UnknownClass":
                        throw new UnknownClass(msg, stack);
                  case "TypeError":
                        throw new TypeError(msg, stack);
                  case "UnknownField":
                        throw new UnknownField(msg, stack);
                  default:
                        throw new VMException(msg, stack);
            }
      }

      public void error(String msg) throws VMException {
            error("unknown", msg);
      }

      public void pushContext(BlockContext ctx) {
            ctx.invokingContext = this.ctx;
            this.ctx = ctx;
      }

      public void popContext() {
            ctx = ctx.invokingContext;
      }

      public static STObject TranscriptStream_SHOW(BlockContext ctx, int nArgs, Primitive primitive) {
            VirtualMachine vm = ctx.vm;
            vm.assertNumOperands(nArgs + 1); // ensure args + receiver
            int firstArg = ctx.sp - nArgs + 1;
            STObject receiverObj = ctx.stack[firstArg - 1];
            vm.assertEqualBackingTypes(receiverObj, "TranscriptStream");
            STObject arg = ctx.stack[firstArg];
            System.out.println(arg.asString());
            ctx.sp -= nArgs + 1; // pop receiver and arg
            return receiverObj;  // leave receiver on stack for primitive methods
      }

      //
      private void assertEqualBackingTypes(STObject receiverObj, String transcriptStream) {

      }

      public void assertNumOperands(int i) {

      }

      public STMetaClassObject lookupClass(String id) {
            return systemDict.lookupClass(id);
      }

      public STInteger newInteger(int v) {
            return new STInteger(this, v);
      }

      public STCharacter newChar() {
            return new STCharacter(this, (char) consumeShort(ctx.ip));
      }
      public STFloat newFloat(float v) {
            return new STFloat(this, v);
      }

      public STString newString(String s) {
            return new STString(this, s);
      }

      public STBoolean newBoolean(boolean b) {
            return (STBoolean)systemDict.lookup(String.valueOf(b).toUpperCase());
      }

      public STNil nil() {
            return (STNil) systemDict.lookup("NIL");
      }

      private int consumeInt(int index) {
            int x = getInt(index);
            ctx.ip += Bytecode.OperandType.INT.sizeInBytes;
            return x;
      }

      public int consumeShort(int index) {
            int x = getShort(index);
            ctx.ip += Bytecode.OperandType.SHORT.sizeInBytes;
            return x;
      }

      // get short operand out of bytecode sequence
      public int getShort(int index) {
            byte[] code = ctx.compiledBlock.bytecode;
            return Bytecode.getShort(code, index);
      }

      public int getInt(int index) {
            byte[] code = ctx.compiledBlock.bytecode;
            return Bytecode.getInt(code, index);
      }

      // D e b u g g i n g

      void trace() {
            traceInstr();
            traceStack();
      }

      void traceInstr() {
            String instr = Bytecode.disassembleInstruction(ctx.compiledBlock, ctx.ip);
            System.out.printf("%-40s", instr);
      }

      void traceStack() {
            BlockContext c = ctx;
            List<String> a = new ArrayList<>();
            while (c != null) {
                  a.add(c.toString());
                  c = c.invokingContext;
            }
            Collections.reverse(a);
            System.out.println(Utils.join(a, ", "));
      }

      public String getVMStackString() {
            StringBuilder stack = new StringBuilder();
            BlockContext c = ctx;
            while (c != null) {
                  int ip = c.prev_ip;
                  if (ip < 0) ip = c.ip;
                  String instr = Bytecode.disassembleInstruction(c.compiledBlock, ip);
                  String location = c.currentFile + ":" + c.currentLine + ":" + c.currentCharPos;
                  String mctx = c.compiledBlock.qualifiedName + pLocals(c) + pContextWorkStack(c);
                  String s = String.format("    at %50s%-20s executing %s\n",
                          mctx,
                          String.format("(%s)", location),
                          instr);
                  stack.append(s);
                  c = c.invokingContext;
            }
            return stack.toString();
      }

      public String pContextWorkStack(BlockContext ctx) {
            StringBuilder buf = new StringBuilder();
            buf.append("[");
            for (int i = 0; i <= ctx.sp; i++) {
                  if (i > 0) buf.append(", ");
                  pValue(buf, ctx.stack[i]);
            }
            buf.append("]");
            return buf.toString();
      }

      public String pLocals(BlockContext ctx) {
            StringBuilder buf = new StringBuilder();
            buf.append("[");
            for (int i = 0; i < ctx.locals.length; i++) {
                  if (i > 0) buf.append(", ");
                  pValue(buf, ctx.locals[i]);
            }
            buf.append("]");
            return buf.toString();
      }

      void pValue(StringBuilder buf, STObject v) {
            if (v == null) buf.append("null");
            else if (v == nil()) buf.append("nil");
            else if (v instanceof STString) buf.append("'" + v.asString() + "'");
            else if (v instanceof BlockDescriptor) {
                  BlockDescriptor blk = (BlockDescriptor) v;
                  buf.append(blk.block.name);
            } else if (v instanceof STMetaClassObject) {
                  buf.append(v.toString());
            } else {
                  STObject r = v.asString(); //getAsString(v);
                  buf.append(r.toString());
            }
      }
}
