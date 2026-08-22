package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

class Parser {

    private static final Set<String> PRIMITIVE_TYPE_NAMES = Set.of(
            "bool", "byte", "short", "int", "long", "float", "double", "char", "string", "void"
    );

    private final List<Token> tokens;
    private final DiagnosticCollector diagnostics;
    private final String file;
    private int pos;
    private String currentClassName;

    Parser(List<Token> tokens, DiagnosticCollector diagnostics, String file) {
        this.tokens = tokens;
        this.diagnostics = diagnostics;
        this.file = file;
    }

    CompilationUnitNode parse() {
        SourcePosition pos0 = pos();
        String packageName = parsePackage();
        List<String> imports = parseImports();
        List<AstNode> declarations = new ArrayList<>();
        while (!atEnd()) {
            if (check(TokenType.IDENTIFIER) || check(TokenType.VOID) || isPrimitiveType()) {
                declarations.add(parseFunctionDeclaration(List.of()));
            } else {
                declarations.add(parseTypeDeclaration());
            }
        }
        return new CompilationUnitNode(pos0, packageName, imports, List.copyOf(declarations));
    }

    private FunctionDeclarationNode parseFunctionDeclaration(List<String> mods) {
        SourcePosition p = pos();
        String returnType = "void";
        String name;
        if ((check(TokenType.IDENTIFIER) || check(TokenType.VOID) || isPrimitiveType())
                && !checkNext(TokenType.LPAREN) && !checkNext(TokenType.LESS)) {
            returnType = advance().value();
            name = expectId("Expected function name", "PARSE010");
        } else {
            name = expectId("Expected function name", "PARSE010");
        }
        List<String> typeParams = parseTypeParameters();
        expect(TokenType.LPAREN, "Expected '('", "PARSE011");
        List<FormalParameterNode> params = new ArrayList<>();
        if (!check(TokenType.RPAREN)) {
            params.add(parseFormalParameter());
            while (check(TokenType.COMMA)) { advance(); params.add(parseFormalParameter()); }
        }
        expect(TokenType.RPAREN, "Expected ')'", "PARSE012");
        if (check(TokenType.COLON)) {
            advance();
            returnType = parseTypeRef();
        }
        List<String> thrown = new ArrayList<>();
        List<StatementNode> body = List.of();
        if (check(TokenType.LBRACE)) {
            body = parseBlock();
        } else if (check(TokenType.EQUAL)) {
            advance();
            ExpressionNode expr = parseExpression();
            if (check(TokenType.SEMICOLON)) advance();
            body = List.of(new ReturnStmt(pos(), expr));
        } else {
            expectSemicolon();
        }
        return new FunctionDeclarationNode(p, mods, returnType, name, params, thrown, typeParams, body);
    }

    private List<String> parseTypeParameters() {
        List<String> typeParams = new ArrayList<>();
        if (check(TokenType.LESS)) {
            advance();
            while (!check(TokenType.GREATER) && !atEnd()) {
                if (check(TokenType.IDENTIFIER)) {
                    typeParams.add(advance().value());
                } else {
                    advance();
                }
                if (check(TokenType.COMMA)) advance();
            }
            expect(TokenType.GREATER, "Expected '>' after type parameters", "PARSE075");
        }
        return typeParams;
    }

    private String parsePackage() {
        if (!check(TokenType.PACKAGE)) return "";
        advance();
        StringBuilder name = new StringBuilder();
        if (!check(TokenType.IDENTIFIER)) {
            error("Expected package name", "PARSE001");
            return "";
        }
        name.append(advance().value());
        while (check(TokenType.DOT)) {
            advance();
            if (check(TokenType.IDENTIFIER)) {
                name.append('.').append(advance().value());
            } else {
                error("Expected package name component", "PARSE002");
                break;
            }
        }
        expectSemicolon();
        return name.toString();
    }

    private List<String> parseImports() {
        List<String> result = new ArrayList<>();
        while (check(TokenType.IMPORT)) {
            advance();
            if (check(TokenType.STAR)) {
                advance();
                result.add("*");
            } else {
                StringBuilder path = new StringBuilder();
                if (!check(TokenType.IDENTIFIER)) {
                    error("Expected import name", "PARSE004");
                    break;
                }
                path.append(advance().value());
                while (check(TokenType.DOT)) {
                    advance();
                    if (check(TokenType.STAR)) {
                        advance();
                        path.append(".*");
                        break;
                    } else if (check(TokenType.IDENTIFIER)) {
                        path.append('.').append(advance().value());
                    } else {
                        error("Expected import path component", "PARSE005");
                        break;
                    }
                }
                result.add(path.toString());
            }
            expectSemicolon();
        }
        return result;
    }

    private AstNode parseTypeDeclaration() {
        List<String> mods = parseModifiers();
        if (check(TokenType.CLASS)) return parseClassDeclaration(mods);
        if (check(TokenType.INTERFACE)) return parseInterfaceDeclaration(mods);
        if (check(TokenType.RECORD)) return parseRecordDeclaration(mods);
        error("Expected type declaration", "PARSE007");
        advance();
        return new ClassDeclarationNode(pos(), "error", List.of(), null, List.of(), List.of(), List.of());
    }

