
package org.rishav.eclipse.plugin.killbugs;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression.Operator;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.ui.handlers.HandlerUtil;

public class StringComparisonFixtHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		ICompilationUnit icu = null;

		ISelection sel = HandlerUtil.getCurrentSelection(event);

		if (sel instanceof TreeSelection) {
			IStructuredSelection selection = (IStructuredSelection) sel;

			Object firstElement = selection.getFirstElement();
			if (firstElement instanceof ICompilationUnit) {
				icu = (ICompilationUnit) firstElement;
			}
		}
		else if (sel instanceof TextSelection) {
			ITypeRoot typeRoot = JavaUI.getEditorInputTypeRoot(HandlerUtil
					.getActiveEditor(event).getEditorInput());
			icu = (ICompilationUnit) typeRoot.getAdapter(ICompilationUnit.class);
		}

		try {
			replaceEqualOperatorOnStringWithEqualsMethod(icu);
		} catch (JavaModelException e) {
			System.err.println(e);
			e.printStackTrace();
		} catch (MalformedTreeException e) {
			System.err.println(e);
			e.printStackTrace();
		} catch (BadLocationException e) {
			System.err.println(e);
			e.printStackTrace();
		}
		return null;
	}

	private void replaceEqualOperatorOnStringWithEqualsMethod(
			final ICompilationUnit icu) throws JavaModelException,
			MalformedTreeException, BadLocationException {

		ASTParser parser = ASTParser.newParser(AST.JLS3);
		parser.setSource(icu);
		parser.setResolveBindings(true);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);

		CompilationUnit astRoot = (CompilationUnit) parser.createAST(null);

		final Document doc = new Document(icu.getSource());

		final AST ast = astRoot.getAST();

		final ASTRewrite rew = ASTRewrite.create(ast);

		astRoot.accept(new ASTVisitor() {
			/* checks each == or != comparison if operands are string */
			@Override
			public boolean visit(InfixExpression node) {
				if (node.getOperator().equals(InfixExpression.Operator.EQUALS)
						|| node.getOperator().equals(
								InfixExpression.Operator.NOT_EQUALS)) {
					if ( !(node.getLeftOperand() instanceof NullLiteral || node
							.getRightOperand() instanceof NullLiteral)
							&& node.getLeftOperand().resolveTypeBinding()
									.getQualifiedName().equals("java.lang.String")) {

						/*
						 * callEquals is created to write a new equals method
						 * invocation
						 */
						MethodInvocation callEquals = ast.newMethodInvocation();
						callEquals.setName(ast.newSimpleName("equals"));

						/*
						 * expression which will invoke the equals method is
						 * created from left hand expression of == comparison
						 */
						Expression leftOperand = node.getLeftOperand();
						Expression newExpression = (Expression) ASTNode.copySubtree(
								ast, leftOperand);
						callEquals.setExpression(newExpression);

						/*
						 * argument of equals method invocation is created from
						 * right hand expression of == comparison
						 */
						Expression rightOperand = node.getRightOperand();
						Expression argument = (Expression) ASTNode.copySubtree(ast,
								rightOperand);
						callEquals.arguments().add(argument);

						// callEquals.setExpression(ast.newSimpleName(node
						// .getLeftOperand().toString()));

						// StringLiteral sl1 = ast.newStringLiteral(); String
						// propname = node.getLeftOperand()
						// .resolveConstantExpressionValue().toString();
						// sl1.setLiteralValue(propname);

						// TextElement newTextElement = ast.newTextElement();
						// newTextElement
						// .setText(oldMethodInvocation.toString());
						// rew.replace(node, newTextElement, null);
						
						if (node.getOperator().equals(
								InfixExpression.Operator.NOT_EQUALS)) {
							PrefixExpression newPrefixExpression = ast
									.newPrefixExpression();
							newPrefixExpression.setOperator(Operator.NOT);
							newPrefixExpression.setOperand(callEquals);

							rew.replace(node, newPrefixExpression, null);
						}
						else
							rew.replace(node, callEquals, null);
					}
				}
				return true;
			}
		});

		TextEdit edits = rew.rewriteAST(doc, null);

		edits.apply(doc);
		String newSource = doc.get();

		// updation of the compilation unit
		icu.getBuffer().setContents(newSource);
	}
}