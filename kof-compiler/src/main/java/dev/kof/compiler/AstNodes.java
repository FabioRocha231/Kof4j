package dev.kof.compiler;

import java.util.List;

sealed interface AstNode {
    SourcePosition position();
}

record CompilationUnitNode(SourcePosition position, String packageName, List<String> imports,
                           List<? extends AstNode> declarations) implements AstNode {
}

record FunctionDeclarationNode(SourcePosition position, List<String> modifiers, String returnType,
                               String name, List<FormalParameterNode> parameters,
                               List<String> thrownExceptions, List<String> typeParameters,
                               List<StatementNode> body) implements AstNode {
}

sealed interface TypeDeclarationNode extends AstNode {
    String name();
    List<String> modifiers();
}

record ClassDeclarationNode(SourcePosition position, String name, List<String> modifiers,
                            String superClass, List<String> interfaces, List<String> typeParameters,
                            List<? extends AstNode> members) implements TypeDeclarationNode {
}

record InterfaceDeclarationNode(SourcePosition position, String name, List<String> modifiers,
                                List<String> interfaces,
                                List<? extends AstNode> members) implements TypeDeclarationNode {
}

record RecordDeclarationNode(SourcePosition position, String name, List<String> modifiers,
                             String superClass, List<String> interfaces,
                             List<RecordComponentNode> components,
                             List<? extends AstNode> members) implements TypeDeclarationNode {
}

record RecordComponentNode(SourcePosition position, List<String> modifiers, String type, String name,
                            ExpressionNode initializer) implements AstNode {
}

sealed interface MemberNode extends AstNode {
}

record FieldDeclarationNode(SourcePosition position, List<String> modifiers, String type,
                            String name, ExpressionNode initializer) implements MemberNode {
}

record MethodDeclarationNode(SourcePosition position, List<String> modifiers, String returnType,
                             String name, List<FormalParameterNode> parameters,
                             List<String> thrownExceptions, List<StatementNode> body) implements MemberNode {
}

record ConstructorDeclarationNode(SourcePosition position, List<String> modifiers,
                                  String name, List<FormalParameterNode> parameters,
                                  List<String> thrownExceptions,
                                  List<StatementNode> body) implements MemberNode {
}

record FormalParameterNode(SourcePosition position, List<String> modifiers, String type,
                           String name) implements AstNode {
}

sealed interface ExpressionNode extends AstNode {
}

record IdentifierExpr(SourcePosition position, String name) implements ExpressionNode {
}

sealed interface LiteralKind {
}

enum ConcreteLiteralKind implements LiteralKind {
    INT, LONG, FLOAT, DOUBLE, STRING, CHAR, BOOLEAN, NULL
}

record LiteralExpr(SourcePosition position, LiteralKind kind, String value) implements ExpressionNode {
}

record BinaryExpr(SourcePosition position, String operator, ExpressionNode left,
                  ExpressionNode right) implements ExpressionNode {
}

record UnaryExpr(SourcePosition position, String operator, ExpressionNode operand,
                 boolean prefix) implements ExpressionNode {
}

record AssignmentExpr(SourcePosition position, ExpressionNode target,
                      String operator, ExpressionNode value) implements ExpressionNode {
}

record MethodCallExpr(SourcePosition position, ExpressionNode receiver,
                      String methodName, List<String> typeArguments,
                      List<ExpressionNode> arguments) implements ExpressionNode {
}

record NewExpr(SourcePosition position, String typeName, List<String> typeArguments,
               List<ExpressionNode> arguments) implements ExpressionNode {
}

record NewArrayExpr(SourcePosition position, String elementType, ExpressionNode size) implements ExpressionNode {
}

record ArrayAccessExpr(SourcePosition position, ExpressionNode receiver, ExpressionNode index) implements ExpressionNode {
}

record FieldAccessExpr(SourcePosition position, ExpressionNode receiver,
                       String fieldName) implements ExpressionNode {
}

record IfExpr(SourcePosition position, ExpressionNode condition,
              ExpressionNode thenExpr, ExpressionNode elseExpr) implements ExpressionNode {
}

record LambdaExpr(SourcePosition position, List<FormalParameterNode> parameters,
                  List<StatementNode> body) implements ExpressionNode {
}

sealed interface StatementNode extends AstNode {
}

record ExpressionStmt(SourcePosition position, ExpressionNode expression) implements StatementNode {
}

record ReturnStmt(SourcePosition position, ExpressionNode value) implements StatementNode {
}

record BlockStmt(SourcePosition position, List<StatementNode> statements) implements StatementNode {
}

record IfStmt(SourcePosition position, ExpressionNode condition,
              StatementNode thenBranch, StatementNode elseBranch) implements StatementNode {
}

record WhileStmt(SourcePosition position, ExpressionNode condition,
                 StatementNode body) implements StatementNode {
}

record ForStmt(SourcePosition position, StatementNode init, ExpressionNode condition,
               ExpressionNode update, StatementNode body) implements StatementNode {
}

record ForInStmt(SourcePosition position, String varName, ExpressionNode collection,
                 StatementNode body) implements StatementNode {
}

record VarDeclStmt(SourcePosition position, String type, String name,
                   ExpressionNode initializer) implements StatementNode {
}

record ThrowStmt(SourcePosition position, ExpressionNode expression) implements StatementNode {
}

/**
 * SpawnStmt — runs the given call (or block) as a concurrent task.
 * The program waits for spawned tasks before exiting.
 */
record SpawnStmt(SourcePosition position, ExpressionNode expression) implements StatementNode {
}

/**
 * AssertStmt — assert(condition) or assert(condition, "message").
 * Throws "assertion failed" (or the given message) when the condition
 * is false. The failure exit code powers `kof test`.
 */
record AssertStmt(SourcePosition position, ExpressionNode condition, String message) implements StatementNode {
}

record BreakStmt(SourcePosition position) implements StatementNode {
}

record ContinueStmt(SourcePosition position) implements StatementNode {
}

record SwitchCase(SourcePosition position, ExpressionNode value, List<StatementNode> body) implements AstNode {
}

record SwitchStmt(SourcePosition position, ExpressionNode expression,
                  List<SwitchCase> cases, List<StatementNode> defaultBody) implements StatementNode {
}

record CatchClause(SourcePosition position, String exceptionType, String exceptionName,
                   List<StatementNode> body) implements AstNode {
}

record TryStmt(SourcePosition position, List<StatementNode> tryBody,
               List<CatchClause> catchClauses, List<StatementNode> finallyBody) implements StatementNode {
}

record DoWhileStmt(SourcePosition position, ExpressionNode condition,
                   StatementNode body) implements StatementNode {
}