    private List<String> parseModifiers() {
        List<String> mods = new ArrayList<>();
        while (check(TokenType.PUBLIC, TokenType.PRIVATE, TokenType.PROTECTED, TokenType.STATIC,
                TokenType.FINAL, TokenType.ABSTRACT, TokenType.TRANSIENT, TokenType.VOLATILE,
                TokenType.SYNCHRONIZED, TokenType.NATIVE, TokenType.DEFAULT, TokenType.OVERRIDE)) {
            mods.add(advance().value());
        }
        return mods;
    }

    private ClassDeclarationNode parseClassDeclaration(List<String> mods) {
        advance();
        String name = expectId("Expected class name", "PARSE008");
        currentClassName = name;
        List<String> typeParams = parseTypeParameters();
        String superClass = null;
        if (check(TokenType.EXTENDS)) {
            advance();
            superClass = parseTypeRef();
        }
        List<String> ifaces = parseImplementedInterfaces();
        List<AstNode> members = new ArrayList<>();
        if (check(TokenType.LBRACE)) {
            advance();
            while (!check(TokenType.RBRACE) && !atEnd()) {
                members.add(parseClassMember());
            }
            expect(TokenType.RBRACE, "Expected '}' after class body", "PARSE009");
        }
        return new ClassDeclarationNode(pos(), name, mods, superClass, ifaces, typeParams, List.copyOf(members));
    }

    private InterfaceDeclarationNode parseInterfaceDeclaration(List<String> mods) {
        advance();
        String name = expectId("Expected interface name", "PARSE010");
        List<String> ifaces = new ArrayList<>();
        if (check(TokenType.EXTENDS)) {
            advance();
            ifaces.add(parseTypeRef());
            while (check(TokenType.COMMA)) {
                advance();
                ifaces.add(parseTypeRef());
            }
        }
        List<AstNode> members = new ArrayList<>();
        if (check(TokenType.LBRACE)) {
            advance();
            while (!check(TokenType.RBRACE) && !atEnd()) {
                members.add(parseClassMember());
            }
            expect(TokenType.RBRACE, "Expected '}' after interface body", "PARSE011");
        }
        return new InterfaceDeclarationNode(pos(), name, mods, ifaces, List.copyOf(members));
    }

    private RecordDeclarationNode parseRecordDeclaration(List<String> mods) {
        advance();
        String name = expectId("Expected record name", "PARSE012");
        List<RecordComponentNode> components = new ArrayList<>();
        if (check(TokenType.LPAREN)) {
            advance();
            if (!check(TokenType.RPAREN)) {
                components.add(parseRecordComponent());
                while (check(TokenType.COMMA)) {
                    advance();
                    components.add(parseRecordComponent());
                }
            }
            expect(TokenType.RPAREN, "Expected ')' after record components", "PARSE013");
        }
        String superClass = null;
        if (check(TokenType.EXTENDS)) {
            advance();
            superClass = parseTypeRef();
        }
        List<String> ifaces = parseImplementedInterfaces();
        List<AstNode> members = new ArrayList<>();
        if (check(TokenType.LBRACE)) {
            advance();
            while (!check(TokenType.RBRACE) && !atEnd()) {
                members.add(parseClassMember());
            }
            expect(TokenType.RBRACE, "Expected '}' after record body", "PARSE014");
        }
        return new RecordDeclarationNode(pos(), name, mods, superClass, ifaces,
                List.copyOf(components), List.copyOf(members));
    }

    private List<String> parseImplementedInterfaces() {
        List<String> ifaces = new ArrayList<>();
        if (check(TokenType.IMPLEMENTS)) {
            advance();
            ifaces.add(parseTypeRef());
            while (check(TokenType.COMMA)) {
                advance();
                ifaces.add(parseTypeRef());
            }
        }
        return ifaces;
    }

    private RecordComponentNode parseRecordComponent() {
        List<String> mods = parseModifiers();
        String type = parseTypeRef();
        String name = expectId("Expected component name", "PARSE015");
        return new RecordComponentNode(pos(), mods, type, name);
    }

    private AstNode parseClassMember() {
        List<String> mods = parseModifiers();
        if (check(TokenType.IDENTIFIER) && peek().value().equals("constructor") && checkNext(TokenType.LPAREN)) {
            return parseConstructor(mods);
        }
        if (check(TokenType.IDENTIFIER) && checkNext(TokenType.LPAREN)) {
            String name = advance().value();
            expect(TokenType.LPAREN, "Expected '('", "PARSE011");
            List<FormalParameterNode> params = new ArrayList<>();
            if (!check(TokenType.RPAREN)) {
                params.add(parseFormalParameter());
                while (check(TokenType.COMMA)) { advance(); params.add(parseFormalParameter()); }
            }
            expect(TokenType.RPAREN, "Expected ')'", "PARSE012");
            String returnType = "void";
            if (check(TokenType.COLON)) {
                advance();
                returnType = parseTypeRef();
            }
            List<String> thrown = parseThrows();
            if (check(TokenType.LBRACE)) {
                List<StatementNode> body = parseBlock();
                return new MethodDeclarationNode(pos(), mods, returnType, name, params, thrown, body);
            }
            if (check(TokenType.EQUAL)) {
                advance();
                ExpressionNode expr = parseExpression();
                if (check(TokenType.SEMICOLON)) advance();
                return new MethodDeclarationNode(pos(), mods, returnType, name, params, thrown, List.of(new ReturnStmt(pos(), expr)));
            }
            expectSemicolon();
            return new MethodDeclarationNode(pos(), mods, returnType, name, params, thrown, List.of());
        }
        if (check(TokenType.IDENTIFIER, TokenType.BOOL_TYPE, TokenType.BYTE_TYPE, TokenType.SHORT_TYPE,
                TokenType.INT_TYPE, TokenType.LONG_TYPE, TokenType.FLOAT_TYPE, TokenType.DOUBLE_TYPE,
                TokenType.CHAR_TYPE, TokenType.STRING_TYPE, TokenType.VOID)) {
            Token nameTok = peek();
            Token next = pos + 1 < tokens.size() ? tokens.get(pos + 1) : peek();
            Token afterNext = pos + 2 < tokens.size() ? tokens.get(pos + 2) : peek();
            if (next.is(TokenType.IDENTIFIER) && afterNext.is(TokenType.LPAREN)) {
                advance();
                return parseMethod(mods, nameTok.value());
            }
            return parseField(mods);
        }
        if (check(TokenType.CLASS, TokenType.INTERFACE, TokenType.RECORD)) {
            return parseTypeDeclaration();
        }
        if (check(TokenType.LBRACE)) {
            return parseConstructor(mods);
        }
        error("Unexpected token in class body", "PARSE016");
        advance();
        return new FieldDeclarationNode(pos(), mods, "Object", "error", null);
    }

