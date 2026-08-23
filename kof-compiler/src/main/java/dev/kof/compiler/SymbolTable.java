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

    record TypeParameterSymbol(String name) implements Symbol {
        @Override
        public Type type() {
            return new Type.TypeVariable(name);
        }
    }

    record LocalVariableSymbol(String name, Type type, int index) implements Symbol {
    }

    record FieldSymbol(String name, Type type, int accessFlags, String ownerClass) implements Symbol {
    }

    static final class MethodSymbol implements Symbol {
        private final String name;
        private final String ownerClass;
        private Type returnType;
        private final List<Type> parameterTypes;
        private final int accessFlags;
        private final DispatchKind dispatchKind;

        MethodSymbol(String name, String ownerClass, Type returnType,
                     List<Type> parameterTypes, int accessFlags,
                     DispatchKind dispatchKind) {
            this.name = name;
            this.ownerClass = ownerClass;
            this.returnType = returnType;
            this.parameterTypes = parameterTypes;
            this.accessFlags = accessFlags;
            this.dispatchKind = dispatchKind;
        }

        void setReturnType(Type returnType) {
            this.returnType = returnType;
        }

        @Override
        public Type type() {
            return returnType;
        }

        public String name() {
            return name;
        }

        public String ownerClass() {
            return ownerClass;
        }

        public Type returnType() {
            return returnType;
        }

        public List<Type> parameterTypes() {
            return parameterTypes;
        }

        public int accessFlags() {
            return accessFlags;
        }

        public DispatchKind dispatchKind() {
            return dispatchKind;
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
