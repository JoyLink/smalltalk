package smalltalk.compiler;

import org.antlr.symtab.Scope;
import org.antlr.symtab.StringTable;
import org.antlr.symtab.Symbol;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.NotNull;
import org.antlr.v4.runtime.tree.TerminalNode;
import smalltalk.vm.primitive.STCompiledBlock;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fill STBlock, STMethod objects in Symbol table with bytecode,
 * {@link smalltalk.vm.primitive.STCompiledBlock}.
 */
public class CodeGenerator extends SmalltalkBaseVisitor<Code> {
	public Scope currentScope;
	public final Compiler compiler;
	public final Map<Scope, StringTable> blockToStrings = new HashMap<>();
	private String file;
	public CodeGenerator(Compiler compiler) {
		this.compiler = compiler;
		file = compiler.fileName;
	}

	@Override
	protected Code aggregateResult(Code aggregate, Code nextResult) {
		if (aggregate != Code.None) {
			if (nextResult != Code.None) {
				return aggregate.join(nextResult);
			}
			return aggregate;
		} else {
			return nextResult;
		}
	}

	@Override
	protected Code defaultResult() {
		return Code.None;
	}

	private STCompiledBlock getCompiledBlock(STBlock scope, Code code) {
		STCompiledBlock stCB = new STCompiledBlock(scope);
		if (code != null)
			stCB.bytecode = code.bytes();
		if (blockToStrings.containsKey(scope)) {
			stCB.literals = blockToStrings.get(scope).toArray();
			stCB.initialLiteralAsStrings();
		}
		return stCB;
	}

	private Code store(String text) {
		Code code = new Code();
		Symbol symbol = currentScope.resolve(text);
		int intValue = symbol.getInsertionOrderNumber();
		if (symbol instanceof STField) code = code.join(Compiler.store_field(intValue));
		 else {
			int relativeNum = ((STBlock) currentScope).getRelativeScopeCount(symbol.getScope().getName());
			code = code.join(Compiler.store_local(relativeNum, intValue));
		}
		return code;
	}

	private Code push(String text) {
		Code code = new Code();
		int depth, posi;
		Symbol symbol = currentScope.resolve(text);

		if (null == symbol || symbol.getScope() == compiler.symtab.GLOBALS) code.join(Compiler.push_global(addToStringTable(text)));
		else {
			posi = symbol.getInsertionOrderNumber();
			if (symbol instanceof STField)  code.join(Compiler.push_field(posi));
			else {
				depth = ((STBlock) currentScope).getRelativeScopeCount(symbol.getScope().getName());
				code.join(Compiler.push_local(depth, posi));
			}
		}
		return code;
	}

	private int addToStringTable(String text) {
		int posi;
		if (text.contains("'")) text = text.substring(text.indexOf("'") + 1, text.lastIndexOf("'"));
		if (null != blockToStrings.get(currentScope))  posi = blockToStrings.get(currentScope).add(text);
		else {
			StringTable strTable = new StringTable();
			posi = strTable.add(text);
			blockToStrings.put(currentScope, strTable);
		}
		return posi;
	}

	public void pushScope(Scope scope) {
		currentScope = scope;
	}

	public void popScope() {
		currentScope = currentScope.getEnclosingScope();
	}

	public int getLiteralIndex(String s) {
		String[] strings;
		if (blockToStrings.containsKey(currentScope)) {
			strings = blockToStrings.get(currentScope).toArray();
			for (int i = 0; i < strings.length; i++)  if (strings[i].equals(s)) return i;
		}
		return -1;
	}

	public Code dbgAtEndMain(Token t) {
		int charPos = t.getCharPositionInLine() + t.getText().length();
		return dbg(t.getLine(), charPos);
	}

	public Code dbgAtEndBlock(Token t) {
		int charPos = t.getCharPositionInLine() + t.getText().length();
		charPos -= 1; // point at ']'
		return dbg(t.getLine(), charPos);
	}

	public Code dbg(Token t) {
		return dbg(t.getLine(), t.getCharPositionInLine());
	}

	public Code dbg(int line, int charPos) {
		return Compiler.dbg(getLiteralIndex(compiler.getFileName()), line, charPos);
	}

	@Override
	public Code visitFile(@NotNull SmalltalkParser.FileContext ctx) {
		if (!ctx.main().getText().isEmpty()) visit(ctx.main());
		for (int i = 0; i < ctx.classDef().size(); i++) visit(ctx.classDef(i));
		return Code.None;
	}