    private AstNode parseField(List<String> mods) {
        String type = parseTypeRef();
        String name = expectId("Expected field name", "PARSE017");
        ExpressionNode init = null;
        if (check(TokenType.EQUAL)) {
            advance();
            init = parseExpression();
        }
        expectSemicolon();
        return new FieldDeclarationNode(pos(), mods, type, name, init);
    }

    private AstNode parseMethod(List<String> mods, String returnType) {
        String name = advance().value();
        List<FormalParameterNode> params = parseFormalParameters();
        if (check(TokenType.COLON)) {
            advance();
            returnType = parseTypeRef();
        }
        List<String> thrown = parseThrows();
        if (check(TokenType.LBRACE)) {
            List<StatementNode> body = parseBlock();
            return new MethodDeclarationNode(pos(), mods, returnType, name, params, thrown, body);
        }
        if (check(TokenType.EQUAL)) {
            advance();
            ExpressionNode expr = parseExpression();
            if (check(TokenType.SEMICOLON)) advance();
            return new MethodDeclarationNode(pos(), mods, returnType, name, params, thrown, List.of(new ReturnStmt(pos(), expr)));
        }
        expectSemicolon();
        return new MethodDeclarationNode(pos(), mods, returnType, name, params, thrown, List.of());
    }

    private ConstructorDeclarationNode parseConstructor(List<String> mods) {
        String name;
        if (check(TokenType.IDENTIFIER) && peek().value().equals("constructor")) {
            advance();
            name = currentClassName != null ? currentClassName : "error";
        } else if (check(TokenType.IDENTIFIER)) {
            name = advance().value();
        } else {
            name = "error";
            error("Expected constructor name", "PARSE020");
        }
        List<FormalParameterNode> params = parseFormalParameters();
        List<String> thrown = parseThrows();
        List<StatementNode> body = List.of();
        if (check(TokenType.LBRACE)) {
            body = parseBlock();
        }
        return new ConstructorDeclarationNode(pos(), mods, name, params, thrown, body);
    }

    private List<FormalParameterNode> parseFormalParameters() {
        expect(TokenType.LPAREN, "Expected '('", "PARSE021");
        List<FormalParameterNode> params = new ArrayList<>();
        if (!check(TokenType.RPAREN)) {
            params.add(parseFormalParameter());
            while (check(TokenType.COMMA)) {
                advance();
                params.add(parseFormalParameter());
            }
        }
        expect(TokenType.RPAREN, "Expected ')'", "PARSE022");
        return params;
    }

    private FormalParameterNode parseFormalParameter() {
        List<String> mods = parseModifiers();
        String type = parseTypeRef();
        String name = expectId("Expected parameter name", "PARSE023");
        return new FormalParameterNode(pos(), mods, type, name);
    }

    private List<String> parseThrows() {
        List<String> thrown = new ArrayList<>();
        if (check(TokenType.THROW)) {
            advance();
            thrown.add(parseTypeRef());
            while (check(TokenType.COMMA)) {
                advance();
                thrown.add(parseTypeRef());
            }
        }
        return thrown;
    }

    private List<StatementNode> parseBlock() {
        expect(TokenType.LBRACE, "Expected '{'", "PARSE024");
        List<StatementNode> stmts = new ArrayList<>();
        while (!check(TokenType.RBRACE) && !atEnd()) {
            stmts.add(parseStatement());
        }
        expect(TokenType.RBRACE, "Expected '}'", "PARSE025");
        return stmts;
    }

