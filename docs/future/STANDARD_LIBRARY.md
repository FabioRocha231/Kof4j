# Standard Library — Proposta

**Última atualização:** 21 de agosto de 2026
**Status:** Proposta — NÃO implementado

---

## Filosofia

> Se é essencial para qualquer programa, pertence à plataforma.

A standard library deve ser:
- Mínima
- Coerente
- Sem dependências externas
- Disponível em todos os backends

---

## Módulos Propostos

### kof.core

Tipos e operações básicas.

```
String.length()
String.charAt(index)
String.substring(start, end)
String.concat(other)
String.equals(other)
String.contains(other)
String.startsWith(prefix)
String.endsWith(suffix)
String.trim()
String.toLowerCase()
String.toUpperCase()
String.indexOf(other)
String.split(delimiter)
```

### kof.io

Entrada/saída básica.

```
println(value)
print(value)
input() → String
```

### kof.time

Data e hora.

```
DateTime.now()
DateTime.parse("2024-01-01")
duration.hours()
```

### kof.json

Serialização JSON.

```
json.encode(obj)
json.decode(str, Type)
```

### kof.sql

Acesso a banco de dados (futuro).

```
users.find(1)
users.where(User.age > 18)
```

### kof.http

Cliente HTTP (futuro).

```
http.get("https://api.example.com/users")
http.post("https://api.example.com/users", data)
```

### kof.concurrent

Concorrência (futuro).

```
async { ... }
await(promise)
```

### kof.test

Framework de testes (futuro).

```
test "soma" {
    assert add(2, 3) == 5
}
```

---

## Prioridade

| Módulo | Prioridade | Status |
|--------|-----------|--------|
| kof.core | Alta | Parcial (String ops) |
| kof.io | Alta | Parcial (print/println) |
| kof.web | Alta | Não implementado |
| kof.http | Alta | Não implementado |
| kof.json | Média | Não implementado |
| kof.time | Média | Não implementado |
| kof.sql | Alta | Não implementado |
| kof.concurrent | Média | Não implementado |
| kof.test | Alta | Não implementado |

---

## Princípios

1. **Mínimo necessário** — não criar bibliotecas que ninguém usa
2. **Coerência** — APIs devem seguir padrões consistentes
3. **Backend-agnostic** — mesma API em JVM e Native
4. **Sem dependências** — standard library não depende de bibliotecas externas
5. **Evolução** — APIs podem ser estendidas sem quebrar código existente
