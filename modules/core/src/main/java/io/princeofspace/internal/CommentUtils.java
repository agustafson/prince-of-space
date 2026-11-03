package io.princeofspace.internal;

import com.github.javaparser.JavaToken;
import com.github.javaparser.Position;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.DataKey;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.comments.BlockComment;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.comments.LineComment;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SwitchExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.nodeTypes.NodeWithArguments;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Predicate;

/** Static comment-oriented AST helpers used by visitor delegates. */
final class CommentUtils {

    private static final int SINGLE_ARGUMENT_COUNT = 1;
    private static final char BACKSLASH = '\\';
    private static final HexFormat COMMENT_HEX = HexFormat.of().withUpperCase();
    private static final DataKey<NavigableSet<Position>> SOURCE_COMMENT_STARTS = new DataKey<>() {};
    private static final DataKey<List<Comment>> SEMICOLON_SUFFIX = new DataKey<>() {};
    private static final DataKey<List<Comment>> STATEMENT_ENDING = new DataKey<>() {};
    private static final DataKey<List<Comment>> CHAIN_DOT_PREFIX = new DataKey<>() {};
    private static final DataKey<List<Comment>> CHAIN_DOT_SUFFIX = new DataKey<>() {};
    private static final DataKey<List<Comment>> SELECTOR_PREFIX = new DataKey<>() {};
    private static final DataKey<List<Comment>> SELECTOR_SUFFIX = new DataKey<>() {};
    private static final DataKey<List<Comment>> OPERATOR_SUFFIX = new DataKey<>() {};
    private static final DataKey<ConditionalComments> CONDITIONAL_COMMENTS = new DataKey<>() {};

    static final class ConditionalComments {
        final List<Comment> beforeQuestion;
        final List<Comment> afterQuestion;
        final List<Comment> beforeColon;
        final List<Comment> afterColon;

        ConditionalComments(List<Comment> beforeQuestion, List<Comment> afterQuestion,
                List<Comment> beforeColon, List<Comment> afterColon) {
            this.beforeQuestion = beforeQuestion;
            this.afterQuestion = afterQuestion;
            this.beforeColon = beforeColon;
            this.afterColon = afterColon;
        }

        boolean isEmpty() {
            return beforeQuestion.isEmpty() && afterQuestion.isEmpty()
                    && beforeColon.isEmpty() && afterColon.isEmpty();
        }
    }
    private static final String IDENTITY_COMPARISON = "ReferenceEquality";
    private static final String PMD_IDENTITY_COMPARISON = "PMD.CompareObjectsWithEquals";

    private CommentUtils() {}

    /** Indexes parsed source positions once; normalisation never creates new comment positions. */
    static void indexSourceCommentStarts(CompilationUnit unit) {
        unit.removeData(SOURCE_COMMENT_STARTS);
        if (unit.getRange().isEmpty() || unit.getTokenRange().isEmpty()) {
            return;
        }
        List<Comment> comments = new ArrayList<>(unit.getAllContainedComments());
        unit.getComment().ifPresent(comments::add);
        NavigableSet<Position> starts = new TreeSet<>();
        for (Comment comment : comments) {
            if (comment.getBegin().isEmpty()) {
                return;
            }
            starts.add(comment.getBegin().orElseThrow());
        }
        unit.setData(SOURCE_COMMENT_STARTS, Collections.unmodifiableNavigableSet(starts));
    }

    /** Retains source comments that JavaParser did not attach after comparing equal-content orphans. */
    static void restoreUnattachedComments(CompilationUnit unit, Collection<Comment> sourceComments) {
        var attached = Collections.newSetFromMap(new IdentityHashMap<Comment, Boolean>());
        attached.addAll(unit.getAllContainedComments());
        unit.getComment().ifPresent(attached::add);
        for (Comment comment : sourceComments) {
            if (attached.contains(comment) || comment.getRange().isEmpty()) {
                continue;
            }
            Node owner = unit;
            for (Node candidate : unit.findAll(Node.class)) {
                if (!(candidate instanceof Comment) && candidate.getRange().isPresent()
                        && candidate.getRange().orElseThrow().contains(comment.getRange().orElseThrow())
                        && owner.getRange().map(range -> range.contains(candidate.getRange().orElseThrow())).orElse(true)) {
                    owner = candidate;
                }
            }
            owner.addOrphanComment(comment);
            attached.add(comment);
        }
    }

    /** Encodes decoded comment content without making literal backslashes eligible Unicode escapes. */
    static String escapeCommentContent(String content) {
        StringBuilder escaped = new StringBuilder(content.length());
        for (int i = 0; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (ch == BACKSLASH) {
                int end = i + 1;
                while (end < content.length() && content.charAt(end) == BACKSLASH) {
                    end++;
                }
                boolean protect = end < content.length()
                        && ((end - i) % 2 != 0)
                        && (content.charAt(end) == 'u' || needsCommentEscape(content, end));
                escaped.append(content, i, end - 1);
                // Escaping the last backslash protects both literal text and newly inserted escapes.
                escaped.append(protect ? "\\u005C" : "\\");
                i = end - 1;
            } else if (needsCommentEscape(content, i)) {
                escaped.append("\\u").append(COMMENT_HEX.toHexDigits(ch));
            } else {
                escaped.append(ch);
            }
        }
        return escaped.toString();
    }

    private static boolean needsCommentEscape(String content, int index) {
        char ch = content.charAt(index);
        if (ch == 0) {
            return true;
        }
        if (Character.isHighSurrogate(ch)) {
            return index + 1 == content.length() || !Character.isLowSurrogate(content.charAt(index + 1));
        }
        return Character.isLowSurrogate(ch)
                && (index == 0 || !Character.isHighSurrogate(content.charAt(index - 1)));
    }

    /** Removes every owner of the exact comment instance within a subtree. */
    @SuppressWarnings({IDENTITY_COMPARISON, PMD_IDENTITY_COMPARISON})
    static void removeMirroredCommentOwners(Node root, Comment comment) {
        for (Node node : root.findAll(Node.class)) {
            // JavaParser can mirror this exact Comment instance onto the call scope, name, and arguments.
            if (node.getComment().orElse(null) == comment) {
                node.removeComment();
            }
            removeOrphanCommentByIdentity(node, comment);
        }
    }

