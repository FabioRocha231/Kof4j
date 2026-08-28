# Kof Array Reference

**Version:** 0.2.0-beta

## Creation

```kof
var arr = new Int[10]      // array of 10 integers
var strings = new String[5] // array of 5 strings
var empty = new Int[0]      // empty array
```

## Access

```kof
var arr = new Int[5]
arr[0] = 10    // set element
println(arr[0]) // get element
```

## Length

```kof
var arr = new Int[5]
println(arr.length)  // 5
```

## Bounds Checking

Runtime checks bounds on access. Out-of-bounds access triggers `kof_bounds_error`.

## Multi-dimensional

```kof
var matrix = new Int[3]
// Each element can be an array
```

## Array as Parameter

```kof
sum(Int[] arr): Int {
    var total = 0
    for (var i = 0; i < arr.length; i++) {
        total = total + arr[i]
    }
    return total
}
```

## Array as Return

```kof
createArray(): Int[] {
    var a = new Int[3]
    a[0] = 10
    a[1] = 20
    a[2] = 30
    return a
}
```

## Empty Array

```kof
var empty = new Int[0]
println(empty.length)  // 0
```
