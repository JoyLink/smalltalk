package smalltalk.compiler;

import org.antlr.symtab.Scope;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import smalltalk.misc.Utils;
import smalltalk.vm.Bytecode;

import java.util.ArrayList;
import java.util.List;

public class Compiler {
      protected final STSymbolTable symtab;
      public final List<String> errors = new ArrayList<>();
      protected SmalltalkParser parser;
      protected CommonTokenStream tokens;
      protected SmalltalkParser.FileContext fileTree;
      protected String fileName;
      public boolean genDbg; // generate dbg file,line instructions

      protected ParseTreeWalker walker;

      public Compiler() {
            symtab = new STSymbolTable();
            fileName = "<unknown>";
      }

      public Compiler(STSymbolTable symtab) {
            this.symtab = symtab;
            fileName = "<string>";
      }

      public STSymbolTable compile(ANTLRInputStream input) {
            // parse class(es)
            fileTree = parseClasses(input);
            if(null != input.name){
                  fileName = input.name;
                  fileName = fileName.substring(fileName.lastIndexOf('/')+1);
            }
//        System.out.println(fileName);
            // define symbols
            defSymbols(fileTree);
            // resolve symbols
            resolveSymbols(fileTree);
            // gen code
            CodeGenerator generator = new CodeGenerator(this);
            generator.visitFile(fileTree);

            return symtab;
      }

      // Convenience methods for code gen
      // these are what I coded imitating ParrT's except nil char int pop
      public static Code push_nil() 				{ return Code.of(Bytecode.NIL); }
      public static Code push_self() 				{ return Code.of(Bytecode.SELF);}
      public static Code push_true()              { return Code.of(Bytecode.TRUE);}
      public static Code push_false()             { return Code.of(Bytecode.FALSE);}

      public static Code push_char(int c)         { return Code.of(Bytecode.PUSH_CHAR).join(Utils.shortToBytes(c));}
      public static Code push_int(int v) 			{ return Code.of(Bytecode.PUSH_INT).join(Utils.intToBytes(v)); }
      public static Code push_float(float v)      { return Code.of(Bytecode.PUSH_FLOAT).join(Utils.floatToBytes(v));}
      public static Code push_field(int v)      { return Code.of(Bytecode.PUSH_FIELD).join(Utils.shortToBytes(v));}
      public static Code push_local(int v1, int v2){
            return Code.of(Bytecode.PUSH_LOCAL).join(Utils.shortToBytes(v1).join(Utils.shortToBytes(v2)));
      }
      public static Code push_literal(int v){
            return Code.of(Bytecode.PUSH_LITERAL).join(Utils.toLiteral(v));
      }
      public static Code push_global(int v){
            return Code.of(Bytecode.PUSH_GLOBAL).join(Utils.toLiteral(v));
      }
      public static Code push_array(int v){
            return Code.of(Bytecode.PUSH_ARRAY).join(Utils.shortToBytes(v));
      }
      public static Code store_field(int v){
            return Code.of(Bytecode.STORE_FIELD).join(Utils.shortToBytes(v));
      }
      public static Code store_local(int v1, int v2){
            return Code.of(Bytecode.STORE_LOCAL).join(Utils.shortToBytes(v1)).join(Utils.shortToBytes(v2));
      }
      public static Code pop() 					{ return Code.of(Bytecode.POP);}

      public static Code send(int s, int i){
            return Code.of(Bytecode.SEND).join(Utils.shortToBytes(s)).join(Utils.toLiteral(i));
      }
      public static Code send_super(int s, int i){
            return Code.of(Bytecode.SEND_SUPER).join(Utils.shortToBytes(s)).join(Utils.toLiteral(i));
      }
      public static Code block(short v)           { return Code.of(Bytecode.BLOCK).join(Utils.shortToBytes(v));}
      public static Code block_return()           { return Code.of(Bytecode.BLOCK_RETURN);}
      public static Code method_return() 			{ return Code.of(Bytecode.RETURN);}

      public static Code dbg(int literalIndex, int line, int charPos) {
            return Code.of(Bytecode.DBG).join(Utils.toLiteral(literalIndex).join(Utils.intToBytes(Bytecode.combineLineCharPos(line,charPos))));
      }

      // Error support
      public void error(String msg) {
            errors.add(msg);
      }


      public String getFileName() {
            return fileName;
      }

      public void defineFields(STClass cl, List<String> instanceVars) {
            if( null != instanceVars ){
                  for (String varStr:instanceVars){
                        try{
                              cl.define(new STField(varStr));
                        }catch (IllegalArgumentException e){
                              error("redefinition of "+varStr+" in "+cl.toQualifierString(">>"));
                        }

                  }
            }
      }

      public void defineArguments(STBlock stBlock, List<String> args) {
            if( null != args ){
                  for (String argStr: args){
                        try{
                              stBlock.define(new STArg(argStr));
                        }catch (IllegalArgumentException e){
                              error("redefinition of "+argStr+" in "+stBlock.toQualifierString(">>"));
                        }

                  }
            }
      }

      public void defineLocals(Scope currentScope, List<String> vars) {
            if( null != vars ){
                  for(String varStr : vars){
                        try {
                              currentScope.define(new STVariable(varStr));
                        }catch (IllegalArgumentException e){
                              error("redefinition of "+varStr+" in "+currentScope.toQualifierString(">>"));
                        }
                  }
            }
      }

      public STMethod createMethod(String methodName, SmalltalkParser.MethodContext ctx) {
            return new STMethod(methodName,ctx);
      }

      public STMethod createMethod(String main, SmalltalkParser.MainContext ctx) {
            return new STMethod(main,ctx);
      }

      public STMethod createPrimitiveMethod(STClass stClass, String selector, String primitiveName,
                                            SmalltalkParser.MethodContext methodNode) {
            return new STPrimitiveMethod(selector,methodNode,primitiveName);
      }

      public STBlock createBlock(STMethod currentBlock, SmalltalkParser.BlockContext ctx) {
            return new STBlock(currentBlock,ctx);
      }


      public SmalltalkParser.FileContext parseClasses(ANTLRInputStream antlrInputStream) {
            ParserRuleContext ruleContext;
            Lexer l = new SmalltalkLexer(antlrInputStream);
            tokens = new CommonTokenStream(l);
            parser = new SmalltalkParser(tokens);
            fileTree = parser.file();
            return fileTree;
      }

      public void defSymbols(ParserRuleContext tree) {
            walker = new ParseTreeWalker();
            walker.walk(new DefineSymbols(this),tree);
      }

      public void resolveSymbols(ParserRuleContext tree) {
            walker = new ParseTreeWalker();
            walker.walk(new ResolveSymbols(this),tree);
      }
}