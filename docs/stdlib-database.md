# stdlib database — Banco de Dados Nativo do Kof

**Última atualização:** 23 de agosto de 2026
**Status:** implementado (Fase 5 do plano de independência do Spring)

---

## 1. Filosofia

> Acesso a banco é uma capacidade da plataforma. JDBC é o mecanismo interno
> (interoperabilidade JVM); a API exposta é Kof-idomática — sem
> `EntityManager`, `Session`, `PersistenceContext` ou `@Transactional`.

## 2. API

```kof
main() {
    var db = db.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1")

    db.execute(db, "create table users(id int, name varchar(50))")
    db.execute(db, "insert into users values (?, ?)", 1, "Mel")
    db.execute(db, "insert into users values (?, ?)", 2, "Kof")

    // Consulta sem tipo: cada linha vira um objeto JSON
    var rows = db.query(db, "select * from users where id = ?", 1)
    println(rows.get(0))     // {"id":1,"name":"Mel"}

    // Consulta tipada: bind automático para records/classes
    var users = db.query<User>(db, "select * from users order by id")
    println(users.get(0).name)

    // Transação: commit automático; rollback em erro
    transaction {
        db.execute(db, "insert into users values (3, 'Ada')")
    }

    db.close(db)
}
```

## 3. Funções

| Chamada | Descrição |
|---------|-----------|
| `db.connect(url)` | Conecta e retorna o handle |
| `db.connect(url, user, pass)` | Conecta com credenciais |
| `db.execute(handle, sql[, args...])` | UPDATE/INSERT/DELETE; retorna linhas afetadas |
| `db.query(handle, sql[, args...])` | SELECT; `List<String>` — cada linha em JSON |
| `db.query<T>(handle, sql[, args...])` | SELECT; `List<T>` — bind por nome de coluna |
| `transaction { ... }` | Bloco transacional (usa a última conexão) |
| `db.close(handle)` | Fecha a conexão |

- Bind com `?` placeholders; args de `Int/Long/Bool/String` são convertidos
  automaticamente (boxing).
- Colunas são normalizadas para minúsculas (H2/Postgres devolvem maiúsculas).
- Até 4 argumentos de bind por chamada (overloads de aridade fixa).

## 4. Exemplo com a stack web

```kof
record User(Int id, String name)

main() {
    var db = db.connect(config.str("database.url", "jdbc:h2:mem:app"))
    db.execute(db, "create table if not exists users(id int, name varchar(50))")

    var app = web.app()
    app.get("/users") {
        var users = db.query<User>(db, "select * from users order by id")
        return json.encode(users)
    }
    app.post("/users") {
        var u = json.decode<User>(body())
        db.execute(db, "insert into users values (?, ?)", u.id, u.name)
        return "{\"ok\": true}"
    }
    app.listen(config.int("server.port", 8080))
}
```

## 5. Drivers

JDBC por `java.sql.DriverManager` — qualquer driver JDBC no classpath
funciona (H2, SQLite, PostgreSQL, MySQL). O driver é resolvido pelo
`ServiceLoader` do JDK; nenhum acoplamento de biblioteca no runtime Kof.

## 6. Targets

| Target | Estado |
|--------|--------|
| JVM | ✅ completo (JDBC) |
| Native | DB001 (gap documentado em compile-time) |
| JS | DB001 (gap documentado em compile-time) |

## 7. Testes

`KofDbE2ETest` — 7 testes E2E com H2 em memória: execute + query JSON,
query tipada com bind, transação com commit, rollback em exceção,
credenciais, e DB001 nos targets native/js.

## 8. Evolução planejada

- `repository<User>` / abstração de repositório.
- Connection pooling.
- Migrations (`kof migrate`).
- Suporte no Native (implementação JDBC nativa ou documentação de gap).