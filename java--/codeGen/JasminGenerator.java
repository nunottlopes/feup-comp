package codeGen;

import symbol.*;
import parser.*;

import java.io.*;
import java.util.*;

public class JasminGenerator{

	private ClassSymbolTable symbolTable;
	private SimpleNode rootNode;

	private PrintWriter printWriter;

	private int labelCounter = 0;

	/*
	 * Constructor of the class
	 */
	public JasminGenerator(ClassSymbolTable symbolTable, SimpleNode rootNode){

		this.symbolTable = symbolTable;
		this.rootNode = (SimpleNode) rootNode.jjtGetChild(0);

		createFile();

		createFileHeader();			// .class .super
		manageFields();				// Global Variables
		manageInit();
		manageMethods();			// Methods

		this.printWriter.close();
	}

	/*
	 * Creates the .j files
	 */
	private void createFile(){

		try{

			File file = new File("jasmin/" + this.symbolTable.getClassName() +  ".j");

			if(!file.exists())
				file.getParentFile().mkdirs();
			
			this.printWriter = new PrintWriter(file);

		} catch(IOException exception){

			exception.printStackTrace();
		}
	}

	/*
	 * Writes to the .j file the .class and the .super
	 */
	private void createFileHeader(){

		this.printWriter.println(".class public " + symbolTable.getClassName());
		this.printWriter.println(".super java/lang/Object\n");
	}

	/*
	 * Writes the global variables (fields)
	 *
	 * .field <access-spec> <field-name> <descriptor>
	 */
	private void manageFields(){

		Map<String, Symbol> map = symbolTable.getGlobal_variables();
		map.forEach((key, value) -> {

			String str = ".field ";
			str += key + " ";
			str += value.getTypeDescriptor();

			this.printWriter.println(str);
		});
	}

	/*
	 * Writes the class initiator
	 */
	private void manageInit(){

		this.printWriter.println("\n.method public <init>()V");
		this.printWriter.println("\taload_0");
		this.printWriter.println("\tinvokenonvirtual java/lang/Object/<init>()V");
		this.printWriter.println("\treturn");
		this.printWriter.println(".end method\n");
	}

	/*
	 * Iterates through every method to create the code for each one
	 */
	private void manageMethods(){

		this.printWriter.println("\n; methods");

		SimpleNode methodsNode = (SimpleNode) this.rootNode.jjtGetChild(this.rootNode.jjtGetNumChildren() - 1);
		for(int i = 0; i < methodsNode.jjtGetNumChildren(); i++){

			manageMethod((SimpleNode) methodsNode.jjtGetChild(i));
		}
	}

	/*
	 * Manages the code generation for each method
	 *
	 * .method <access-spec> <method-spec>
 	 *     <statements>
 	 * .end method
	 */
	private void manageMethod(SimpleNode method){

		if(!(method instanceof ASTMETHOD)){		// Main
			
			String methodName = "main";
			FunctionSymbolTable fst = this.symbolTable.getFunctions().get(methodName);

			manageMethodHeader(methodName, fst);
			manageMethodLimits(fst);
			manageMethodBody((SimpleNode) method.jjtGetChild(1), fst);
			this.printWriter.println("\treturn\n");
		}
		else{

			String methodName = ((SimpleNode) method.jjtGetChild(1)).getName();
			FunctionSymbolTable fst = this.symbolTable.getFunctions().get(methodName);
			
			manageMethodHeader(methodName, fst);
			manageMethodLimits(fst);
			manageMethodBody((SimpleNode) method.jjtGetChild(3), fst);
			manageMethodReturn((SimpleNode) method.jjtGetChild(4), fst);
		}

		this.printWriter.println(".end method");
	}

	/*
	 * Manages the code generation for the method header
	 *
	 * .method <access-spec> <method-spec>
	 */
	private void manageMethodHeader(String methodName, FunctionSymbolTable fst){

		String str = "\n.method public ";

		if(methodName.equals("main")){
			str += "static ";
		}

		str += methodName;
		str += getParametersInformation(fst);

		if(fst.getReturnSymbol() == null)
			str += "V";
		else
			str += fst.getReturnSymbol().getTypeDescriptor();

		this.printWriter.println(str);		// Contains .method <access-spec> <method-spec>
	}

