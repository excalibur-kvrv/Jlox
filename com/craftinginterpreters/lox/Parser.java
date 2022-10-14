package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static com.craftinginterpreters.lox.TokenType.*;

class Parser {
  /*
   * Lox Grammer
   * program -> declaration* EOF
   * declaration -> classDecl 
   *              | varDecl 
   *              | statement 
   *              | funDecl
   * classDecl -> "class" IDENTIFIER "{" function* "}"
   * funDecl -> "fun" function
   * function -> IDENTIFIER "(" parameters? ")" block
   * parameters -> IDENTIFIER ( "," IDENTIFIER )*
   * varDecl -> "var" IDENTIFIER ( "=" expression)? ";"
   * statement -> exprStmt 
   *            | forStmt 
   *            | ifStmt 
   *            | printStmt 
   *            | returnStmt 
   *            | whileStmt 
   *            | block
   * returnStmt -> "return" expression? ";"
   * whileStmt -> "while" "(" expression ")" statement
   * forStmt -> "for" "(" (varDecl | exprStmt | ";") expression? ";" expression? ")" statement
   * ifStmt -> "if" "(" expression ")" statement ( "else" statement )? 
   * block -> "{" declaration* "}"
   * exprStmt -> expression ";"
   * printStmt -> "print" expression ";"
   * expression -> assignment
   * assignment -> ( call "." )? IDENTIFIER "=" assignment | logic_or
   * logic_or -> logic_and ( "or" logic_and )*
   * logic_and -> equality ( "and" equality )*
   * equality -> comparison (( "!=" | "==" ) comparison)*
   * comparison -> term (( ">" | ">=" | "<" | "<=") term)*
   * term -> factor (("-" | "+") factor)*
   * factor -> unary (("/" | "*") unary)*
   * unary -> ("!"|"-") unary | call
   * call -> primary ( "(" arguments? ")" | "." IDENTIFIER )*
   * arguments -> expression ( "," expression )*
   * primary -> NUMBER 
   *          | STRING 
   *          | "true" 
   *          | "false" 
   *          | "nil" 
   *          | "(" expression ")" 
   *          | IDENTIFIER
   */

  private static class ParserError extends RuntimeException {}
  private final List<Token> tokens;  
  private int current = 0;

  Parser(List<Token> tokens) {
    this.tokens = tokens;
  }

  private Expr expression() {
    return assignment();
  }

  private Expr assignment() {
    Expr expr = or();

    if (match(TokenType.EQUAL)) {
      Token equals = previous();
      Expr value = assignment();

      if (expr instanceof Expr.Variable) {
        Token name = ((Expr.Variable)expr).name;
        return new Expr.Assign(name, value);
      } else if (expr instanceof Expr.Get) {
        Expr.Get get = (Expr.Get) expr;
        return new Expr.Set(get.object, get.name, value);
      }

      error(equals, "Invalid assignment targte.");
    }

    return expr;
  }

  private Expr or() {
    Expr expr = and();

    while (match(TokenType.OR)) {
      Token operator = previous();
      Expr right = and();
      expr = new Expr.Logical(expr, operator, right);
    }

    return expr;
  }

  private Expr and() {
    Expr expr = equality();

    while(match(TokenType.AND)) {
      Token operator = previous();
      Expr right = equality();
      expr = new Expr.Logical(expr, operator, right);
    }

    return expr;
  }

