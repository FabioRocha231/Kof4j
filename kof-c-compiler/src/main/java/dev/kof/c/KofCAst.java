package dev.kof.c;

import java.util.List;

public final class KofCAst {
    public record Program(List<VarDecl> globals, List<FuncDecl> funcs) {}

    public record VarDecl(String name) {}

    public record FuncDecl(String name, List<Stmt> body) {}

    public sealed interface Stmt permits IfStmt, WhileStmt, AsmStmt, CallStmt, AssignStmt {}

    public record IfStmt(Expr cond, List<Stmt> thenBody) implements Stmt {}
    public record WhileStmt(Expr cond, List<Stmt> body) implements Stmt {}
    public record AsmStmt(int value) implements Stmt {}
    public record CallStmt(String name) implements Stmt {}
    public record AssignStmt(boolean deref, String target, Expr value) implements Stmt {}

    public sealed interface Expr permits BinaryExpr, UnaryDeref, UnaryAddr, ParenExpr, IdentExpr, IntExpr {}

    public record BinaryExpr(Expr left, String op, Expr right) implements Expr {}
    // Unary cases:
    //  - deref: *(int*)ident
    //  - addr: &ident
    //  - ident / int / paren are separate Expr types
    public record UnaryDeref(String ident) implements Expr {}
    public record UnaryAddr(String ident) implements Expr {}
    public record ParenExpr(Expr inner) implements Expr {}
    public record IdentExpr(String name) implements Expr {}
    public record IntExpr(int value) implements Expr {}
}
