# Kof Target Reference

## JVM Target

```bash
kof build --target=jvm
kof run --target=jvm
```

- Generates `.class` files
- Uses JVM's GC and memory management
- Uses `java.lang.String` for strings
- Uses JVM arrays for arrays
- Uses `INVOKEVIRTUAL` for virtual dispatch
- Uses `INVOKEINTERFACE` for interface calls

## Native Target

```bash
kof build --target=native
kof run --target=native
```

- Generates x86-64 Linux ELF binary
- Uses Linux syscalls (no libc)
- Uses KofString for strings
- Uses KofArray for arrays
- Uses vtable for virtual dispatch
- Uses mmap for memory allocation
- No GC yet (memory reclaimed on process exit)

## Runtime Functions (Native)

| Function | Purpose |
|----------|---------|
| `kof_alloc(size)` | Heap allocation |
| `kof_free(ptr)` | No-op (no GC) |
| `kof_panic(msg)` | Fatal error |
| `kof_print(ptr)` | Print string |
| `kof_println(ptr)` | Print string + newline |
| `kof_print_int(val)` | Print integer |
| `kof_string_from_literal(data, len)` | Create KofString |
| `kof_string_length(str)` | Get string length |
| `kof_string_concat(s1, s2)` | Concatenate strings |
| `kof_string_equals(s1, s2)` | Compare strings |
| `kof_string_char_at(str, idx)` | Get char at index |
| `kof_string_substring(str, start, end)` | Substring |
| `kof_string_contains(str, sub)` | Check contains |
| `kof_string_starts_with(str, prefix)` | Check starts with |
| `kof_string_ends_with(str, suffix)` | Check ends with |
| `kof_array_alloc(len, elem_size)` | Create array |
| `kof_array_length(arr)` | Get array length |
| `kof_array_get(arr, idx)` | Get array element |
| `kof_array_set(arr, idx, val)` | Set array element |
| `kof_init_object(ptr, type_id, vtable)` | Initialize object header |
| `kof_net_socket(domain, type, proto)` | Create socket |
| `kof_net_bind(fd, port, addr)` | Bind socket |
| `kof_net_listen(fd, backlog)` | Listen on socket |
| `kof_net_accept(fd)` | Accept connection |
| `kof_net_read(fd, buf, len)` | Read from socket |
| `kof_net_write(fd, buf, len)` | Write to socket |
| `kof_net_close(fd)` | Close socket |