	/*
	 * Manages the code generation for the method limits
	 *
	 * .limit locals
	 * .limit stack
	 */
	private void manageMethodLimits(FunctionSymbolTable fst){

		int size = fst.getParameters().size() + fst.getLocalVariables().size();
		this.printWriter.println("\n\t.limit locals " + 9);
		this.printWriter.println("\t.limit stack " + 9 + "\n");
	}

	/*
	 * Manages the code generation for the method body
	 *
	 * <statements>
	 */
	private void manageMethodBody(SimpleNode body, FunctionSymbolTable fst){

		for(int i = 0; i < body.jjtGetNumChildren(); i++){

			if(body.jjtGetChild(i) instanceof ASTASSIGN)
				manageASSIGN((SimpleNode) body.jjtGetChild(i), fst);

			else if(body.jjtGetChild(i) instanceof ASTCALL_FUNCTION){
				manageCALL_FUNCTION((SimpleNode) body.jjtGetChild(i), fst);
			}

			else if(body.jjtGetChild(i) instanceof ASTIF_ELSE_STATEMENT){
				manageIF_ELSE((SimpleNode) body.jjtGetChild(i), fst);
			}
		}
	}

	/*
	 * Manages the code generation for the method return
	 */
	private void manageMethodReturn(SimpleNode returnAux, FunctionSymbolTable fst){

		SimpleNode ret = (SimpleNode) returnAux.jjtGetChild(0);

		if(ret instanceof ASTIDENTIFIER){

			writeIDENTIFIER(ret, fst);

			String type;
			if(isGlobal(ret.getName()))
				type = getGlobalType(ret);
			else
				type = getLocalType(ret, fst);

			if(type.equals("int") || type.equals("boolean"))
				this.printWriter.println("\tireturn\n");
			else
				this.printWriter.println("\tareturn\n");
		}
		else if(ret instanceof ASTINT){
			
			int value = Integer.parseInt(ret.getValueInt());
			writeINT(value);
			this.printWriter.println("\tireturn\n");
		}
		else if(ret instanceof ASTTRUE || ret instanceof ASTFALSE){
			
			String value = ret.getValueBoolean();
			writeBOOLEAN(value);

			this.printWriter.println("\tireturn\n");
		}
		else if(ret instanceof ASTCALL_FUNCTION){

			manageCALL_FUNCTION(ret, fst);
			this.printWriter.println("\tireturn\n");
		}
		else if(ret instanceof ASTADD || ret instanceof ASTSUB || 
			ret instanceof ASTDIV || ret instanceof ASTMUL ||
			ret instanceof ASTAND || ret instanceof ASTLT ||
			ret instanceof ASTNOT){

			manageArithmeticExpression(ret, fst);
			this.printWriter.println("\tireturn\n");
		}
		else{	// No return

			this.printWriter.println("\treturn");
		}
	}

	/*
	 * Manages the code generation for ASSIGN nodes
	 */
	private void manageASSIGN(SimpleNode node, FunctionSymbolTable fst){

		if(!isGlobal(((SimpleNode) node.jjtGetChild(0)).getName()))
			manageParamLocalASSIGN(node, fst);
		else
			manageGlobalASSIGN(node, fst);
	}

