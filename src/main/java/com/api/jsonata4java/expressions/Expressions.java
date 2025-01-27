/**
 * (c) Copyright 2018, 2019 IBM Corporation
 * 1 New Orchard Road, 
 * Armonk, New York, 10504-1722
 * United States
 * +1 914 499 1900
 * support: Nathaniel Mills wnm3@us.ibm.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.api.jsonata4java.expressions;

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.tree.ErrorNodeImpl;
import org.antlr.v4.runtime.tree.ParseTree;

import com.api.jsonata4java.expressions.generated.MappingExpressionLexer;
import com.api.jsonata4java.expressions.generated.MappingExpressionParser;
import com.api.jsonata4java.expressions.utils.Constants;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

@SuppressWarnings("deprecation")
public class Expressions {
	ParseTree tree = null;
	String expression = null;
	ExpressionsVisitor _eval = new ExpressionsVisitor(JsonNodeFactory.instance.objectNode());

	/**
	 * Returns a list of $something references in the given expression, using the
	 * given Pattern object (typically patterns should match on $state or $event)
	 *
	 * @param refPattern reference pattern
	 * @param expression expression to be searched for references
	 * @return list of references
	 */
	public static List<String> getRefsInExpression(Pattern refPattern, String expression) {
		// eg if expression = "$state.x.y + $event.a + ($state.c/2)
		// then return values should be ["x.y", "c"]
		Matcher matcher = refPattern.matcher(expression);

		LinkedList<String> matches = new LinkedList<>();

		while (matcher.find()) {
			matches.add(matcher.group(1));
		}

		return matches;
	}

	public Expressions(ParseTree aTree, String anExpression) {
		tree = aTree;
		expression = anExpression;
	}

	// Convert a mapping expression string into a pre-processed expression ready
	// for evaluation
	public static Expressions parse(String mappingExpression) throws ParseException {

		// Expressions can include references to properties within an
		// application interface ("state"),
		// properties within an event, and various operators and functions.

		ANTLRInputStream input = new ANTLRInputStream(mappingExpression);

		MappingExpressionLexer lexer = new MappingExpressionLexer(input);
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		MappingExpressionParser parser = new MappingExpressionParser(tokens);
		ParseTree tree = null;

		BufferingErrorListener errorListener = new BufferingErrorListener();

		try {
			// remove the default error listeners which print to stderr
			parser.removeErrorListeners();
			lexer.removeErrorListeners();

			// replace with error listener that buffer errors and allow us to retrieve them
			// later
			parser.addErrorListener(errorListener);
			lexer.addErrorListener(errorListener);

			tree = parser.expr();
			if (errorListener.heardErrors()) {
				if (tree != null && tree.getChildCount() > 0) {
					ParseTree error = tree.getChild(0);
					if (error instanceof ErrorNodeImpl) {
						if (((ErrorNodeImpl) error).getSymbol().getType() == MappingExpressionLexer.CHAIN) {
							throw new EvaluateRuntimeException(Constants.ERR_MSG_FCT_CHAIN_NOT_UNARY);

						}
					}
				}
				throw new ParseException(errorListener.getErrorsAsString());
			}
		} catch (RecognitionException e) {
			throw new ParseException(e.getMessage());
		}

		return new Expressions(tree, mappingExpression);
	}

	/**
	 * Evaluate the stored expression against the supplied event and application
	 * interface data.
	 * 
	 * @param rootContext bound to root context ($$ and paths that don't start with
	 *                    $event, $state or $instance) when evaluating expressions.
	 *                    May be null.
	 * @return the JsonNode resulting from the expression evaluation against the rootContext
	 * @throws EvaluateException If the given device event is invalid.
	 */
	public JsonNode evaluate(JsonNode rootContext) throws EvaluateException {

		JsonNode result = null;

		ExpressionsVisitor eval = new ExpressionsVisitor(rootContext);
		_eval = eval;

		try {
			result = _eval.visit(tree); // was eval.visit();
		} catch (EvaluateRuntimeException e) {
			throw new EvaluateException(e.getMessage(), e);
		}

		// prevent a NPE when expression evaluates to null (which is a legitimate return
		// value for an expression)
		if (result == null) {
			return null;
		}

		return result;
	}

	public ExpressionsVisitor getExpr() {
	   return _eval;
	}
	
	public void setExpr(ExpressionsVisitor expr) {
	   _eval = expr;
	}
	
	public ParseTree getTree() {
	   return tree;
	}
	
	public void setTree(ParseTree parsetree) {
	   tree = parsetree;
	}
	
	public String toString() {
		return expression;
	}

}
