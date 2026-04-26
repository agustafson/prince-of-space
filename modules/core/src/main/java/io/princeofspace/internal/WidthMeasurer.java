package io.princeofspace.internal;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.ArrayAccessExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.InstanceOfExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.TextBlockLiteralExpr;
import io.princeofspace.model.FormatterConfig;

/**
 * Computes single-line widths for expressions without invoking the pretty-printer.
 */
final class WidthMeasurer {
    private static final int SINGLE_PARAMETER_COUNT = 1;
    private static final int TERNARY_SEPARATOR_WIDTH = 3; // " ? " / " : "

    private WidthMeasurer() {}

    static int flatWidth(Node node, FormatterConfig fmt) {
        if (node instanceof Expression expression) {
            return expressionWidth(expression, fmt);
        }
        return node.toString().length();
    }

    private static int expressionWidth(Expression expression, FormatterConfig fmt) {
        if (expression instanceof LambdaExpr lambda) {
            return lambdaHeaderWidth(lambda, fmt);
        }
        if (expression instanceof MethodCallExpr call) {
            return methodCallWidth(call, fmt);
        }
        if (expression instanceof BinaryExpr binaryExpr) {
            return expressionWidth(binaryExpr.getLeft(), fmt)
                    + 1
                    + binaryExpr.getOperator().asString().length()
                    + 1
                    + expressionWidth(binaryExpr.getRight(), fmt);
        }
        if (expression instanceof ConditionalExpr conditionalExpr) {
            return expressionWidth(conditionalExpr.getCondition(), fmt)
                    + TERNARY_SEPARATOR_WIDTH
                    + expressionWidth(conditionalExpr.getThenExpr(), fmt)
                    + TERNARY_SEPARATOR_WIDTH
                    + expressionWidth(conditionalExpr.getElseExpr(), fmt);
        }
        if (expression instanceof StringLiteralExpr stringLiteralExpr) {
            return stringLiteralExpr.toString().length();
        }
        if (expression instanceof TextBlockLiteralExpr textBlockLiteralExpr) {
            return textBlockLiteralExpr.toString().length();
        }
        if (expression instanceof EnclosedExpr enclosedExpr) {
            return 2 + expressionWidth(enclosedExpr.getInner(), fmt);
        }
        if (expression instanceof FieldAccessExpr fieldAccessExpr) {
            return expressionWidth(fieldAccessExpr.getScope(), fmt)
                    + 1
                    + fieldAccessExpr.getNameAsString().length();
        }
        if (expression instanceof ArrayAccessExpr arrayAccessExpr) {
            return expressionWidth(arrayAccessExpr.getName(), fmt)
                    + 1
                    + expressionWidth(arrayAccessExpr.getIndex(), fmt)
                    + 1;
        }
        if (expression instanceof CastExpr castExpr) {
            return 2 + castExpr.getType().toString().length() + 2 + expressionWidth(castExpr.getExpression(), fmt);
        }
        if (expression instanceof InstanceOfExpr instanceOfExpr) {
            return expressionWidth(instanceOfExpr.getExpression(), fmt)
                    + " instanceof ".length()
                    + instanceOfExpr.getType().toString().length();
        }
        if (expression instanceof ObjectCreationExpr objectCreationExpr) {
            int argsWidth = commaSeparatedWidth(objectCreationExpr.getArguments(), fmt);
            int width = "new ".length() + objectCreationExpr.getType().toString().length() + 2 + argsWidth;
            if (objectCreationExpr.getAnonymousClassBody().isPresent()) {
                width += " { }".length();
            }
            return width;
        }
        if (expression instanceof MethodReferenceExpr methodReferenceExpr) {
            return expressionWidth(methodReferenceExpr.getScope(), fmt)
                    + 2
                    + methodReferenceExpr.getIdentifier().length();
        }
        return expression.toString().length();
    }

    private static int methodCallWidth(MethodCallExpr methodCallExpr, FormatterConfig fmt) {
        int width = methodCallExpr.getScope().map(scope -> expressionWidth(scope, fmt) + 1).orElse(0);
        width += methodCallExpr.getNameAsString().length();
        width += methodCallExpr.getTypeArguments()
                .map(typeArgs -> 2 + commaSeparatedTypeWidth(typeArgs))
                .orElse(0);
        width += 2 + commaSeparatedWidth(methodCallExpr.getArguments(), fmt);
        return width;
    }

    private static int commaSeparatedWidth(NodeList<? extends Expression> expressions, FormatterConfig fmt) {
        int width = 0;
        boolean first = true;
        for (Expression expression : expressions) {
            if (!first) {
                width += 2;
            }
            first = false;
            width += expressionWidth(expression, fmt);
        }
        return width;
    }

    private static int commaSeparatedTypeWidth(NodeList<com.github.javaparser.ast.type.Type> types) {
        int width = 0;
        boolean first = true;
        for (com.github.javaparser.ast.type.Type type : types) {
            if (!first) {
                width += 2;
            }
            first = false;
            width += type.toString().length();
        }
        return width;
    }

    private static int lambdaHeaderWidth(LambdaExpr lambdaExpr, FormatterConfig fmt) {
        int parametersWidth;
        if (lambdaExpr.isEnclosingParameters()) {
            parametersWidth = 2 + commaSeparatedParameterWidth(lambdaExpr.getParameters());
        } else if (lambdaExpr.getParameters().size() == SINGLE_PARAMETER_COUNT) {
            parametersWidth = lambdaExpr.getParameter(0).toString().length();
        } else {
            parametersWidth = 2 + commaSeparatedParameterWidth(lambdaExpr.getParameters());
        }

        if (lambdaExpr.getBody().isBlockStmt()) {
            return parametersWidth + " -> { }".length();
        }
        return parametersWidth + " -> ".length() + expressionWidth(lambdaExpr.getBody().asExpressionStmt().getExpression(), fmt);
    }

    private static int commaSeparatedParameterWidth(NodeList<com.github.javaparser.ast.body.Parameter> parameters) {
        int width = 0;
        boolean first = true;
        for (com.github.javaparser.ast.body.Parameter parameter : parameters) {
            if (!first) {
                width += 2;
            }
            first = false;
            width += parameter.toString().length();
        }
        return width;
    }
}