	/*
	 * Manages the code generation for ASSIGN nodes for parameters and local variables
	 */
	private void manageParamLocalASSIGN(SimpleNode node, FunctionSymbolTable fst){

		SimpleNode lhs = ((SimpleNode) node.jjtGetChild(0));
		String lhsName = lhs.getName();
		SimpleNode rhs = ((SimpleNode) node.jjtGetChild(1));

		int index = getNodeIndex(lhsName, fst);

		if(rhs instanceof ASTINT){

			int value = Integer.parseInt(rhs.getValueInt());
			writeINT(value);
			this.printWriter.println("\tistore " + Integer.toString(index) + "\n");
		}
		else if(rhs instanceof ASTTRUE || rhs instanceof ASTFALSE){

			writeBOOLEAN(rhs.getValueBoolean());
			this.printWriter.println("\tistore " + Integer.toString(index) + "\n");
		}
		else if(rhs instanceof ASTIDENTIFIER){

			String type = getLocalType(lhs, fst);
			if(type.equals("int") || type.equals("boolean"))
				this.printWriter.println("\tistore " + Integer.toString(index) + "\n");
			else
				this.printWriter.println("\tastore " + Integer.toString(index) + "\n");
		}
		else if(rhs instanceof ASTCALL_FUNCTION){

			manageCALL_FUNCTION(rhs, fst);
			this.printWriter.println("\tistore " + Integer.toString(index) + "\n");
		}
		else if(rhs instanceof ASTADD || rhs instanceof ASTSUB || 
			rhs instanceof ASTDIV || rhs instanceof ASTMUL ||
			rhs instanceof ASTAND || rhs instanceof ASTLT ||
			rhs instanceof ASTNOT){

			manageArithmeticExpression(rhs, fst);
			this.printWriter.println("\tistore " + Integer.toString(index) + "\n");
		}
	}

	/*
	 * Manages the code generation for ASSIGN nodes for global variables
	 */
	private void manageGlobalASSIGN(SimpleNode node, FunctionSymbolTable fst){

		SimpleNode lhs = ((SimpleNode) node.jjtGetChild(0));
		SimpleNode rhs = ((SimpleNode) node.jjtGetChild(1));

		if(rhs instanceof ASTINT){

			this.printWriter.println("\tiload_0");

			int value = Integer.parseInt(rhs.getValueInt());
			writeINT(value);
			writePutfield(lhs);
		}
		else if(rhs instanceof ASTTRUE || rhs instanceof ASTFALSE){

			this.printWriter.println("\tiload_0");
			writeBOOLEAN(rhs.getValueBoolean());
			writePutfield(lhs);
		}
		else if(rhs instanceof ASTIDENTIFIER){

			String rhsName = rhs.getName(); 
			if(isGlobal(rhsName)){

				this.printWriter.println("\taload_0");
				this.printWriter.println("\taload_0");

				writeGetfield(rhs);
				writePutfield(lhs);
			}
			else{

				int index2 = getNodeIndex(rhsName, fst);

				this.printWriter.println("\taload_0");

				String type = getLocalType(rhs, fst);
				if(type.equals("int") || type.equals("boolean"))
					this.printWriter.println("\tiload " +  Integer.toString(index2));
				else
					this.printWriter.println("\taload " +  Integer.toString(index2));

				writePutfield(lhs);
			}
		}
		else if(rhs instanceof ASTCALL_FUNCTION){

			manageCALL_FUNCTION(rhs, fst);
			writePutfield(lhs);
		}
		else if(rhs instanceof ASTADD || rhs instanceof ASTSUB || 
			rhs instanceof ASTDIV || rhs instanceof ASTMUL ||
			rhs instanceof ASTAND || rhs instanceof ASTLT ||
			rhs instanceof ASTNOT){

			manageArithmeticExpression(rhs, fst);
			writePutfield(lhs);
		}
		this.printWriter.println();
	}

	/*
	 * Manages the code generation for CALL_FUNCTION nodes
	 */
	private void manageCALL_FUNCTION(SimpleNode node, FunctionSymbolTable fst){

		SimpleNode child = (SimpleNode) node.jjtGetChild(0);
		boolean flag = true;

		if(child instanceof ASTIDENTIFIER){

			flag = writeIDENTIFIER(child, fst);
		}
		else if(child instanceof ASTNEW_CLASS){

			manageNEW_CLASS(child, fst);
		}
		else if(child instanceof ASTCALL_FUNCTION){

			manageCALL_FUNCTION(child, fst);
		}

		manageCALL_ARGUMENTS((SimpleNode) node.jjtGetChild(2), fst);

		if(flag)
			manageFUNCTION((SimpleNode) node.jjtGetChild(1));
		else{

			this.printWriter.print("\tinvokestatic " + child.getName());
			this.printWriter.print("/" + ((SimpleNode) node.jjtGetChild(1)).getName());
			this.printWriter.println("(I)V");
		}
	}

