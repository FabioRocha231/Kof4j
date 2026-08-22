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

- **JVM**: Exceptions propagate naturally via the JVM exception table; the thrown
  String is wrapped in a `RuntimeException` and unwrapped back in the catch.
- **Native**: real unwinding via an exception frame chain (`kof_throw_string`):
  frames restore `rsp`/`rbp` and jump to the handler; `finally` runs and the
  exception is rethrown; propagation across function frames works.
- **try/catch**: works on both targets. In Native, the FIRST catch of a try
  captures (no type dispatch between multiple catches).
- **finally**: always executed (normal path, caught path, propagation).

## Limitations

- No stack traces in Native
- In Native, multiple catches on one try: the first one captures
- Exceptions are Strings (no exception object model yet)
- No exception object model (exceptions are simple values)
- Native exceptions are fatal (no recovery)