    private StatementNode parseStatement() {
        if (check(TokenType.LBRACE)) {
            return new BlockStmt(pos(), parseBlock());
        }
        if (check(TokenType.RETURN)) {
            return parseReturn();
        }
        if (check(TokenType.IF)) {
            return parseIfStatement();
        }
        if (check(TokenType.WHILE)) {
            return parseWhileStatement();
        }
        if (check(TokenType.DO)) {
            return parseDoWhileStatement();
        }
        if (check(TokenType.FOR)) {
            return parseForStatement();
        }
        if (check(TokenType.THROW)) {
            return parseThrowStatement();
        }
        if (check(TokenType.TRY)) {
            return parseTryStatement();
        }
        if (check(TokenType.SWITCH)) {
            return parseSwitchStatement();
        }
        if (check(TokenType.BREAK)) {
            SourcePosition p = pos();
            advance();
            expectSemicolon();
            return new BreakStmt(p);
        }
        if (check(TokenType.CONTINUE)) {
            SourcePosition p = pos();
            advance();
            expectSemicolon();
            return new ContinueStmt(p);
        }
        if (check(TokenType.VAR, TokenType.VAL)) {
            return parseVarDecl();
        }
        if (check(TokenType.INT_TYPE, TokenType.LONG_TYPE, TokenType.FLOAT_TYPE, TokenType.DOUBLE_TYPE,
                TokenType.BOOL_TYPE, TokenType.BYTE_TYPE, TokenType.SHORT_TYPE, TokenType.CHAR_TYPE,
                TokenType.STRING_TYPE)) {
            if (checkNext(TokenType.IDENTIFIER)) {
                return parseVarDecl();
            }
        }
        if (check(TokenType.IDENTIFIER) && checkNext(TokenType.IDENTIFIER)
                && pos + 2 < tokens.size() && tokens.get(pos + 2).is(TokenType.EQUAL)) {
            return parseVarDecl();
        }
        if (check(TokenType.SEMICOLON)) {
            advance();
            return new ExpressionStmt(pos(), null);
        }
        ExpressionNode expr = parseExpression();
        expectSemicolon();
        return new ExpressionStmt(pos(), expr);
    }

    private StatementNode parseReturn() {
        SourcePosition p = pos();
        advance();
        if (check(TokenType.SEMICOLON)) {
            advance();
            return new ReturnStmt(p, null);
        }
        ExpressionNode value = parseExpression();
        expectSemicolon();
        return new ReturnStmt(p, value);
    }

    private StatementNode parseIfStatement() {
        SourcePosition p = pos();
        advance();
        expect(TokenType.LPAREN, "Expected '(' after 'if'", "PARSE028");
        ExpressionNode cond = parseExpression();
        expect(TokenType.RPAREN, "Expected ')'", "PARSE029");
        StatementNode thenB = parseStatement();
        StatementNode elseB = null;
        if (check(TokenType.ELSE)) {
            advance();
            elseB = parseStatement();
        }
        return new IfStmt(p, cond, thenB, elseB);
    }

    private StatementNode parseWhileStatement() {
        SourcePosition p = pos();
        advance();
        expect(TokenType.LPAREN, "Expected '(' after 'while'", "PARSE030");
        ExpressionNode cond = parseExpression();
        expect(TokenType.RPAREN, "Expected ')'", "PARSE031");
        StatementNode body = parseStatement();
        return new WhileStmt(p, cond, body);
    }

    private StatementNode parseDoWhileStatement() {
        SourcePosition p = pos();
        advance();
        StatementNode body = parseStatement();
        expect(TokenType.WHILE, "Expected 'while' after 'do'", "PARSE060");
        expect(TokenType.LPAREN, "Expected '(' after 'while'", "PARSE061");
        ExpressionNode cond = parseExpression();
        expect(TokenType.RPAREN, "Expected ')'", "PARSE062");
        expectSemicolon();
        return new DoWhileStmt(p, cond, body);
    }

    private StatementNode parseForStatement() {
        SourcePosition p = pos();
        advance();
        expect(TokenType.LPAREN, "Expected '(' after 'for'", "PARSE032");
        StatementNode init;
        if (check(TokenType.SEMICOLON)) {
            advance();
            init = new ExpressionStmt(p, null);
        } else if (check(TokenType.VAR, TokenType.VAL) ||
                (isPrimitiveType() && checkNext(TokenType.IDENTIFIER))) {
            init = parseVarDecl();
        } else {
            ExpressionNode initExpr = parseExpression();
            expectSemicolon();
            init = new ExpressionStmt(p, initExpr);
        }
        ExpressionNode cond = null;
        if (!check(TokenType.SEMICOLON)) {
            cond = parseExpression();
        }
        expectSemicolon();
        ExpressionNode update = null;
        if (!check(TokenType.RPAREN)) {
            update = parseExpression();
        }
        expect(TokenType.RPAREN, "Expected ')'", "PARSE035");
        StatementNode body = parseStatement();
        return new ForStmt(p, init, cond, update, body);
    }

    private StatementNode parseThrowStatement() {
        SourcePosition p = pos();
        advance();
        ExpressionNode expr = parseExpression();
        expectSemicolon();
        return new ThrowStmt(p, expr);
    }

    private StatementNode parseTryStatement() {
        SourcePosition p = pos();
        advance();
        List<StatementNode> tryBody = parseBlock();
        List<CatchClause> catchClauses = new ArrayList<>();
        while (check(TokenType.CATCH)) {
            SourcePosition cp = pos();
            advance();
            expect(TokenType.LPAREN, "Expected '('", "PARSE050");
            String excType = parseTypeRef();
            String excName = expectId("Expected exception name", "PARSE051");
            expect(TokenType.RPAREN, "Expected ')'", "PARSE052");
            List<StatementNode> catchBody = parseBlock();
            catchClauses.add(new CatchClause(cp, excType, excName, catchBody));
        }
        List<StatementNode> finallyBody = List.of();
        if (check(TokenType.FINALLY)) {
            advance();
            finallyBody = parseBlock();
        }
        return new TryStmt(p, tryBody, catchClauses, finallyBody);
    }