	@Override
	public Code visitMain(@NotNull SmalltalkParser.MainContext ctx) {
		pushScope(ctx.classScope);
		pushScope(ctx.scope);
		Code code = new Code() ;
		code = visitChildren(ctx);
		if (ctx.body() instanceof SmalltalkParser.FullBodyContext) {
			if (compiler.genDbg) {
				addToStringTable(file);
				code = Code.join(code, dbgAtEndMain(ctx.stop));
			}
			code.join(Compiler.pop());
		}
		code.join(Compiler.push_self()).join(Compiler.method_return());
		ctx.scope.compiledBlock = getCompiledBlock(ctx.scope, code);
		popScope();
		popScope();
		return code;
	}

	@Override
	public Code visitPrimitiveMethodBlock(@NotNull SmalltalkParser.PrimitiveMethodBlockContext ctx) {
		SmalltalkParser.MethodContext methodContext = (SmalltalkParser.MethodContext) ctx.getParent();
		pushScope(methodContext.scope);
		Code code = visitChildren(ctx);
		if (compiler.genDbg) { // put dbg in front of push_self
			addToStringTable(file);
			code = Code.join(code, dbgAtEndBlock(ctx.stop));
		}
		methodContext.scope.compiledBlock = getCompiledBlock(methodContext.scope, code);
		popScope();
		return Code.None;
	}

	@Override
	public Code visitSmalltalkMethodBlock(@NotNull SmalltalkParser.SmalltalkMethodBlockContext ctx) {
//		System.out.println("visitSmalltalkMethodBlock");
		SmalltalkParser.MethodContext methodNode = (SmalltalkParser.MethodContext) ctx.getParent();
		pushScope(methodNode.scope);
		Code code = visitChildren(ctx);
		if (compiler.genDbg) { // put dbg in front of push_self
			addToStringTable(file);
			code = Code.join(code, dbgAtEndBlock(ctx.stop));
		}
		if (ctx.body() instanceof SmalltalkParser.FullBodyContext) code = code.join(Compiler.pop()); // visitFullBody() doesn't have last pop; we toss here but use with block_return in visitBlock
		code = code.join(Compiler.push_self());
		code = code.join(Compiler.method_return());
		methodNode.scope.compiledBlock = getCompiledBlock(methodNode.scope, code);
		popScope();
		return Code.None;
	}

	@Override
	public Code visitAssign(@NotNull SmalltalkParser.AssignContext ctx) {
//		System.out.println("visitAssign");
		Code e = visit(ctx.messageExpression());
		Code store = store(ctx.lvalue().ID().getText());
		Code code = e.join(store);
		if (compiler.genDbg) {
			addToStringTable(file);
			code = dbg(ctx.start).join(code);
		}
		return code;
	}

	@Override
	public Code visitBlock(@NotNull SmalltalkParser.BlockContext ctx) {
//		System.out.println("visitBlock");
		pushScope(ctx.scope);
		short blockIndex = (short) ctx.scope.index;
		Code code = new Code();
		code.join(Compiler.block(blockIndex));
		Code sideCode = new Code();
		sideCode = visitChildren(ctx);
		if (ctx.body() instanceof SmalltalkParser.EmptyBodyContext) {
			sideCode = sideCode.join(Compiler.push_nil());
		}
		if (compiler.genDbg) {
			addToStringTable(file);
			sideCode = Code.join(sideCode, dbgAtEndBlock(ctx.stop));
		}
		sideCode = sideCode.join(Compiler.block_return());
		ctx.scope.compiledBlock = getCompiledBlock(ctx.scope, sideCode);
		popScope();
		return code;
	}

	@Override
	public Code visitFullBody(@NotNull SmalltalkParser.FullBodyContext ctx) {
//		System.out.println("visitFullyBody");
		Code code = new Code();
		if (ctx.stat().size()<2) code = code.join(visitChildren(ctx));
		else {
			if(ctx.localVars()!=null)code.join(visit(ctx.localVars()));
			code = code.join(visit(ctx.stat(0)));
			int	length = ctx.stat().size();
			for (int i = 1; i < length; i++) {
				code = code.join(Compiler.pop());
				code = code.join(visit(ctx.stat(i)));
			}
		}
		return code;
	}

	@Override
	public Code visitEmptyBody(@NotNull SmalltalkParser.EmptyBodyContext ctx) {
//		System.out.println("visitEmptyBody");
		Code code = new Code();
		if(compiler.genDbg){
			addToStringTable(file);
			code.join(dbg(ctx.stop));
		}
		return code;
	}