	/*
	 *  Manages the code generation for IF_ELSE_STATEMENT nodes
	 */
	//CHECKAR 1
	private void manageIF_ELSE(SimpleNode node, FunctionSymbolTable fst){
		
		SimpleNode condition = (SimpleNode) node.jjtGetChild(0);
		SimpleNode if_body = (SimpleNode) node.jjtGetChild(1);
		SimpleNode else_body = (SimpleNode) node.jjtGetChild(2);

		if(condition.jjtGetChild(0) instanceof ASTLT){

			SimpleNode lt = (SimpleNode) condition.jjtGetChild(0);
			SimpleNode lhs = (SimpleNode) lt.jjtGetChild(0);
			SimpleNode rhs = (SimpleNode) lt.jjtGetChild(1);

			String label1 = "label_" + Integer.toString(labelCounter);
			labelCounter++;
			String label2 = "label_" + Integer.toString(labelCounter);
			labelCounter++;

			manageArithmeticExpression(lhs, fst);
			manageArithmeticExpression(rhs, fst);
			this.printWriter.println("\tif_icmplt " + label1);
			manageIfBody(if_body, fst);
			this.printWriter.println("\tgoto " + label2);
			this.printWriter.println("\t" + label1 + ":");
			manageIfBody(else_body, fst);
			this.printWriter.println("\t" + label2 + ":");
		}
	}

	private void manageIfBody(SimpleNode node, FunctionSymbolTable fst){

		if(node.jjtGetChild(0) instanceof ASTSTATEMENT_LIST)
			manageMethodBody((SimpleNode) node.jjtGetChild(0), fst);
		else
			manageMethodBody(node, fst);
	}


	/*
	 * Manages the code generation for CALL_ARGUMENTS nodes
	 */
	private void manageCALL_ARGUMENTS(SimpleNode node, FunctionSymbolTable fst){

		for(int i = 0; i < node.jjtGetNumChildren(); i++){

			SimpleNode child = (SimpleNode) node.jjtGetChild(i);

			if(child instanceof ASTINT){

				int value = Integer.parseInt(child.getValueInt());
				writeINT(value);
			}
			else if(child instanceof ASTTRUE || child instanceof ASTFALSE){

				String value = child.getValueBoolean();
				writeBOOLEAN(value);
			}
			else if(child instanceof ASTIDENTIFIER){

				writeIDENTIFIER(child, fst);
			}
			else if(child instanceof ASTCALL_FUNCTION){
				
				manageCALL_FUNCTION(child, fst);
			}
			else{

				manageArithmeticExpression(child, fst);
			}
		}
	}

	/*
	 * Manages the code generation for FUNCTION nodes
	 */
	private void manageFUNCTION(SimpleNode node){

		String invokeStr = "\tinvokevirtual ";
		invokeStr += this.symbolTable.getClassName() + "/" + node.getName();
		invokeStr += getParametersInformation(this.symbolTable.getFunctions().get(node.getName()));
		invokeStr += this.symbolTable.getFunctions().get(node.getName()).getReturnSymbol().getTypeDescriptor();
		this.printWriter.println(invokeStr);
	}