    private StatementNode parseSwitchStatement() {
        SourcePosition p = pos();
        advance();
        expect(TokenType.LPAREN, "Expected '(' after 'switch'", "PARSE070");
        ExpressionNode expr = parseExpression();
        expect(TokenType.RPAREN, "Expected ')'", "PARSE071");
        expect(TokenType.LBRACE, "Expected '{'", "PARSE072");
        List<SwitchCase> cases = new ArrayList<>();
        List<StatementNode> defaultBody = List.of();
        while (!check(TokenType.RBRACE) && !atEnd()) {
            if (check(TokenType.CASE)) {
                SourcePosition cp = pos();
                advance();
                ExpressionNode value = parseExpression();
                expect(TokenType.COLON, "Expected ':'", "PARSE073");
                List<StatementNode> caseBody = new ArrayList<>();
                while (!check(TokenType.CASE) && !check(TokenType.DEFAULT) && !check(TokenType.RBRACE) && !atEnd()) {
                    caseBody.add(parseStatement());
                }
                cases.add(new SwitchCase(cp, value, caseBody));
            } else if (check(TokenType.DEFAULT)) {
                advance();
                expect(TokenType.COLON, "Expected ':'", "PARSE074");
                defaultBody = new ArrayList<>();
                while (!check(TokenType.CASE) && !check(TokenType.DEFAULT) && !check(TokenType.RBRACE) && !atEnd()) {
                    defaultBody.add(parseStatement());
                }
            } else {
                advance();
            }
        }
        expect(TokenType.RBRACE, "Expected '}'", "PARSE075");
        return new SwitchStmt(p, expr, cases, defaultBody);
    }

    private StatementNode parseVarDecl() {
        SourcePosition p = pos();
        String type = "var";
        if (check(TokenType.VAR, TokenType.VAL)) {
            advance();
        } else {
            type = parseTypeRef();
        }
        String name = expectId("Expected variable name", "PARSE037");
        ExpressionNode init = null;
        if (check(TokenType.EQUAL)) {
            advance();
            init = parseExpression();
        }
        expectSemicolon();
        return new VarDeclStmt(p, type, name, init);
    }

    private ExpressionNode parseExpression() {
        return parseAssignment();
    }

    private ExpressionNode parseAssignment() {
        ExpressionNode left = parseBinary(0);
        if (check(TokenType.EQUAL, TokenType.PLUS_EQUAL, TokenType.MINUS_EQUAL,
                TokenType.STAR_EQUAL, TokenType.SLASH_EQUAL, TokenType.PERCENT_EQUAL,
                TokenType.AMP_EQUAL, TokenType.PIPE_EQUAL, TokenType.CARET_EQUAL,
                TokenType.LESS_LESS_EQUAL, TokenType.GREATER_GREATER_EQUAL,
                TokenType.GREATER_GREATER_GREATER_EQUAL)) {
            String op = advance().value();
            ExpressionNode right = parseAssignment();
            return new AssignmentExpr(pos(), left, op, right);
        }
        return left;
    }

    private ExpressionNode parseBinary(int minPrec) {
        ExpressionNode left = parseUnary();
        while (isBinaryOp() && precedence(peek().value()) >= minPrec) {
            String op = advance().value();
            int prec = precedence(op);
            ExpressionNode right = parseBinary(prec + 1);
            left = new BinaryExpr(pos(), op, left, right);
        }
        return left;
    }

    private int precedence(String op) {
        return switch (op) {
            case "||" -> 1;
            case "&&" -> 2;
            case "|", "^" -> 3;
            case "&" -> 4;
            case "==", "!=", "<", "<=", ">", ">=", "instanceof", "as" -> 5;
            case "<<", ">>", ">>>" -> 6;
            case "+", "-" -> 7;
            case "*", "/", "%" -> 8;
            default -> -1;
        };
    }

    private boolean isBinaryOp() {
        return switch (peek().type()) {
            case PLUS, MINUS, STAR, SLASH, PERCENT,
                 EQUAL_EQUAL, BANG_EQUAL, LESS, LESS_EQUAL, GREATER, GREATER_EQUAL,
                 AMP_AMP, PIPE_PIPE, AMP, PIPE, CARET,
                 LESS_LESS, GREATER_GREATER, GREATER_GREATER_GREATER,
                 INSTANCEOF, AS -> true;
            default -> false;
        };
    }

    private ExpressionNode parseUnary() {
        if (check(TokenType.BANG)) {
            SourcePosition p = pos();
            advance();
            ExpressionNode operand = parseUnary();
            return new UnaryExpr(p, "!", operand, true);
        }
        if (check(TokenType.MINUS)) {
            SourcePosition p = pos();
            advance();
            ExpressionNode operand = parseUnary();
            return new UnaryExpr(p, "-", operand, true);
        }
        if (check(TokenType.PLUS_PLUS)) {
            SourcePosition p = pos();
            advance();
            ExpressionNode operand = parseUnary();
            return new UnaryExpr(p, "++", operand, true);
        }
        if (check(TokenType.MINUS_MINUS)) {
            SourcePosition p = pos();
            advance();
            ExpressionNode operand = parseUnary();
            return new UnaryExpr(p, "--", operand, true);
        }
        return parsePostfix();
    }