  private Expr equality() {
    Expr expr = comparison();
    while (match(TokenType.BANG_EQUAL, TokenType.EQUAL_EQUAL)) {
      Token operator = previous();
      Expr right = comparison();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private boolean match(TokenType... types) {
    for (TokenType type: types) {
      if (check(type)) {
        advance();
        return true;
      }
    }
    return false;
  }

  private boolean check(TokenType type) {
    if (isAtEnd()) return false;
    return peek().type == type;
  }

  private Token advance() {
    if (!isAtEnd()) current++;
    return previous();
  }

  private boolean isAtEnd() {
    return peek().type == TokenType.EOF;
  }

  private Token peek() {
    return tokens.get(current);
  }

  private Token previous() {
    return tokens.get(current - 1);
  }

  private Expr comparison() {
    Expr expr = term();

    while(match(TokenType.GREATER, TokenType.GREATER_EQUAL, TokenType.LESS, TokenType.LESS_EQUAL)) {
      Token operator = previous();
      Expr right = term();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private Expr term() {
    Expr expr = factor();

    while (match(TokenType.MINUS, TokenType.PLUS)) {
      Token operator = previous();
      Expr right = factor();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private Expr factor() {
    Expr expr = unary();

    while (match(TokenType.SLASH, TokenType.STAR)) {
      Token operator = previous();
      Expr right = unary();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private Expr unary() {
    if (match(TokenType.BANG, TokenType.MINUS)) {
      Token operator = previous();
      Expr right = unary();
      return new Expr.Unary(operator, right);
    }
    return call();
  }

  private Expr call() {
    Expr expr = primary();

    while (true) {
      if (match(TokenType.LEFT_PAREN)) {
        expr = finishCall(expr);
      } else if (match(TokenType.DOT)) {
        Token name = consume(TokenType.IDENTIFIER, "Expect property name after '.'.");
        expr = new Expr.Get(expr, name);
      } else {
        break;
      }
    }

    return expr;
  }

  private Expr finishCall(Expr callee) {
    List<Expr> arguments = new ArrayList<>();
    if (!check(TokenType.RIGHT_PAREN)) {
      do {
        if (arguments.size() >= 255) {
          error(peek(), "Can't have more than 255 arguements.");
        }
        arguments.add(expression());
      } while (match(TokenType.COMMA));
    }

    Token paren = consume(TokenType.RIGHT_PAREN, "Expect ')' after arguments.");
    return new Expr.Call(callee, paren, arguments);
  }

  private Expr primary() {
    if (match(TokenType.FALSE)) return new Expr.Literal(false);
    if (match(TokenType.TRUE)) return new Expr.Literal(true);
    if (match(TokenType.NIL)) return new Expr.Literal(null);

    if (match(TokenType.NUMBER, TokenType.STRING)) {
      return new Expr.Literal(previous().literal);
    }

    if (match(TokenType.LEFT_PAREN)) {
      Expr expr = expression();
      consume(TokenType.RIGHT_PAREN, "Expect ')' after expression.");
      return new Expr.Grouping(expr);
    }

    if (match(TokenType.IDENTIFIER)) {
      return new Expr.Variable(previous());
    }

    if (match(TokenType.THIS)) return new Expr.This(previous());

    throw error(peek(), "Expect expression.");
  }

  private Token consume(TokenType type, String message) {
    if (check(type)) return advance();
    throw error(peek(), message);
  }

  private ParserError error(Token token, String message) {
    Lox.error(token, message);
    return new ParserError();
  }

  private void synchronize() {
    advance();
    while (!isAtEnd()) {
      if (previous().type == TokenType.SEMICOLON) return;

      switch(peek().type) {
        case CLASS:
        case FOR:
        case FUN:
        case IF:
        case PRINT:
        case RETURN:
        case VAR:
        case WHILE:
        return;
        default:
          break;
      }
    }
    advance();
  }

  List<Stmt> parse() {
    List<Stmt> statements = new ArrayList<>();
    
    while (!isAtEnd()) {
      statements.add(declaration());
    }

    return statements;
  }

  private Stmt statement() {
    if (match(TokenType.FOR)) return forStatement();
    if (match(TokenType.IF)) return ifStatement();
    if (match(TokenType.PRINT)) return printStatement();
    if (match(TokenType.RETURN)) return returnStatement();
    if (match(TokenType.WHILE)) return whileStatement();
    if (match(TokenType.LEFT_BRACE)) return new Stmt.Block(block());
    return expressionStatement();
  }

  private Stmt returnStatement() {
    Token keyword = previous();
    Expr value = null;
    if (!check(TokenType.SEMICOLON)) {
      value = expression();
    }

    consume(TokenType.SEMICOLON, "Expect ';' after return value.");
    return new Stmt.Return(keyword, value);
  }

  private Stmt forStatement() {
    consume(TokenType.LEFT_PAREN, "Expect '(' after 'for'.");

    Stmt initializer;
    if (match(TokenType.SEMICOLON)) {
      initializer = null;
    } else if (match(TokenType.VAR)) {
      initializer = varDeclaration();
    } else {
      initializer = expressionStatement();
    }

    Expr condition = null;
    if (!check(SEMICOLON)) {
      condition = expression();
    }
    consume(TokenType.SEMICOLON, "Expect ';' after loop condition.");

    Expr increment = null;
    if (!check(TokenType.RIGHT_PAREN)) {
      increment = expression();
    }
    consume(TokenType.RIGHT_PAREN, "Expect ')' after for clauses.");

    Stmt body = statement();
    
    if (increment != null) {
      body = new Stmt.Block(Arrays.asList(body, new Stmt.Expression(increment)));
    }

    if (condition == null) condition = new Expr.Literal(true);
    body = new Stmt.While(condition, body);

    if (initializer != null) {
      body = new Stmt.Block(Arrays.asList(initializer, body));
    }

    return body;
  }

  private Stmt ifStatement() {
    consume(TokenType.LEFT_PAREN, "Expect '(' after 'if'.");
    Expr condition = expression();
    consume(TokenType.RIGHT_PAREN, "Expect ')' after if condition.");

    Stmt thenBranch = statement();
    Stmt elseBranch = null;
    if (match(TokenType.ELSE)) {
      elseBranch = statement();
    }

    return new Stmt.If(condition, thenBranch, elseBranch);
  }

  private Stmt whileStatement() {
    consume(TokenType.LEFT_PAREN, "Expect '(' after 'while'.");
    Expr condition = expression();
    consume(TokenType.RIGHT_PAREN, "Expect ')' after condition.");
    Stmt body = statement();
    return new Stmt.While(condition, body);
  }

  private List<Stmt> block() {
    List<Stmt> statements = new ArrayList<>();

    while (!check(TokenType.RIGHT_BRACE) && !isAtEnd()) {
      statements.add(declaration());
    }

    consume(TokenType.RIGHT_BRACE, "Expect '}' after block.");
    return statements;
  }

  private Stmt declaration() {
    try {
      if (match(TokenType.CLASS)) return classDeclaration();
      if (match(TokenType.FUN)) return function("function");
      if (match(TokenType.VAR)) return varDeclaration();
      return statement();
    } catch (ParserError error) {
      synchronize();
      return null;
    }
  }

  private Stmt classDeclaration() {
    Token name = consume(TokenType.IDENTIFIER, "Expect class name.");
    consume(TokenType.LEFT_BRACE, "Expect '{' before class body");

    List<Stmt.Function> methods = new ArrayList<>();
    while (!check(TokenType.RIGHT_BRACE) && !isAtEnd()) {
      methods.add(function("method"));
    }

    consume(TokenType.RIGHT_BRACE, "Expect '}' after class body.");
    return new Stmt.Class(name, methods);
  }

  private Stmt.Function function(String kind) {
    Token name = consume(TokenType.IDENTIFIER, "Expect " + kind + "name.");
    consume(TokenType.LEFT_PAREN, "Expect '(' after " + kind + "name.");
    List<Token> parameters = new ArrayList<>();

    if (!check(TokenType.RIGHT_PAREN)) {
      do {
        if (parameters.size() >= 255) {
          error(peek(), "Can't have more than 255 parameters.");
        }
        parameters.add(consume(TokenType.IDENTIFIER, "Expect parameter name."));
      } while (match(TokenType.COMMA));
    }

    consume(TokenType.RIGHT_PAREN, "Expect ')' after parameters.");
    consume(TokenType.LEFT_BRACE, "Expect '{' before " + kind + "body.");
    List<Stmt> body = block();
    return new Stmt.Function(name, parameters, body);
  }

  private Stmt varDeclaration() {
    Token name = consume(TokenType.IDENTIFIER, "Expect variable name.");

    Expr initializer = null;
    if (match(TokenType.EQUAL)) {
      initializer = expression();
    }

    consume(SEMICOLON, "Expect ';' after variable declaration.");
    return new Stmt.Var(name, initializer);
  }

  private Stmt printStatement() {
    Expr value = expression();
    consume(TokenType.SEMICOLON, "Expect ';' after value.");
    return new Stmt.Print(value);
  }

  private Stmt expressionStatement() {
    Expr expr = expression();
    consume(TokenType.SEMICOLON, "Expect ';' after expression.");
    return new Stmt.Expression(expr);
  }
}