	/*
	 * Manages the code generation for Arithmetic Expressions
	 */
	private void manageArithmeticExpression(SimpleNode node, FunctionSymbolTable fst){

		if(node.jjtGetNumChildren() == 2){

			SimpleNode lhs = (SimpleNode) node.jjtGetChild(0);
			SimpleNode rhs = (SimpleNode) node.jjtGetChild(1);

			if(node instanceof ASTADD){

				manageArithmeticExpression(lhs, fst);
				manageArithmeticExpression(rhs, fst);
				this.printWriter.println("\tiadd");
			}
			else if(node instanceof ASTSUB){

				manageArithmeticExpression(lhs, fst);
				manageArithmeticExpression(rhs, fst);
				this.printWriter.println("\tisub");
			}
			else if(node instanceof ASTDIV){

				manageArithmeticExpression(lhs, fst);
				manageArithmeticExpression(rhs, fst);
				this.printWriter.println("\tidiv");
			}
			else if(node instanceof ASTMUL){

				manageArithmeticExpression(lhs, fst);
				manageArithmeticExpression(rhs, fst);
				this.printWriter.println("\timul");
			}
			else if(node instanceof ASTAND){

				String label1 = "label_" + Integer.toString(labelCounter);
				labelCounter++;
				String label2 = "label_" + Integer.toString(labelCounter);
				labelCounter++;

				manageArithmeticExpression(lhs, fst);
				this.printWriter.println("\tifeq " + label1);
				manageArithmeticExpression(rhs, fst);
				this.printWriter.println("\tifeq " + label1);
				this.printWriter.println("\ticonst_1");
				this.printWriter.println("\tgoto " + label2);
				this.printWriter.println("\t" + label1 + ":");
				this.printWriter.println("\ticonst_0");
				this.printWriter.println("\t" + label2 + ":");
			}
			else if(node instanceof ASTLT){

				String label1 = "label_" + Integer.toString(labelCounter);
				labelCounter++;
				String label2 = "label_" + Integer.toString(labelCounter);
				labelCounter++;

				manageArithmeticExpression(lhs, fst);
				manageArithmeticExpression(rhs, fst);
				this.printWriter.println("\tif_icmplt " + label1);
				this.printWriter.println("\ticonst_1");
				this.printWriter.println("\tgoto " + label2);
				this.printWriter.println("\t" + label1 + ":");
				this.printWriter.println("\ticonst_0");
				this.printWriter.println("\t" + label2 + ":");
			}
		}
		else if(node.jjtGetNumChildren() == 1){

			if(node instanceof ASTNEW_CLASS)
				manageNEW_CLASS(node, fst);

			else if(node instanceof ASTNOT){

				SimpleNode lhs = (SimpleNode) node.jjtGetChild(0);

				String label1 = "label_" + Integer.toString(labelCounter);
				labelCounter++;
				String label2 = "label_" + Integer.toString(labelCounter);
				labelCounter++;

				manageArithmeticExpression(lhs, fst);
				this.printWriter.println("\tifne " + label1);
				this.printWriter.println("\ticonst_1");
				this.printWriter.println("\tgoto " + label2);
				this.printWriter.println("\t" + label1 + ":");
				this.printWriter.println("\ticonst_0");
				this.printWriter.println("\t" + label2 + ":");
			}
		}
		else {

			if(node instanceof ASTIDENTIFIER){

				writeIDENTIFIER(node, fst);
			}
			else if(node instanceof ASTTRUE || node instanceof ASTFALSE){

				String value = node.getValueBoolean();
				writeBOOLEAN(value);
			}
			else if(node instanceof ASTINT){

				int value = Integer.parseInt(node.getValueInt());
				writeINT(value);
			}
			else if(node instanceof ASTCALL_FUNCTION){

				manageCALL_FUNCTION(node, fst);
			}
		}
	}


	/*
	 * Manages the code generation for the NEW_CLASS's
	 */ 
	private void manageNEW_CLASS(SimpleNode node, FunctionSymbolTable fst){

		this.printWriter.println("\tnew " + this.symbolTable.getClassName());
		this.printWriter.println("\tdup");
		this.printWriter.println("\tinvokenonvirtual " + this.symbolTable.getClassName() + "/<init>()V");
	}