    private ExpressionNode parsePostfix() {
        ExpressionNode expr = parsePrimary();
        while (true) {
            if (check(TokenType.DOT)) {
                advance();
                String field = expectId("Expected field name", "PARSE039");
                expr = new FieldAccessExpr(pos(), expr, field);
            } else if (check(TokenType.LBRACKET)) {
                SourcePosition p = pos();
                advance();
                ExpressionNode index = parseExpression();
                expect(TokenType.RBRACKET, "Expected ']'", "PARSE045");
                expr = new ArrayAccessExpr(p, expr, index);
            } else if (check(TokenType.LPAREN)) {
                List<ExpressionNode> args = parseArguments();
                if (expr instanceof IdentifierExpr ie) {
                    expr = new MethodCallExpr(pos(), null, ie.name(), List.of(), args);
                } else if (expr instanceof FieldAccessExpr fa) {
                    expr = new MethodCallExpr(pos(), fa.receiver(), fa.fieldName(), List.of(), args);
                } else {
                    expr = new MethodCallExpr(pos(), expr, "", List.of(), args);
                }
            } else if (check(TokenType.LESS) && (expr instanceof IdentifierExpr || expr instanceof FieldAccessExpr)
                    && looksLikeGenericCall()) {
                List<String> typeArgs = parseCallTypeArguments();
                List<ExpressionNode> args = parseArguments();
                if (expr instanceof IdentifierExpr ie3) {
                    expr = new MethodCallExpr(pos(), null, ie3.name(), typeArgs, args);
                } else if (expr instanceof FieldAccessExpr fa2) {
                    expr = new MethodCallExpr(pos(), fa2.receiver(), fa2.fieldName(), typeArgs, args);
                }
            } else if (check(TokenType.PLUS_PLUS)) {
                advance();
                expr = new UnaryExpr(pos(), "++", expr, false);
            } else if (check(TokenType.MINUS_MINUS)) {
                advance();
                expr = new UnaryExpr(pos(), "--", expr, false);
            } else {
                break;
            }
        }
        return expr;
    }

    private ExpressionNode parsePrimary() {
        if (check(TokenType.INT_LITERAL, TokenType.LONG_LITERAL, TokenType.FLOAT_LITERAL,
                TokenType.DOUBLE_LITERAL, TokenType.STRING_LITERAL, TokenType.CHAR_LITERAL,
                TokenType.BOOLEAN_LITERAL, TokenType.NULL_LITERAL)) {
            Token t = advance();
            LiteralKind kind = switch (t.type()) {
                case INT_LITERAL -> ConcreteLiteralKind.INT;
                case LONG_LITERAL -> ConcreteLiteralKind.LONG;
                case FLOAT_LITERAL -> ConcreteLiteralKind.FLOAT;
                case DOUBLE_LITERAL -> ConcreteLiteralKind.DOUBLE;
                case STRING_LITERAL -> ConcreteLiteralKind.STRING;
                case CHAR_LITERAL -> ConcreteLiteralKind.CHAR;
                case BOOLEAN_LITERAL -> ConcreteLiteralKind.BOOLEAN;
                case NULL_LITERAL -> ConcreteLiteralKind.NULL;
                default -> ConcreteLiteralKind.NULL;
            };
            return new LiteralExpr(pos(), kind, t.value());
        }
        if (check(TokenType.THIS)) {
            Token t = advance();
            return new IdentifierExpr(pos(), t.value());
        }
        if (check(TokenType.SUPER)) {
            Token t = advance();
            return new IdentifierExpr(pos(), t.value());
        }
        if (check(TokenType.IDENTIFIER)) {
            return new IdentifierExpr(pos(), advance().value());
        }
        if (check(TokenType.NEW)) {
            return parseNewExpression();
        }
        if (check(TokenType.LPAREN)) {
            if (looksLikeLambdaParams()) {
                List<FormalParameterNode> params = parseLambdaParams();
                expect(TokenType.ARROW, "Expected '->'", "PARSE042");
                return new LambdaExpr(pos(), params, parseLambdaBody());
            }
            advance();
            ExpressionNode expr = parseExpression();
            expect(TokenType.RPAREN, "Expected ')'", "PARSE040");
            return expr;
        }
        if (check(TokenType.IF)) {
            SourcePosition p = pos();
            advance();
            expect(TokenType.LPAREN, "Expected '(' after if", "PARSE043");
            ExpressionNode condition = parseExpression();
            expect(TokenType.RPAREN, "Expected ')'", "PARSE040");
            ExpressionNode thenExpr = parseExpression();
            expect(TokenType.ELSE, "Expected 'else'", "PARSE044");
            ExpressionNode elseExpr = parseExpression();
            return new IfExpr(p, condition, thenExpr, elseExpr);
        }
        if (check(TokenType.LBRACE)) {
            List<FormalParameterNode> params = new ArrayList<>();
            List<StatementNode> body = parseBlock();
            return new LambdaExpr(pos(), params, body);
        }
        error("Unexpected token in expression: " + peek().value(), "PARSE041");
        advance();
        return new IdentifierExpr(pos(), "error");
    }