    /** JavaParser removes orphans by structural equality, so rebuild survivors by identity. */
    @SuppressWarnings({IDENTITY_COMPARISON, PMD_IDENTITY_COMPARISON})
    static void removeOrphanCommentByIdentity(Node owner, Comment comment) {
        if (owner.getOrphanComments().isEmpty()) {
            return;
        }
        List<Comment> survivors = new ArrayList<>(owner.getOrphanComments());
        if (!survivors.removeIf(orphan -> orphan == comment)) {
            return;
        }
        for (Comment orphan : new ArrayList<>(owner.getOrphanComments())) {
            owner.removeOrphanComment(orphan);
        }
        for (Comment survivor : survivors) {
            owner.addOrphanComment(survivor);
        }
    }

    /** Detaches comments after a semicolon before descendants can print them early. */
    static List<Comment> takeTrailingComments(Node node) {
        if (node.containsData(SEMICOLON_SUFFIX)) {
            return takeCapturedSemicolonSuffix(node);
        }
        if (node.getEnd().isEmpty()) {
            return List.of();
        }
        Position end = node.getEnd().orElseThrow();
        return takeCommentsBetween(node, end, new Position(end.line + 1, 1));
    }

    static List<Comment> takeCapturedSemicolonSuffix(Node node) {
        return takeCapturedComments(node, SEMICOLON_SUFFIX);
    }

    static List<Comment> takeStatementEndingComments(Node node) {
        return takeCapturedComments(node, STATEMENT_ENDING);
    }

    private static List<Comment> takeCapturedComments(Node node, DataKey<List<Comment>> key) {
        if (!node.containsData(key)) {
            return List.of();
        }
        List<Comment> comments = node.getData(key);
        node.removeData(key);
        return comments;
    }

    /** Captures comments beside a statement semicolon before mirrored descendants can print them. */
    static void collectStatementSemicolonComments(Node root) {
        Map<Position, Comment> byPosition = new HashMap<>();
        for (Comment comment : root.getAllContainedComments()) {
            comment.getBegin().ifPresent(position -> byPosition.put(position, comment));
        }
        for (Node node : root.findAll(Node.class, candidate ->
                candidate instanceof FieldDeclaration || candidate instanceof ExpressionStmt
                        || candidate instanceof ReturnStmt || candidate instanceof ThrowStmt
                        || candidate instanceof ExplicitConstructorInvocationStmt || candidate instanceof EnumConstantDeclaration)) {
            if (node.getTokenRange().isEmpty()) {
                continue;
            }
            JavaToken semicolon = node.getTokenRange().orElseThrow().getEnd();
            if (node instanceof EnumConstantDeclaration) {
                var next = semicolon.getNextToken();
                while (next.isPresent() && next.orElseThrow().getCategory().isWhitespaceOrComment()) {
                    next = next.orElseThrow().getNextToken();
                }
                if (next.isEmpty()) {
                    continue;
                }
                semicolon = next.orElseThrow();
            }
            if (!semicolon.getText().equals(";") || semicolon.getRange().isEmpty()) {
                continue;
            }
            collectStatementEndingComments(root, node, semicolon);
            int line = semicolon.getRange().orElseThrow().end.line;
            List<Comment> suffix = new ArrayList<>();
            var token = semicolon.getNextToken();
            while (token.isPresent() && token.orElseThrow().getRange().isPresent()
                    && token.orElseThrow().getRange().orElseThrow().end.line == line) {
                JavaToken next = token.orElseThrow();
                if (!next.getCategory().isWhitespaceOrComment()) {
                    break;
                }
                Comment comment = byPosition.get(next.getRange().orElseThrow().begin);
                if (comment != null && isLineOrBlock(comment)) {
                    suffix.add(comment);
                }
                token = next.getNextToken();
            }
            if (!suffix.isEmpty()) {
                for (Comment comment : suffix) {
                    removeMirroredCommentOwners(root, comment);
                }
                node.setData(SEMICOLON_SUFFIX, suffix);
            }
        }
    }

    private static void collectStatementEndingComments(Node root, Node node, JavaToken semicolon) {
        if (!(node instanceof FieldDeclaration || node instanceof ExpressionStmt
                || node instanceof ReturnStmt || node instanceof ThrowStmt)) {
            return;
        }
        previousCodeToken(semicolon).ifPresent(left -> {
            List<Comment> comments = takeTokenGap(root, left, semicolon,
                    token -> token.getText().startsWith("//"));
            if (comments.isEmpty()) {
                return;
            }
            // The inherited field printer appends ';' after the final declarator.
            Node owner = node instanceof FieldDeclaration field
                    ? field.getVariable(field.getVariables().size() - 1) : node;
            owner.setData(STATEMENT_ENDING, comments);
            Position end = left.getRange().orElseThrow().end;
            Node terminal = node;
            for (Node candidate : node.findAll(Node.class)) {
                if (!(candidate instanceof Comment) && candidate.getEnd().filter(end::equals).isPresent()) {
                    terminal = candidate;
                }
            }
            InlineBlockComments.addRetainedWidths(terminal, end, comments);
        });
    }

    /** Detaches comments at an argument or initializer boundary before nested expressions can hoist them. */
    @SuppressWarnings({IDENTITY_COMPARISON, PMD_IDENTITY_COMPARISON})
    static List<Comment> takeExpressionBoundaryComments(Node argument) {
        Optional<JavaToken> initializerPrefix = initializerPrefixToken(argument);
        if (initializerPrefix.isPresent()) {
            return takeCommentsBetween(argument.getParentNode().orElseThrow(), initializerPrefix.orElseThrow().getRange().orElseThrow().end,
                    argument.getBegin().orElseThrow());
        }
        Node owner = argument.getParentNode().orElse(null);
        if (!(owner instanceof NodeWithArguments<?> call) || argument.getBegin().isEmpty()) {
            return List.of();
        }
        if (owner.getOrphanComments().isEmpty() && !hasAnyLineOrBlockComment(argument)) {
            return List.of();
        }
        Position start = argumentOpening(owner, call).orElse(null);
        for (Expression candidate : call.getArguments()) {
            if (candidate == argument) {
                return start == null ? List.of()
                        : takeCommentsBetween(owner, start, argument.getBegin().orElseThrow());
            }
            start = candidate.getEnd().orElse(null);
        }
        return List.of();
    }