	/*
	 * Manages the code generation for the INT's
	 */ 
	private void writeINT(int value){

		if(value >= 0 && value <= 5)
			this.printWriter.println("\ticonst_" + Integer.toString(value));
		else if(value == -1)
			this.printWriter.println("\ticonst_m1");
		else if(value >= -128 && value <= 127)
			this.printWriter.println("\tbipush " + Integer.toString(value));
		else if(value >= -32768 && value <= 32767)
			this.printWriter.println("\tsipush " + Integer.toString(value));
		else
			this.printWriter.println("\tldc " + Integer.toString(value));
	}
	/*
	 * Manages the code generation for the BOOLEAN's
	 */
	private void writeBOOLEAN(String value){

		if(value.equals("true"))
			this.printWriter.println("\ticonst_1");
		else
			this.printWriter.println("\ticonst_0");
	}
	/*
	 * Manages the code generation for the IDENTIFIER's
	 */
	private boolean writeIDENTIFIER(SimpleNode node, FunctionSymbolTable fst){

		String nodeName = node.getName();
		if(isGlobal(nodeName)){

			this.printWriter.println("\taload_0");
			writeGetfield(node);
			return true;
		}
		else if(isLocal(nodeName, fst)){

			int index = getNodeIndex(nodeName, fst);
			String type = getLocalType(node, fst);
			if(type.equals("int") || type.equals("boolean")){
				this.printWriter.println("\tiload " + Integer.toString(index));
			}
			else{
				this.printWriter.println("\taload " + Integer.toString(index));
			}
			return true;
		}
		else{

			return false;
		}
	}

	/*
	 * Manages the code generation for "getfield"
	 */
	private void writeGetfield(SimpleNode var){

		// FALTA: NOT SURE ABOUT THE CLASS NAME
		String getfieldStr = "\tgetfield ";
		getfieldStr += this.symbolTable.getClassName() + "/" + var.getName() + " ";
		getfieldStr += this.symbolTable.getGlobal_variables().get(var.getName()).getTypeDescriptor();
		this.printWriter.println(getfieldStr);
	}

	/*
	 * Manages the code generation for "putfield"
	 */
	private void writePutfield(SimpleNode var){

		// FALTA: NOT SURE ABOUT THE CLASS NAME
		String putfieldStr = "\tputfield ";
		putfieldStr += this.symbolTable.getClassName() + "/" + var.getName() + " ";
		putfieldStr += this.symbolTable.getGlobal_variables().get(var.getName()).getTypeDescriptor();
		this.printWriter.println(putfieldStr);
	}

	/*
	 * Checks whether a variable is global or not
	 * Returns true if it is global, false if not
	 */
	private boolean isGlobal(String name){

		if(this.symbolTable.getGlobal_variables().get(name) != null)
			return true;
		return false;
	}

	/*
	 * Checks whether a variable is local or not
	 * Returns true if it is local, false if not
	 */
	private boolean isLocal(String name, FunctionSymbolTable fst){

		if(fst.getParameters().get(name) != null || fst.getLocalVariables().get(name) != null)
			return true;
		return false;
	}

	/*
	 * Returns the index of a parameter or local variable inside a method
	 */
	private int getNodeIndex(String name, FunctionSymbolTable fst){

		Map<String, Symbol> map;
		int index;

		if(fst.getParameters().get(name) != null){
			map = fst.getParameters();
			index = 1;
		}
		else{
			map = fst.getLocalVariables();
			index = 1 + fst.getParameters().size();
		}

		for (Map.Entry<String, Symbol> entry : map.entrySet()) {
		    if(entry.getKey().equals(name))
		    	break;
		    index++;
		}
		return index;
	}

	/*
	 * Creates a string with the code needed for the parameters
	 */
	private String getParametersInformation(FunctionSymbolTable value){

		String str = "(";

		Map<String, Symbol> map2 = value.getParameters();
		for (Map.Entry<String, Symbol> entry : map2.entrySet()) {

			String type = entry.getValue().getTypeDescriptor();
			str += type;
			if(!(type.equals("I") || type.equals("Z") || type.equals("[I")))
				str += ";";
		}

		str += ")";
		return str;
	}

	/*
	 * Returns a string with the type of the global nodes
	 */
	private String getGlobalType(SimpleNode node){

		return this.symbolTable.getGlobal_variables().get(node.getName()).getTypeString();
	}
	/*
	 * Returns a string with the type of the parameter ou local nodes
	 */
	private String getLocalType(SimpleNode node, FunctionSymbolTable fst){
		
		if(fst.getParameters().get(node.getName()) != null)
			return fst.getParameters().get(node.getName()).getTypeString();
		else
			return fst.getLocalVariables().get(node.getName()).getTypeString();
	}

	/*
	 * Generates a random number between 0 and 100
	 */
	private int getRandomNumber(){

		Random rand = new Random();
		return rand.nextInt(101);
	}
}