    private boolean looksLikeLambdaParams() {
        int i = pos + 1;
        int depth = 0;
        while (i < tokens.size()) {
            TokenType t = tokens.get(i).type();
            if (t == TokenType.LPAREN) {
                depth++;
            } else if (t == TokenType.RPAREN) {
                if (depth == 0) {
                    return i + 1 < tokens.size() && tokens.get(i + 1).type() == TokenType.ARROW;
                }
                depth--;
            } else if (t == TokenType.ARROW) {
                return false;
            }
            i++;
        }
        return false;
    }

    private List<FormalParameterNode> parseLambdaParams() {
        List<FormalParameterNode> params = new ArrayList<>();
        expect(TokenType.LPAREN, "Expected '('", "PARSE011");
        if (!check(TokenType.RPAREN)) {
            params.add(parseLambdaParameter());
            while (check(TokenType.COMMA)) {
                advance();
                params.add(parseLambdaParameter());
            }
        }
        expect(TokenType.RPAREN, "Expected ')'", "PARSE012");
        return params;
    }

    private FormalParameterNode parseLambdaParameter() {
        SourcePosition p = pos();
        String name = expectId("Expected parameter name", "PARSE010");
        String type = "Object";
        if (check(TokenType.COLON)) {
            advance();
            type = parseTypeRef();
        }
        return new FormalParameterNode(p, List.of(), type, name);
    }

    private List<StatementNode> parseLambdaBody() {
        if (check(TokenType.LBRACE)) {
            return parseBlock();
        }
        ExpressionNode expr = parseExpression();
        if (check(TokenType.SEMICOLON)) advance();
        return List.of(new ReturnStmt(pos(), expr));
    }

    private ExpressionNode parseNewExpression() {
        SourcePosition p = pos();
        advance();
        String typeName = parseNewTypeRef();
        List<String> typeArgs = List.of();
        if (check(TokenType.LESS)) {
            advance();
            typeArgs = new ArrayList<>();
            while (!check(TokenType.GREATER) && !atEnd()) {
                if (check(TokenType.IDENTIFIER) || isPrimitiveType()) {
                    typeArgs.add(parseTypeRef());
                } else {
                    advance();
                }
                if (check(TokenType.COMMA)) advance();
            }
            expect(TokenType.GREATER, "Expected '>' after type arguments", "PARSE076");
        }
        if (check(TokenType.LBRACKET)) {
            advance();
            ExpressionNode size = parseExpression();
            expect(TokenType.RBRACKET, "Expected ']'", "PARSE046");
            return new NewArrayExpr(p, typeName, size);
        }
        List<ExpressionNode> args = parseArguments();
        return new NewExpr(p, typeName, typeArgs, args);
    }

    private String parseNewTypeRef() {
        if (check(TokenType.VOID)) {
            advance();
            return "void";
        }
        if (isPrimitiveType()) {
            return advance().value();
        }
        if (check(TokenType.IDENTIFIER)) {
            String name = peek().value();
            if (PRIMITIVE_TYPE_NAMES.contains(name.toLowerCase()) || PRIMITIVE_TYPE_NAMES.contains(name)) {
                advance();
                return name;
            }
            StringBuilder type = new StringBuilder();
            type.append(advance().value());
            while (check(TokenType.DOT) && checkNext(TokenType.IDENTIFIER)) {
                advance();
                type.append('.').append(advance().value());
            }
            return type.toString();
        }
        error("Expected type", "PARSE044");
        return "Object";
    }

    /**
     * Disambiguation: a '<' after an identifier may be a less-than comparison or
     * the start of call type arguments (e.g. listOf<Int>(...)). We only treat it
     * as generic call when the token stream between '<' and the matching '>'
     * consists solely of type-like tokens (identifiers, primitives, '.', ',')
     * and the '>' is immediately followed by '('.
     */
    private boolean looksLikeGenericCall() {
        if (!check(TokenType.LESS)) return false;
        int i = pos + 1;
        int depth = 1;
        while (i < tokens.size()) {
            Token t = tokens.get(i);
            switch (t.type()) {
                case LESS -> depth++;
                case GREATER -> {
                    depth--;
                    if (depth == 0) return i + 1 < tokens.size() && tokens.get(i + 1).type() == TokenType.LPAREN;
                }
                case GREATER_GREATER -> {
                    depth -= 2;
                    if (depth == 0) return i + 1 < tokens.size() && tokens.get(i + 1).type() == TokenType.LPAREN;
                }
                case GREATER_GREATER_GREATER -> {
                    depth -= 3;
                    if (depth == 0) return i + 1 < tokens.size() && tokens.get(i + 1).type() == TokenType.LPAREN;
                }
                case IDENTIFIER, INT_TYPE, LONG_TYPE, FLOAT_TYPE, DOUBLE_TYPE, BOOL_TYPE,
                        BYTE_TYPE, SHORT_TYPE, CHAR_TYPE, STRING_TYPE, DOT, COMMA -> { }
                default -> {
                    return false;
                }
            }
            i++;
        }
        return false;
    }

    private List<String> parseCallTypeArguments() {
        List<String> typeArgs = new ArrayList<>();
        splitShiftRight();
        expect(TokenType.LESS, "Expected '<'", "PARSE078");
        while (!check(TokenType.GREATER) && !atEnd()) {
            splitShiftRight();
            if (check(TokenType.IDENTIFIER) || isPrimitiveType()) {
                String typeRef = parseTypeRef();
                while (check(TokenType.LBRACKET) && checkNext(TokenType.RBRACKET)) {
                    advance();
                    advance();
                    typeRef += "[]";
                }
                typeArgs.add(typeRef);
            } else {
                advance();
            }
            if (check(TokenType.COMMA)) advance();
        }
        splitShiftRight();
        expect(TokenType.GREATER, "Expected '>'", "PARSE079");
        return typeArgs;
    }

