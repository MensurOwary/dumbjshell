package io.dumbjshell;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.function.Function;

import static java.util.Map.entry;

record TypeData(String jvmType, Function<String, Object> castFunc) {
}

class ClassRepresentation implements Opcodes {
    private static final Map<String, TypeData> JVM_TYPES = Map.ofEntries(
            entry("int", new TypeData("I", Integer::parseInt)),
            entry("long", new TypeData("J", Long::parseLong)),
            entry("boolean", new TypeData("Z", Boolean::parseBoolean)),
            entry("double", new TypeData("D", Double::parseDouble)),
            entry("float", new TypeData("F", Float::parseFloat)),
            entry("String", new TypeData("Ljava/lang/String;", String::valueOf)),
            entry("Integer", new TypeData("Ljava/lang/Integer;", Integer::valueOf)),
            entry("Long", new TypeData("Ljava/lang/Long;", Long::valueOf)),
            entry("Boolean", new TypeData("Ljava/lang/Boolean;", Boolean::valueOf)),
            entry("Double", new TypeData("Ljava/lang/Double;", Double::valueOf)),
            entry("Float", new TypeData("Ljava/lang/Float;", Float::valueOf))
    );

    private final String className = "io.dumbjshell.Demo";
    private final List<ClassField> fields = new ArrayList<>();

    private Class<?> currentClass;

    ClassRepresentation() {
        this.currentClass = generateClass();
    }

    public boolean addField(ClassField field) {
        if (fieldExists(field)) return false;
        this.fields.add(field);
        return true;
    }

    private boolean fieldExists(ClassField field) {
        return fields.stream().anyMatch(f -> f.name().equals(field.name()));
    }

    public Class<?> generateClass() {
        final var cw = new ClassWriter(0);

        cw.visit(V1_8, ACC_PUBLIC + ACC_SUPER, "io/dumbjshell/Demo", null, "java/lang/Object", null);

        for (var field : fields) {
            final var fw = cw.visitField(ACC_PUBLIC + ACC_STATIC, field.name(), JVM_TYPES.get(field.dataType()).jvmType(), null, JVM_TYPES.get(field.dataType()).castFunc().apply(field.value()));
            fw.visitEnd();
        }

        cw.visitEnd();

        var bc = cw.toByteArray();

        var loader = new DynamicClassLoader(Thread.currentThread().getContextClassLoader());

        return loader.define("io.dumbjshell.Demo", bc);
    }

    public String eval(Statement stmt) throws Exception {
        if (!stmt.isExpressionStmt()) throw new RuntimeException("not an expression");
        final var expr = ((ExpressionStmt) stmt).getExpression();
        if (expr instanceof VariableDeclarationExpr vd) {
            final var variable = vd.getVariables().getFirst().get();
            final var varName = variable.getNameAsString();
            final var varType = variable.getType().asString();
            final var varValue = variable.getInitializer().map(this::evalInitalizer).orElseThrow();

            final var classField = new ClassField(varType, varName, varValue.toString());
            if (this.addField(classField)) {
                this.currentClass = generateClass();
                return currentClass.getField(varName).get(null).toString();
            }
        } else if (expr instanceof NameExpr ne) {
            return evalNameExpr(ne).toString();
        } else if (expr instanceof BinaryExpr be) {
            return evalBinaryExpr(be);
        } else if (expr instanceof LiteralExpr l) {
            return evalLiteralAndNameExpr(l).toString();
        } else if (expr instanceof AssignExpr ae) {
            final var target = ae.getTarget().asNameExpr().getNameAsString();
            final var value = ae.getValue();
            return switch (ae.getOperator()) {
                case ASSIGN -> {
                    final var result = eval(new ExpressionStmt(value));

                    if (fieldExists(new ClassField(null, target, null))) {
                        final var dataType = fields.stream().filter(f -> f.name().equals(target)).findFirst().get().dataType();
                        final var setResult = JVM_TYPES.get(dataType).castFunc().apply(result);
                        currentClass.getField(target).set(null, setResult);
                        yield setResult.toString();
                    } else {
                        throw new RuntimeException(String.format("Variable %s does not exist", target));
                    }
                }
                default -> throw new RuntimeException("Cannot evaluate this assignment expression");
            };
        }

        throw new RuntimeException("cannot process");
    }

    private Object evalInitalizer(Expression e) {
        if (e instanceof LiteralExpr l) {
            return evalLiteralAndNameExpr(l);
        }
        if (e instanceof BinaryExpr be) {
            return evalBinaryExpr(be);
        }
        return null;
    }

    private Object evalNameExpr(NameExpr ne) {
        try {
            final var varName = ne.getNameAsString();
            if (currentClass != null) {
                return currentClass.getField(varName).get(null);
            }
            throw new RuntimeException("cannot process");
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private String evalBinaryExpr(BinaryExpr expr) {
        final var left = expr.getLeft();
        final var right = expr.getRight();

        Object leftRes = evalLiteralAndNameExpr(left);
        Object rightRes = evalLiteralAndNameExpr(right);
        switch (expr.getOperator()) {
            case PLUS -> {
                if (leftRes instanceof Integer a && rightRes instanceof Integer b) {
                    return (a + b) + "";
                }
                if (leftRes instanceof String a && rightRes instanceof String b) {
                    return a + b;
                }
                throw new RuntimeException("unsupported binary expr");
            }
            case MINUS -> {
                if (leftRes instanceof Integer a && rightRes instanceof Integer b) {
                    return (a - b) + "";
                }
                throw new RuntimeException("unsupported binary expr");
            }
            default -> throw new RuntimeException("");
        }
    }

    private Object evalLiteralAndNameExpr(Expression expr) {
        if (expr instanceof LiteralExpr l) {
            if (l instanceof IntegerLiteralExpr ie) {
                return Integer.parseInt(ie.getValue());
            } else if (l instanceof StringLiteralExpr se) {
                return se.getValue();
            } else if (l instanceof BooleanLiteralExpr be) {
                return be.getValue();
            }
        } else if (expr instanceof NameExpr ne) {
            return evalNameExpr(ne);
        }
        throw new RuntimeException("Unsupported literal expr");
    }

    public static class DynamicClassLoader extends ClassLoader {
        public DynamicClassLoader(ClassLoader parent) {
            super(parent);
        }

        public Class<?> define(String className, byte[] bytecode) {
            return super.defineClass(className, bytecode, 0, bytecode.length);
        }
    }
}

record ClassField(String dataType, String name, String value) {
}

public class Main {

    public static void main(String[] args) {
        var sc = new Scanner(System.in);

        final var jp = new JavaParser();

        final var cr = new ClassRepresentation();

        while (true) {
            try {
                System.out.print("dumbjshell> ");
                final var userInput = sc.nextLine();
                if ("exit".equalsIgnoreCase(userInput)) {
                    System.out.println("Exiting...");
                    return;
                }

                final var maybe = jp.parseStatement(userInput + ";");
                if (maybe.isSuccessful() && maybe.getResult().isPresent()) {
                    final var cu = maybe.getResult().get();
                    final var eval = cr.eval(cu);
                    System.out.println("==> " + eval);
                } else {
                    final var maybeExpr = jp.parseExpression(userInput);
                    if (maybeExpr.isSuccessful() && maybeExpr.getResult().isPresent()) {
                        final var cu = maybeExpr.getResult().get();
                        final var eval = cr.eval(new ExpressionStmt(cu));
                        System.out.println("==> " + eval);
                    } else {
                        System.out.println("Error!");
                    }
                }
            } catch (Exception ex) {
                System.out.println("Error: " + ex.getMessage());
            }
        }

    }

}