    /** Removes initializer-prefix mirrors before a declaration type or name can print them. */
    static void normaliseInitializerPrefixCommentOwners(Node root) {
        for (Expression expression : root.findAll(Expression.class)) {
            if (!hasInitializerPrefixComments(expression)) {
                continue;
            }
            Position start = initializerPrefixToken(expression).orElseThrow().getRange().orElseThrow().end;
            Node owner = expression.getParentNode().orElseThrow();
            for (Comment comment : takeCommentsBetween(root, start, expression.getBegin().orElseThrow())) {
                owner.addOrphanComment(comment);
            }
        }
    }

    private static boolean hasInitializerPrefixComments(Node expression) {
        Optional<JavaToken> prefix = initializerPrefixToken(expression);
        if (prefix.isEmpty()) {
            return false;
        }
        var token = prefix.orElseThrow().getNextToken();
        while (token.isPresent() && token.orElseThrow().getCategory().isWhitespaceOrComment()) {
            if (token.orElseThrow().getCategory().isComment()) {
                return true;
            }
            token = token.orElseThrow().getNextToken();
        }
        return false;
    }

    static boolean initializerPrefixBreaksLine(Node expression) {
        return hasInitializerPrefixComments(expression)
                && initializerPrefixToken(expression).orElseThrow().getRange().orElseThrow().end.line
                        < expression.getBegin().orElseThrow().line;
    }

    @SuppressWarnings({IDENTITY_COMPARISON, PMD_IDENTITY_COMPARISON})
    private static Optional<JavaToken> initializerPrefixToken(Node expression) {
        Node parent = expression.getParentNode().orElse(null);
        String operator;
        if (parent instanceof VariableDeclarator variable && variable.getInitializer().orElse(null) == expression) {
            operator = "=";
        } else if (parent instanceof AssignExpr assignment && assignment.getValue() == expression) {
            operator = assignment.getOperator().asString();
        } else {
            return Optional.empty();
        }
        var token = expression.getTokenRange().flatMap(range -> range.getBegin().getPreviousToken());
        while (token.isPresent() && token.orElseThrow().getCategory().isWhitespaceOrComment()) {
            token = token.orElseThrow().getPreviousToken();
        }
        return token.filter(candidate -> candidate.getText().equals(operator) && candidate.getRange().isPresent());
    }

    private static List<Comment> takeCommentsBetween(Node root, Position start, Position end) {
        if (!sourceGapMayContainComments(root, start, end)) {
            return List.of();
        }
        var seen = Collections.newSetFromMap(new IdentityHashMap<Comment, Boolean>());
        List<Comment> comments = new ArrayList<>(root.getAllContainedComments());
        root.getComment().ifPresent(comments::add);
        comments.removeIf(comment -> !seen.add(comment) || !isLineOrBlock(comment)
                || comment.getBegin().isEmpty() || comment.getEnd().isEmpty()
                || !comment.getBegin().orElseThrow().isAfter(start)
                || !comment.getEnd().orElseThrow().isBefore(end));
        comments.sort(Comparator.comparing(comment -> comment.getBegin().orElseThrow()));
        for (Comment comment : comments) {
            removeMirroredCommentOwners(root, comment);
        }
        return comments;
    }

    private static boolean sourceGapMayContainComments(Node root, Position start, Position end) {
        CompilationUnit unit = root.findCompilationUnit().orElse(null);
        if (unit == null || !unit.containsData(SOURCE_COMMENT_STARTS)) {
            return true;
        }
        // Detached and reparented comments retain their source positions, so this is a superset.
        Position next = unit.getData(SOURCE_COMMENT_STARTS).higher(start);
        return next != null && next.isBefore(end);
    }

    /** Assigns opening and trailing comments to the call boundary that prints them. */
    static void normaliseCallCommentOwners(Node root) {
        for (Node owner : root.findAll(Node.class, node -> node instanceof NodeWithArguments<?>)) {
            NodeWithArguments<?> call = (NodeWithArguments<?>) owner;
            if (owner instanceof MethodCallExpr method) {
                normaliseSelectorComments(root, method);
            }
            normaliseChainSuffixComments(root, owner);
            argumentOpening(owner, call).ifPresent(opening -> {
                Position end = call.getArguments().isEmpty() ? argumentClosing(owner, opening)
                        : call.getArgument(0).getBegin().orElseThrow();
                List<Comment> leading = takeCommentsBetween(root, opening, end);
                for (int i = 0; i < leading.size(); i++) {
                    if (!call.getArguments().isEmpty() && i == leading.size() - 1
                            && call.getArgument(0).getComment().isEmpty()) {
                        call.getArgument(0).setComment(leading.get(i));
                    } else {
                        owner.addOrphanComment(leading.get(i));
                    }
                }
            });
            for (int i = 0; i < call.getArguments().size(); i++) {
                Expression argument = call.getArgument(i);
                Optional<Position> end = i + 1 < call.getArguments().size()
                        ? call.getArgument(i + 1).getBegin() : closingAfterArgument(argument);
                if (argument.getEnd().isPresent() && end.isPresent()) {
                    for (Comment comment : takeCommentsBetween(root, argument.getEnd().orElseThrow(), end.orElseThrow())) {
                        owner.addOrphanComment(comment);
                    }
                }
            }
        }
    }

    static List<Comment> takeEndingArgumentComments(Node owner) {
        if (!(owner instanceof NodeWithArguments<?> call)) {
            return List.of();
        }
        if (call.getArguments().isEmpty()) {
            return argumentOpening(owner, call).map(opening ->
                    takeCommentsBetween(owner, opening, argumentClosing(owner, opening))).orElseGet(List::of);
        }
        Expression last = call.getArgument(call.getArguments().size() - 1);
        return last.getEnd().flatMap(start -> closingAfterArgument(last)
                .map(end -> takeCommentsBetween(owner, start, end))).orElseGet(List::of);
    }

    /** Anonymous-body comments must not be owned by the constructor type or argument nodes. */
    static void normaliseAnonymousBodyCommentOwners(Node root) {
        for (ObjectCreationExpr creation : root.findAll(ObjectCreationExpr.class)) {
            if (creation.getAnonymousClassBody().isEmpty() || creation.getEnd().isEmpty()) {
                continue;
            }
            var members = creation.getAnonymousClassBody().orElseThrow();
            Optional<Position> opening = anonymousBodyOpening(creation);
            if (opening.isEmpty()) {
                continue;
            }
            Position end = members.isEmpty() ? creation.getEnd().orElseThrow() : members.get(0).getBegin().orElseThrow();
            for (Comment comment : takeCommentsBetween(root, opening.orElseThrow(), end)) {
                creation.addOrphanComment(comment);
            }
            if (!members.isEmpty()) {
                Position start = members.get(members.size() - 1).getEnd().orElseThrow();
                for (Comment comment : takeCommentsBetween(root, start, creation.getEnd().orElseThrow())) {
                    creation.addOrphanComment(comment);
                }
            }
        }
    }