    private void splitShiftRight() {
        Token cur = tokens.get(pos);
        if (cur.type() == TokenType.GREATER_GREATER) {
            tokens.set(pos, new Token(TokenType.GREATER, ">", cur.file(), cur.line(), cur.column(), cur.offset(), 1));
            tokens.add(pos + 1, new Token(TokenType.GREATER, ">", cur.file(), cur.line(), cur.column() + 1, cur.offset() + 1, 1));
        } else if (cur.type() == TokenType.GREATER_GREATER_GREATER) {
            tokens.set(pos, new Token(TokenType.GREATER, ">", cur.file(), cur.line(), cur.column(), cur.offset(), 1));
            tokens.add(pos + 1, new Token(TokenType.GREATER, ">", cur.file(), cur.line(), cur.column() + 1, cur.offset() + 1, 1));
            tokens.add(pos + 2, new Token(TokenType.GREATER, ">", cur.file(), cur.line(), cur.column() + 2, cur.offset() + 2, 1));
        }
    }

    private boolean isPrimitiveTypeAtNext() {
        if (pos + 1 >= tokens.size()) return false;
        Token n = tokens.get(pos + 1);
        return switch (n.type()) {
            case INT_TYPE, LONG_TYPE, FLOAT_TYPE, DOUBLE_TYPE, BOOL_TYPE, BYTE_TYPE,
                    SHORT_TYPE, CHAR_TYPE, STRING_TYPE -> true;
            default -> false;
        };
    }

    private List<ExpressionNode> parseArguments() {
        expect(TokenType.LPAREN, "Expected '('", "PARSE042");
        List<ExpressionNode> args = new ArrayList<>();
        if (!check(TokenType.RPAREN)) {
            args.add(parseExpression());
            while (check(TokenType.COMMA)) {
                advance();
                args.add(parseExpression());
            }
        }
        expect(TokenType.RPAREN, "Expected ')'", "PARSE043");
        return args;
    }

    private String parseTypeRef() {
        StringBuilder type = new StringBuilder();
        if (check(TokenType.VOID)) {
            advance();
            return "void";
        }
        if (isPrimitiveType()) {
            return advance().value();
        }
        if (check(TokenType.IDENTIFIER)) {
            type.append(advance().value());
            while (check(TokenType.DOT) && checkNext(TokenType.IDENTIFIER)) {
                advance();
                type.append('.').append(advance().value());
            }
        } else {
            error("Expected type", "PARSE044");
            return "Object";
        }
        if (check(TokenType.LESS)) {
            StringBuilder args = new StringBuilder("<");
            int depth = 0;
            boolean first = true;
            do {
                splitShiftRight();
                boolean isClose = check(TokenType.GREATER);
                if (check(TokenType.LESS)) depth++;
                else if (isClose) depth--;
                if (!first && !isClose) args.append(tokens.get(pos).value());
                else if (!first && isClose && depth > 0) args.append(tokens.get(pos).value());
                first = false;
                advance();
            } while (depth > 0 && !atEnd());
            args.append(">");
            type.append(args);
        }
        while (check(TokenType.LBRACKET)) {
            advance();
            expect(TokenType.RBRACKET, "Expected ']'", "PARSE045");
            type.append("[]");
        }
        return type.toString();
    }

    private boolean isPrimitiveType() {
        return check(TokenType.INT_TYPE, TokenType.LONG_TYPE, TokenType.FLOAT_TYPE,
                TokenType.DOUBLE_TYPE, TokenType.BOOL_TYPE, TokenType.BYTE_TYPE,
                TokenType.SHORT_TYPE, TokenType.CHAR_TYPE, TokenType.STRING_TYPE);
    }

    private boolean check(TokenType... types) {
        if (atEnd()) return false;
        TokenType cur = peek().type();
        for (TokenType t : types) {
            if (cur == t) return true;
        }
        return false;
    }

    private boolean checkNext(TokenType type) {
        int next = pos + 1;
        return next < tokens.size() && tokens.get(next).type() == type;
    }

    private boolean atEnd() {
        return pos >= tokens.size() || peek().type() == TokenType.EOF;
    }

    private Token peek() {
        return tokens.get(Math.min(pos, tokens.size() - 1));
    }

    private Token advance() {
        if (!atEnd()) pos++;
        return tokens.get(pos - 1);
    }

    private Token expect(TokenType type, String message, String code) {
        if (check(type)) return advance();
        diagnostics.error(file, peek().line(), peek().column(), peek().length(), message, code);
        return peek();
    }

    private void expectSemicolon() {
        if (check(TokenType.SEMICOLON)) {
            advance();
        }
    }

    private String expectId(String message, String code) {
        if (check(TokenType.IDENTIFIER)) return advance().value();
        diagnostics.error(file, peek().line(), peek().column(), peek().length(), message, code);
        return "error";
    }

    private SourcePosition pos() {
        Token t = peek();
        return new SourcePosition(file, t.line(), t.column(), t.offset(), t.length());
    }

    private void error(String message, String code) {
        diagnostics.error(file, peek().line(), peek().column(), peek().length(), message, code);
    }
}
