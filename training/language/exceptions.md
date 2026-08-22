# Kof Exceptions Reference

## Throw

```kof
throw "error message"
throw 42
throw "custom error"
```

## Try/Catch

```kof
try {
    riskyOperation()
} catch (String e) {
    println("Error: " + e)
}
```

## Try/Finally

```kof
try {
    riskyOperation()
} finally {
    println("Cleanup")
}
```

## Try/Catch/Finally

```kof
try {
    riskyOperation()
} catch (String e) {
    println("Error: " + e)
} finally {
    println("Cleanup")
}
```

## Multiple Catch

```kof
try {
    riskyOperation()
} catch (String e) {
    println("String error: " + e)
} catch (Int e) {
    println("Int error: " + e)
}
```

## Runtime Errors

| Error | Trigger | Message |
|-------|---------|---------|
| Null pointer | Access null object | "Runtime error: null pointer access" |
| Array bounds | Index out of range | "Runtime error: array index out of bounds" |
| Panic | `kof_panic()` | Custom message |

## Behavior

- **JVM**: Exceptions propagate naturally via JVM mechanism
- **Native**: `throw` calls `kof_panic` (fatal error, process exits)
- **try/catch**: Parsed and analyzed, but Native treats as sequential (no real exception propagation)
- **finally**: Always executed

## Limitations

- No stack traces in Native
- No exception object model (exceptions are simple values)
- Native exceptions are fatal (no recovery)