    static List<Comment> takeAnonymousBodyEndingComments(ObjectCreationExpr creation) {
        var members = creation.getAnonymousClassBody().orElseThrow();
        Optional<Position> start = members.isEmpty() ? anonymousBodyOpening(creation)
                : members.get(members.size() - 1).getEnd();
        return start.flatMap(begin -> creation.getEnd().map(end -> takeCommentsBetween(creation, begin, end)))
                .orElseGet(List::of);
    }

    private static Optional<Position> anonymousBodyOpening(ObjectCreationExpr creation) {
        Optional<Position> closing = creation.getArguments().isEmpty()
                ? argumentOpening(creation, creation).map(opening -> argumentClosing(creation, opening))
                : closingAfterArgument(creation.getArgument(creation.getArguments().size() - 1));
        if (closing.isPresent() && creation.getTokenRange().isPresent()) {
            for (JavaToken token : creation.getTokenRange().orElseThrow()) {
                if (token.getText().equals("{") && token.getRange().isPresent()
                        && token.getRange().orElseThrow().begin.isAfter(closing.orElseThrow())) {
                    return token.getRange().map(range -> range.end);
                }
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings({IDENTITY_COMPARISON, PMD_IDENTITY_COMPARISON})
    private static void normaliseChainSuffixComments(Node root, Node owner) {
        if (owner.getParentNode().orElse(null) instanceof MethodCallExpr parent
                && parent.getScope().orElse(null) == owner && owner.getTokenRange().isPresent()) {
            JavaToken last = owner.getTokenRange().orElseThrow().getEnd();
            nextCodeToken(last).filter(token -> token.getText().equals("."))
                    .ifPresent(dot -> {
                        List<Comment> before = takeTokenGap(root, last, dot);
                        if (!before.isEmpty()) {
                            parent.setData(CHAIN_DOT_PREFIX, before);
                        }
                        nextCodeToken(dot).ifPresent(next -> {
                            List<Comment> suffix = takeTokenGap(root, dot, next,
                                    token -> token.getText().startsWith("//"));
                            if (!suffix.isEmpty()) {
                                parent.setData(CHAIN_DOT_SUFFIX, suffix);
                            }
                        });
                    });
        }
    }

    static List<Comment> takeChainDotSuffix(MethodCallExpr call) {
        return takeCapturedComments(call, CHAIN_DOT_SUFFIX);
    }

    static List<Comment> takeChainDotPrefix(MethodCallExpr call) {
        return takeCapturedComments(call, CHAIN_DOT_PREFIX);
    }

    private static void normaliseSelectorComments(Node root, MethodCallExpr call) {
        var range = call.getName().getTokenRange().orElseThrow();
        previousCodeToken(range.getBegin()).filter(left ->
                        (left.getText().equals(".") && call.getScope().isPresent())
                                || (left.getText().equals(">") && call.getTypeArguments().isPresent()))
                .ifPresent(left -> {
            List<Comment> comments = takeTokenGap(root, left, range.getBegin(),
                    token -> token.getText().startsWith("//"));
            if (!comments.isEmpty()) {
                call.getName().setData(SELECTOR_PREFIX, comments);
            }
        });
        nextCodeToken(range.getEnd()).filter(right -> right.getText().equals("("))
                .ifPresent(right -> {
            List<Comment> comments = takeTokenGap(root, range.getEnd(), right,
                    token -> token.getText().startsWith("//"));
            if (!comments.isEmpty()) {
                call.getName().setData(SELECTOR_SUFFIX, comments);
            }
        });
    }

    static List<Comment> takeSelectorPrefix(Node name) {
        return takeCapturedComments(name, SELECTOR_PREFIX);
    }

    static List<Comment> takeSelectorSuffix(Node name) {
        return takeCapturedComments(name, SELECTOR_SUFFIX);
    }

    private static Optional<Position> argumentOpening(Node owner, NodeWithArguments<?> call) {
        if (!call.getArguments().isEmpty()) {
            return call.getArgument(0).getTokenRange().flatMap(range -> previousCodeToken(range.getBegin()))
                    .filter(token -> token.getText().equals("("))
                    .flatMap(JavaToken::getRange).map(range -> range.end);
        }
        Node callee = owner instanceof MethodCallExpr method ? method.getName()
                : owner instanceof ObjectCreationExpr creation ? creation.getType() : owner;
        return callee.getTokenRange().flatMap(range -> {
            var token = range.getEnd().getNextToken();
            while (token.isPresent() && token.orElseThrow().getCategory().isWhitespaceOrComment()) {
                token = token.orElseThrow().getNextToken();
            }
            return token.filter(t -> t.getText().equals("("))
                    .flatMap(JavaToken::getRange).map(tokenRange -> tokenRange.end);
        });
    }

    private static Position argumentClosing(Node owner, Position opening) {
        for (JavaToken token : owner.getTokenRange().orElseThrow()) {
            if (token.getText().equals(")") && token.getRange().isPresent()
                    && token.getRange().orElseThrow().begin.isAfter(opening)) {
                return token.getRange().orElseThrow().begin;
            }
        }
        return opening;
    }

    private static Optional<Position> closingAfterArgument(Expression argument) {
        return argument.getTokenRange().flatMap(range -> {
            var token = range.getEnd().getNextToken();
            while (token.isPresent() && token.orElseThrow().getCategory().isWhitespaceOrComment()) {
                token = token.orElseThrow().getNextToken();
            }
            return token.filter(next -> next.getText().equals(")"))
                    .flatMap(JavaToken::getRange).map(end -> end.begin);
        });
    }

    static Optional<JavaToken> previousCodeToken(JavaToken token) {
        var previous = token.getPreviousToken();
        while (previous.isPresent() && previous.orElseThrow().getCategory().isWhitespaceOrComment()) {
            previous = previous.orElseThrow().getPreviousToken();
        }
        return previous;
    }

    /** Keeps operator-gap comments outside the expressions to which JavaParser attaches them. */
    static void normaliseOperatorCommentOwners(Node root) {
        for (ConditionalExpr expression : root.findAll(ConditionalExpr.class)) {
            if (expression.getThenExpr().getTokenRange().isEmpty()
                    || expression.getElseExpr().getTokenRange().isEmpty()) {
                continue;
            }
            JavaToken question = previousCodeToken(expression.getThenExpr().getTokenRange().orElseThrow().getBegin())
                    .orElseThrow();
            JavaToken colon = previousCodeToken(expression.getElseExpr().getTokenRange().orElseThrow().getBegin())
                    .orElseThrow();
            expression.setData(CONDITIONAL_COMMENTS, new ConditionalComments(
                    takeTokenGap(root, expression.getCondition().getTokenRange().orElseThrow().getEnd(), question),
                    takeTokenGap(root, question, expression.getThenExpr().getTokenRange().orElseThrow().getBegin()),
                    takeTokenGap(root, expression.getThenExpr().getTokenRange().orElseThrow().getEnd(), colon),
                    takeTokenGap(root, colon, expression.getElseExpr().getTokenRange().orElseThrow().getBegin())));
        }
        for (BinaryExpr expression : root.findAll(BinaryExpr.class)) {
            if (expression.getLeft().getTokenRange().isEmpty()) {
                continue;
            }
            JavaToken left = expression.getLeft().getTokenRange().orElseThrow().getEnd();
            JavaToken operator = nextCodeToken(left).orElseThrow();
            List<Comment> comments = takeTokenGap(root, left, operator);
            if (!comments.isEmpty()) {
                expression.setData(OPERATOR_SUFFIX, comments);
                Expression suffixOperand = expression.getLeft();
                while (suffixOperand instanceof BinaryExpr binary) {
                    suffixOperand = binary.getRight();
                }
                InlineBlockComments.addRetainedWidths(suffixOperand, left.getRange().orElseThrow().end, comments);
            }
        }
        for (IfStmt statement : root.findAll(IfStmt.class)) {
            if (statement.getCondition().getTokenRange().isEmpty()) {
                continue;
            }
            JavaToken closer = nextCodeToken(statement.getCondition().getTokenRange().orElseThrow().getEnd())
                    .orElseThrow();
            List<Comment> comments = takeTokenGap(root, closer, nextCodeToken(closer).orElseThrow());
            if (!comments.isEmpty()) {
                statement.setData(OPERATOR_SUFFIX, comments);
            }
        }
    }

    static Optional<JavaToken> nextCodeToken(JavaToken token) {
        var next = token.getNextToken();
        while (next.isPresent() && next.orElseThrow().getCategory().isWhitespaceOrComment()) {
            next = next.orElseThrow().getNextToken();
        }
        return next;
    }

    private static List<Comment> takeTokenGap(Node root, JavaToken left, JavaToken right) {
        return takeTokenGap(root, left, right, token -> token.getCategory().isComment());
    }

    private static List<Comment> takeTokenGap(Node root, JavaToken left, JavaToken right, Predicate<JavaToken> capture) {
        var token = left.getNextToken();
        while (token.isPresent() && token.orElseThrow().getRange().isPresent()
                && token.orElseThrow().getRange().orElseThrow().begin.isBefore(right.getRange().orElseThrow().begin)) {
            if (capture.test(token.orElseThrow())) {
                return takeCommentsBetween(root, left.getRange().orElseThrow().end, right.getRange().orElseThrow().begin);
            }
            token = token.orElseThrow().getNextToken();
        }
        return List.of();
    }

    static boolean hasOperatorSuffix(Node node) {
        return node.containsData(OPERATOR_SUFFIX);
    }

    static boolean operatorSuffixBreaksLine(Node node) {
        if (!(node instanceof BinaryExpr expression) || !node.containsData(OPERATOR_SUFFIX)) {
            return false;
        }
        int leftLine = expression.getLeft().getEnd().orElseThrow().line;
        return node.getData(OPERATOR_SUFFIX).stream().anyMatch(comment -> comment instanceof LineComment
                || comment.getBegin().orElseThrow().line != leftLine
                || comment.getEnd().orElseThrow().line != leftLine);
    }

    static List<Comment> takeOperatorSuffix(Node node) {
        return takeCapturedComments(node, OPERATOR_SUFFIX);
    }

    static ConditionalComments takeConditionalComments(ConditionalExpr expression) {
        if (!expression.containsData(CONDITIONAL_COMMENTS)) {
            return new ConditionalComments(List.of(), List.of(), List.of(), List.of());
        }
        ConditionalComments comments = expression.getData(CONDITIONAL_COMMENTS);
        expression.removeData(CONDITIONAL_COMMENTS);
        return comments;
    }

    static Optional<Position> commaBeforeArgument(Expression argument) {
        return argument.getTokenRange().flatMap(range -> previousCodeToken(range.getBegin()))
                .filter(token -> token.getText().equals(","))
                .flatMap(JavaToken::getRange).map(range -> range.end);
    }

    static boolean hasCommentInside(Node node) {
        return node.getTokenRange().map(range -> {
            for (JavaToken token : range) {
                if (token.getCategory().isComment()) {
                    return true;
                }
            }
            return false;
        }).orElse(false);
    }

    /** Keeps annotation suffix comments between annotations in the containing declaration. */
    static void normaliseAnnotationCommentOwners(Node root) {
        for (AnnotationExpr annotation : root.findAll(AnnotationExpr.class)) {
            Node parent = annotation.getParentNode().orElse(null);
            if (!(parent instanceof NodeWithAnnotations<?>) || annotation.getEnd().isEmpty()) {
                continue;
            }
            followingCodePosition(annotation).ifPresent(end -> {
                for (Comment comment : takeCommentsBetween(root, annotation.getEnd().orElseThrow(), end)) {
                    parent.addOrphanComment(comment);
                }
            });
        }
    }

    static List<Comment> takeAnnotationSuffixComments(AnnotationExpr annotation) {
        Node parent = annotation.getParentNode().orElse(null);
        if (!(parent instanceof NodeWithAnnotations<?>) || annotation.getEnd().isEmpty()) {
            return List.of();
        }
        return followingCodePosition(annotation)
                .map(end -> takeCommentsBetween(parent, annotation.getEnd().orElseThrow(), end)).orElseGet(List::of);
    }

    private static Optional<Position> followingCodePosition(Node node) {
        if (node.getTokenRange().isEmpty()) {
            return Optional.empty();
        }
        var token = node.getTokenRange().orElseThrow().getEnd().getNextToken();
        while (token.isPresent() && token.orElseThrow().getCategory().isWhitespaceOrComment()) {
            token = token.orElseThrow().getNextToken();
        }
        return token.flatMap(JavaToken::getRange).map(range -> range.begin);
    }

    static List<Comment> takeCommentsBeforeModifier(Node modifier) {
        Node parent = modifier.getParentNode().orElse(null);
        if (!(parent instanceof NodeWithAnnotations<?> annotated) || annotated.getAnnotations().isEmpty()
                || modifier.getBegin().isEmpty()) {
            return List.of();
        }
        return annotated.getAnnotations().stream().filter(annotation -> annotation.getEnd().isPresent()
                        && annotation.getEnd().orElseThrow().isBefore(modifier.getBegin().orElseThrow()))
                .map(annotation -> annotation.getEnd().orElseThrow()).max(Position::compareTo)
                .map(end -> takeCommentsBetween(parent, end, modifier.getBegin().orElseThrow()))
                .orElseGet(List::of);
    }

    /** Keeps final-case comments inside the switch rather than after its closing brace. */
    static void normaliseFinalSwitchEntryComments(Node root) {
        for (SwitchStmt statement : root.findAll(SwitchStmt.class)) {
            normaliseFinalSwitchEntryComments(root, statement, statement.getEntries());
        }
        for (SwitchExpr expression : root.findAll(SwitchExpr.class)) {
            normaliseFinalSwitchEntryComments(root, expression, expression.getEntries());
        }
    }

    private static void normaliseFinalSwitchEntryComments(Node root, Node owner, NodeList<SwitchEntry> entries) {
        if (entries.isEmpty() || owner.getEnd().isEmpty()) {
            return;
        }
        var last = entries.get(entries.size() - 1);
        Optional<Position> start = last.getStatements().isEmpty() ? last.getEnd()
                : last.getStatements().get(last.getStatements().size() - 1).getEnd();
        if (start.isPresent()) {
            for (Comment comment : takeCommentsBetween(root, start.orElseThrow(), owner.getEnd().orElseThrow())) {
                last.addOrphanComment(comment);
            }
        }
    }

    /** A comment after a grouped operand belongs after its closing parenthesis. */
    static void normaliseEnclosedExpressionSuffixComments(Node root) {
        for (EnclosedExpr enclosed : root.findAll(EnclosedExpr.class)) {
            if (enclosed.getComment().isPresent() || enclosed.getEnd().isEmpty()) {
                continue;
            }
            Position end = enclosed.getEnd().orElseThrow();
            enclosed.getAllContainedComments().stream()
                    .filter(comment -> comment instanceof LineComment && comment.getBegin().isPresent()
                            && comment.getBegin().orElseThrow().line == end.line
                            && comment.getBegin().orElseThrow().isAfter(end))
                    .findFirst().ifPresent(comment -> {
                        removeMirroredCommentOwners(enclosed, comment);
                        enclosed.setComment(comment);
                    });
        }
    }

    /** Returns whether comments exist between two statements in the same block. */
    static boolean hasCommentBetweenStatements(BlockStmt block, Statement previous, Statement current) {
        return hasCommentBetweenStatements(block.getAllContainedComments(), previous, current);
    }

    /**
     * Same predicate as {@link #hasCommentBetweenStatements(BlockStmt, Statement, Statement)} but
     * receives a pre-collected list of the block's contained comments so the caller can amortize
     * one {@link Node#getAllContainedComments()} traversal across all statement pairs in the block
     * (the underlying call recursively walks the whole subtree and allocates a fresh
     * {@link java.util.LinkedList}, so calling it once per pair is O(N²) per block).
     */
    static boolean hasCommentBetweenStatements(
            List<Comment> blockContainedComments, Statement previous, Statement current) {
        if (blockContainedComments.isEmpty()
                || previous.getRange().isEmpty()
                || current.getRange().isEmpty()) {
            return false;
        }
        int startLineExclusive = previous.getRange().get().end.line;
        int currentLine = current.getRange().get().begin.line;
        int currentColumn = current.getRange().get().begin.column;
        for (Comment comment : blockContainedComments) {
            if (comment.getRange().isEmpty()) {
                continue;
            }
            int line = comment.getRange().get().begin.line;
            if (line > startLineExclusive && line < currentLine) {
                return true;
            }
            if (line == currentLine && comment.getRange().get().end.column < currentColumn) {
                return true;
            }
        }
        return false;
    }

    /** Returns true when a line/block comment would print before the node body. */
    static boolean hasLineOrBlockCommentPrintedBeforeNode(Node node) {
        if (node.getRange().isEmpty()) {
            return false;
        }
        int nodeBegin = node.getRange().get().begin.line;
        Optional<Comment> c = node.getComment();
        if (c.isPresent()
                && (c.get() instanceof LineComment || c.get() instanceof BlockComment)
                && c.get().getRange().isPresent()
                && c.get().getRange().get().begin.line <= nodeBegin) {
            return true;
        }
        for (Comment nested : node.getAllContainedComments()) {
            if ((nested instanceof LineComment || nested instanceof BlockComment)
                    && nested.getRange().isPresent()
                    && nested.getRange().get().begin.line <= nodeBegin) {
                return true;
            }
        }
        return false;
    }

    /** Returns true when a lambda node owns a line/block comment. */
    static boolean hasAnyLineOrBlockCommentOnLambda(Node node) {
        if (!(node instanceof LambdaExpr)) {
            return false;
        }
        Optional<Comment> c = node.getComment();
        if (c.isEmpty()) {
            return false;
        }
        Comment comment = c.get();
        return comment instanceof LineComment || comment instanceof BlockComment;
    }

    /** Returns true when a node's direct comment is line/block style. */
    static boolean hasLineOrBlockComment(Node node) {
        Optional<Comment> c = node.getComment();
        if (c.isEmpty()) {
            return false;
        }
        Comment comment = c.get();
        return comment instanceof LineComment || comment instanceof BlockComment;
    }

    /** Returns true for line/block comments that contain only whitespace. */
    static boolean isEmptyLineOrBlockComment(Comment comment) {
        return (comment instanceof LineComment || comment instanceof BlockComment)
                && comment.getContent().trim().isEmpty();
    }

    /** Returns first line/block comment that would print before an expression. */
    static Optional<Comment> firstLineOrBlockCommentPrintedBeforeExpression(Expression expression) {
        if (expression.getRange().isEmpty()) {
            return Optional.empty();
        }
        int expressionBeginLine = expression.getRange().get().begin.line;
        int expressionBeginColumn = expression.getRange().get().begin.column;
        List<Comment> comments = new ArrayList<>();
        expression.getComment().ifPresent(comments::add);
        comments.addAll(expression.getAllContainedComments());
        return comments.stream()
                .filter(comment -> isCommentBeforeExpression(comment, expressionBeginLine, expressionBeginColumn))
                .min(
                        Comparator.comparingInt(
                                        (Comment comment) ->
                                                comment.getRange().map(r -> r.begin.line).orElse(Integer.MAX_VALUE))
                                .thenComparingInt(
                                        comment ->
                                                comment.getRange().map(r -> r.begin.column).orElse(Integer.MAX_VALUE)));
    }

    /** Returns true when comment starts before expression source position. */
    private static boolean isCommentBeforeExpression(Comment comment, int expressionBeginLine, int expressionBeginColumn) {
        if (!isLineOrBlock(comment) || comment.getRange().isEmpty()) {
            return false;
        }
        int commentBeginLine = comment.getRange().get().begin.line;
        if (commentBeginLine < expressionBeginLine) {
            return true;
        }
        if (commentBeginLine > expressionBeginLine) {
            return false;
        }
        return comment.getRange().get().end.column < expressionBeginColumn;
    }

    /** Returns true when node or descendants contain a line/block comment. */
    static boolean hasAnyLineOrBlockComment(Node node) {
        if (node.getComment().isPresent()
                && (node.getComment().get() instanceof LineComment
                        || node.getComment().get() instanceof BlockComment)) {
            return true;
        }
        for (Comment c : node.getAllContainedComments()) {
            if (c instanceof LineComment || c instanceof BlockComment) {
                return true;
            }
        }
        return false;
    }

    /** Returns true when there is a comment between two sibling nodes by range. */
    static boolean hasCommentBetweenNodes(Node previous, Node current) {
        if (previous.getRange().isEmpty() || current.getRange().isEmpty()) {
            return false;
        }
        Position start = previous.getRange().orElseThrow().end;
        Position end = current.getRange().orElseThrow().begin;
        if (previous.getTokenRange().isPresent()) {
            var token = previous.getTokenRange().orElseThrow().getEnd().getNextToken();
            while (token.isPresent() && token.orElseThrow().getRange().isPresent()
                    && token.orElseThrow().getRange().orElseThrow().begin.isBefore(end)) {
                if (token.orElseThrow().getCategory().isComment()) {
                    return true;
                }
                token = token.orElseThrow().getNextToken();
            }
        }
        Optional<Node> parent = current.getParentNode();
        if (parent.isEmpty()) {
            return false;
        }
        for (Comment comment : parent.get().getAllContainedComments()) {
            if (comment.getRange().isEmpty()) {
                continue;
            }
            if (comment.getRange().orElseThrow().begin.isAfter(start)
                    && comment.getRange().orElseThrow().end.isBefore(end)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true when a node has a line/block comment lexically before its start.
     *
     * <p>Line/block comments on nodes printed immediately after {@code =} or {@code ->} without a line
     * break can be re-attached to a different AST node on the next parse — use a continuation line
     * before printing such nodes.
     */
    static boolean hasLeadingLineOrBlockComment(Node node) {
        Optional<Comment> c = node.getComment();
        if (c.isEmpty() || node.getRange().isEmpty() || c.get().getRange().isEmpty()) {
            return false;
        }
        Comment comment = c.get();
        if (!(comment instanceof LineComment || comment instanceof BlockComment)) {
            return false;
        }
        int commentLine = comment.getRange().get().begin.line;
        int nodeLine = node.getRange().get().begin.line;
        if (commentLine < nodeLine) {
            return true;
        }
        if (commentLine > nodeLine) {
            return false;
        }
        int commentEndColumn = comment.getRange().get().end.column;
        int nodeBeginColumn = node.getRange().get().begin.column;
        return commentEndColumn < nodeBeginColumn;
    }

    /** Returns true when a node has a same-line trailing line/block comment. */
    static boolean hasTrailingLineOrBlockComment(Node node) {
        Optional<Comment> c = node.getComment();
        if (c.isEmpty() || node.getRange().isEmpty() || c.get().getRange().isEmpty()) {
            return false;
        }
        Comment comment = c.get();
        if (!(comment instanceof LineComment || comment instanceof BlockComment)) {
            return false;
        }
        int commentLine = comment.getRange().get().begin.line;
        int nodeLine = node.getRange().get().end.line;
        if (commentLine != nodeLine) {
            return false;
        }
        int commentBeginColumn = comment.getRange().get().begin.column;
        int nodeEndColumn = node.getRange().get().end.column;
        return commentBeginColumn > nodeEndColumn;
    }

    /** Returns true when any operand has a leading line/block comment. */
    static boolean anyOperandHasLeadingLineOrBlockComment(List<Expression> parts) {
        for (Expression p : parts) {
            if (hasLeadingLineOrBlockComment(p)) {
                return true;
            }
        }
        return false;
    }

    /** Returns true when any operand has a trailing line/block comment. */
    static boolean anyOperandHasTrailingLineOrBlockComment(List<Expression> parts) {
        for (Expression p : parts) {
            if (hasTrailingLineOrBlockComment(p)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extracts misplaced orphan line comments from before a type name token.
     *
     * <p>JavaParser sometimes attaches a line comment that lexically follows {@code extends Type {}}
     * as an orphan <em>before</em> the type's simple name (between {@code class} and the name).
     * Relocate those comments into the type body so they are not printed twice and idempotency holds.
     */
    static List<Comment> extractLineCommentsMisplacedBeforeTypeName(ClassOrInterfaceDeclaration n) {
        if (n.getName().getRange().isEmpty()) {
            return List.of();
        }
        int nameLine = n.getName().getRange().get().begin.line;
        int nameCol = n.getName().getRange().get().begin.column;
        List<Comment> out = new ArrayList<>();
        for (Comment c : new ArrayList<>(n.getOrphanComments())) {
            if (!(c instanceof LineComment)) {
                continue;
            }
            if (c.getRange().isEmpty()) {
                continue;
            }
            int cl = c.getRange().get().begin.line;
            int cc = c.getRange().get().begin.column;
            if (cl == nameLine && cc < nameCol) {
                c.remove();
                out.add(c);
            }
        }
        out.sort(
                Comparator.comparingInt((Comment c) -> c.getRange().orElseThrow().begin.line)
                        .thenComparingInt(c -> c.getRange().orElseThrow().begin.column));
        return out;
    }

    /**
     * Extracts and deduplicates orphan line comments on the type-name line.
     *
     * <p>With {@code extends} / {@code implements}, JavaParser may duplicate the same opening-brace
     * line comment as several orphan nodes on the header line. Strip the whole line's line-comment
     * orphans and keep one copy per distinct comment text (rightmost wins).
     */
    static List<Comment> extractAndDedupeLineCommentsOnTypeNameLine(ClassOrInterfaceDeclaration n) {
        if (n.getName().getRange().isEmpty()) {
            return List.of();
        }
        int headerLine = n.getName().getRange().get().begin.line;
        List<LineComment> candidates = new ArrayList<>();
        for (Comment c : new ArrayList<>(n.getOrphanComments())) {
            if (c instanceof LineComment lc
                    && c.getRange().isPresent()
                    && c.getRange().get().begin.line == headerLine) {
                c.remove();
                candidates.add(lc);
            }
        }
        candidates.sort(Comparator.comparingInt(c -> c.getRange().orElseThrow().begin.column));
        Map<String, LineComment> uniqueByContent = new LinkedHashMap<>();
        for (LineComment lc : candidates) {
            uniqueByContent.put(lc.getContent(), lc);
        }
        List<Comment> out = new ArrayList<>(uniqueByContent.values());
        out.sort(Comparator.comparingInt(c -> c.getRange().orElseThrow().begin.column));
        return out;
    }

    /**
     * Removes duplicate header line comments mirrored onto type-clause entries.
     *
     * <p>Some headers carry the same trailing line comment on both the simple name and a type-clause
     * entry (for example, both {@code OperatorNot} and {@code extends SpelNodeImpl}). Remove the
     * duplicate from type-clause entries so we print it once and stay idempotent.
     */
    static void pruneDuplicatedHeaderLineCommentsOnTypeClauses(ClassOrInterfaceDeclaration n) {
        Optional<Comment> nameComment = n.getName().getComment();
        if (nameComment.isEmpty() || !(nameComment.get() instanceof LineComment) || nameComment.get().getRange().isEmpty()) {
            return;
        }
        Comment nc = nameComment.get();
        String content = nc.getContent();
        int line = nc.getRange().orElseThrow().begin.line;
        for (ClassOrInterfaceType t : n.getExtendedTypes()) {
            Optional<Comment> tc = t.getComment();
            if (tc.isPresent()
                    && tc.get() instanceof LineComment
                    && tc.get().getRange().isPresent()
                    && tc.get().getRange().orElseThrow().begin.line == line
                    && Objects.equals(tc.get().getContent(), content)) {
                tc.get().remove();
            }
        }
        for (ClassOrInterfaceType t : n.getImplementedTypes()) {
            Optional<Comment> tc = t.getComment();
            if (tc.isPresent()
                    && tc.get() instanceof LineComment
                    && tc.get().getRange().isPresent()
                    && tc.get().getRange().orElseThrow().begin.line == line
                    && Objects.equals(tc.get().getContent(), content)) {
                tc.get().remove();
            }
        }
    }

    /** Returns a comment candidate that should be hoisted before wrapped call arguments. */
    static Optional<Comment> hoistableArgumentComment(MethodCallExpr mc) {
        if (mc.getArguments().size() != SINGLE_ARGUMENT_COUNT) {
            return Optional.empty();
        }
        Expression only = mc.getArguments().get(0);
        if (mc.getOrphanComments().stream().anyMatch(comment -> comment.getBegin().isPresent()
                && only.getBegin().isPresent()
                && comment.getBegin().orElseThrow().isBefore(only.getBegin().orElseThrow()))) {
            return Optional.empty();
        }
        if (only instanceof LambdaExpr || hasTrailingLineOrBlockComment(only)) {
            return Optional.empty();
        }
        if (hasLineOrBlockComment(only)) {
            return only.getComment().filter(comment -> isBeforeArgumentOpening(mc, comment));
        }
        return firstLineOrBlockCommentPrintedBeforeExpression(only)
                .filter(comment -> isBeforeArgumentOpening(mc, comment));
    }

    private static boolean isBeforeArgumentOpening(MethodCallExpr call, Comment comment) {
        return comment.getBegin().isEmpty() || argumentOpening(call, call).isEmpty()
                || comment.getBegin().orElseThrow().isBefore(argumentOpening(call, call).orElseThrow());
    }

    /** Returns a base comment suitable for hoisting on wrapped method chains. */
    static Optional<Comment> hoistableWrappedChainBaseComment(Expression base) {
        Optional<Comment> comment = base.getComment();
        if (comment.isPresent() && isEmptyLineOrBlockComment(comment.get())) {
            return comment;
        }
        return Optional.empty();
    }

    /** Returns true when any part of a call chain contains line/block comments. */
    static boolean chainHasLineOrBlockComments(Expression base, List<MethodCallExpr> calls) {
        if (hasAnyLineOrBlockComment(base)) {
            return true;
        }
        for (MethodCallExpr mc : calls) {
            if (mc.containsData(CHAIN_DOT_PREFIX) || mc.containsData(CHAIN_DOT_SUFFIX)
                    || mc.getName().containsData(SELECTOR_PREFIX) || mc.getName().containsData(SELECTOR_SUFFIX)
                    || hasAnyLineOrBlockComment(mc) || hasAnyLineOrBlockComment(mc.getName())) {
                return true;
            }
        }
        return false;
    }

    /** Returns true when any argument is a lambda with a block body. */
    static boolean hasBlockLambdaArgument(NodeList<? extends Expression> args) {
        for (Expression expression : args) {
            if (expression instanceof LambdaExpr lambda && lambda.getBody() instanceof BlockStmt) {
                return true;
            }
        }
        return false;
    }

    /** Returns true when comment is either line or block style. */
    private static boolean isLineOrBlock(Comment comment) {
        return comment instanceof LineComment || comment instanceof BlockComment;
    }
}
