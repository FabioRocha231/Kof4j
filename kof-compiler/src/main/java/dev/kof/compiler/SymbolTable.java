package dev.kof.compiler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class SymbolTable {

    private final SymbolTable parent;
    private final Map<String, Symbol> symbols = new HashMap<>();
    private final List<String> symbolOrder = new ArrayList<>();

    SymbolTable() {
        this(null);
    }

    SymbolTable(SymbolTable parent) {
        this.parent = parent;
    }

    void define(Symbol symbol) {
        symbols.put(symbol.name(), symbol);
        symbolOrder.add(symbol.name());
    }

    Symbol resolve(String name) {
        Symbol s = symbols.get(name);
        if (s != null) return s;
        if (parent != null) return parent.resolve(name);
        return null;
    }

    boolean hasLocal(String name) {
        return symbols.containsKey(name);
    }

    SymbolTable enterScope() {
        return new SymbolTable(this);
    }

    SymbolTable parent() {
        return parent;
    }

    Map<String, Symbol> localSymbols() {
        return Collections.unmodifiableMap(symbols);
    }

    sealed interface Symbol {
        String name();
        Type type();
    }

    record ParameterSymbol(String name, Type type, int index) implements Symbol {
    }

    record LocalVariableSymbol(String name, Type type, int index) implements Symbol {
    }

    record FieldSymbol(String name, Type type, int accessFlags, String ownerClass) implements Symbol {
    }

    record MethodSymbol(String name, String ownerClass, Type returnType,
                        List<Type> parameterTypes, int accessFlags,
                        DispatchKind dispatchKind) implements Symbol {
        @Override
        public Type type() {
            return returnType;
        }
    }

    record ConstructorSymbol(String ownerClass, List<Type> parameterTypes, int accessFlags) implements Symbol {
        @Override
        public String name() {
            return "<init>";
        }

        @Override
        public Type type() {
            return new Type.ClassType("", ownerClass, List.of());
        }
    }

    record ClassSymbol(String name, String packageName, String superClass,
                       List<String> interfaces, SymbolTable members) implements Symbol {
        @Override
        public Type type() {
            return new Type.ClassType(packageName, name, List.of());
        }

        String internalName() {
            if (packageName.isEmpty()) return name;
            return packageName.replace('.', '/') + "/" + name;
        }
    }

    record FunctionSymbol(String name, Type returnType, List<Type> parameterTypes,
                          int accessFlags) implements Symbol {
        @Override
        public Type type() {
            return returnType;
        }
    }

    enum DispatchKind {
        INSTANCE,
        STATIC,
        INTERFACE
    }
}