	@Override
	public Code visitReturn(@NotNull SmalltalkParser.ReturnContext ctx) {
//		System.out.println("visitReturn");
		Code code = visit(ctx.messageExpression());
		if (compiler.genDbg) {
			addToStringTable(file);
			code = Code.join(code, dbg(ctx.start)); // put dbg after expression as that is when it executes
		}
		code = code.join(Compiler.method_return());
		return code;
	}

	@Override
	public Code visitBinaryExpression(@NotNull SmalltalkParser.BinaryExpressionContext ctx) {
//		System.out.println("visitBinaryExpression");
		Code code =  visit(ctx.unaryExpression(0));
		for (int i = 0 ; i < ctx.bop().size();i++){
			code = aggregateResult(code, visit(ctx.unaryExpression(i + 1)));
			if (compiler.genDbg) {
				addToStringTable(file);
				code = aggregateResult(dbg(ctx.bop(i).start),code);
			}
			code = aggregateResult(code,Compiler.send(1,addToStringTable(ctx.bop(i).getText())));
		}
		return code;
	}

	@Override
	public Code visitKeywordSend(@NotNull SmalltalkParser.KeywordSendContext ctx) {
//		System.out.println("visitKeywordSend");
		Code code1 = new Code();
		code1 = visit(ctx.recv);
		Code code2 = new Code();
		for (int i = 0; i < ctx.args.size(); i++) code2 = code2.join(visit(ctx.args.get(i)));
		if (!ctx.KEYWORD().isEmpty()) {
			List<TerminalNode> key = ctx.KEYWORD();
			List<String> strS = org.antlr.symtab.Utils.map(key, TerminalNode::getText);
			String str = org.antlr.symtab.Utils.join(strS, "");
			addToStringTable(str);
			if (compiler.genDbg) {
				addToStringTable(file);
				code2 = Code.join( code2,dbg(ctx.KEYWORD(0).getSymbol()));
			}
			code2 = code2.join(Compiler.send(ctx.args.size(), addToStringTable(str)));
		}
		code1 = code1.join(code2);
		return code1;
	}

	@Override
	public Code visitUnarySuperMsgSend(@NotNull SmalltalkParser.UnarySuperMsgSendContext ctx) {
//		System.out.println("visitUnarySuperMsgSend");
		Code code = new Code();
		code.join(Compiler.push_self()).join(Compiler.send_super(0, addToStringTable(ctx.ID().getText())));
		return code;
	}

	@Override
	public Code visitUnaryMsgSend(@NotNull SmalltalkParser.UnaryMsgSendContext ctx) {
//		System.out.println("visitUnaryMsgSend");
		Code code = new Code();
		code = visit(ctx.unaryExpression());
		addToStringTable(ctx.ID().getText());
		if (compiler.genDbg) {
			addToStringTable(file);
			code = Code.join(dbg(ctx.stop),code);
		}
		code = code.join(Compiler.send(0, addToStringTable(ctx.ID().getText())));
		return code;
	}

	@Override
	public Code visitLiteral(@NotNull SmalltalkParser.LiteralContext ctx) {
//		System.out.println("visitLiteral");
		Code code = new Code();
		if (ctx.NUMBER() != null) {
			if (ctx.NUMBER().getText().contains(".")) {
				code = code.join(Compiler.push_float(new Float(ctx.NUMBER().getText())));
			} else
				code = code.join(Compiler.push_int(new Integer(ctx.NUMBER().getText())));
		} else if ( ctx.CHAR() != null) {
			code = code.join(Compiler.push_char(ctx.CHAR().getText().charAt(1)));
		} else if (ctx.STRING() != null) {
			code = code.join(Compiler.push_literal(addToStringTable(ctx.STRING().getText())));
		} else {
			String string = ctx.getText();
			if (string.equals("nil"))  code = code.join(Compiler.push_nil());
			else if (string.equals("self")) code = code.join(Compiler.push_self());
			else if (string.equals("true"))  code = code.join(Compiler.push_true());
			else if (string.equals("false")) code = code.join(Compiler.push_false());
		}
		return code;
	}

	@Override
	public Code visitArray(@NotNull SmalltalkParser.ArrayContext ctx) {
//		System.out.println("visitArray");
		Code code = visitChildren(ctx);
		code.join(Compiler.push_array(ctx.messageExpression().size()));
		return code;
	}

	@Override
	public Code visitId(@NotNull SmalltalkParser.IdContext ctx) {
//		System.out.println("visitId");
		Code code = new Code();
		code.join(push(ctx.getText()));
		return code;
	}


}
