# Database Vision — Persistência como Parte da Linguagem

**Última atualização:** 27 de agosto de 2026
**Versão:** 0.2.6-beta
**Status:** Nível 0-2 e 4 implementados (`kof.db` + `kof.orm`, 0.2.6-beta):
`entity` (schema na linguagem), `orm.create/save/saveAll/find/all/where/
where-op/delete/deleteAll/count/count-filtrado/page/migrate` (JDBC no JVM:
H2, MySQL, MariaDB, PostgreSQL, SQLite; mappings de records; migrations
versionadas); SQLite nativo via `libsqlite3` direto; MySQL/MariaDB handshake via `kof_db_mysql_scramble` (27/08) — wire protocol sobre sockets nativos em progresso; `VERSION` 0.2.6-beta; build 658 testes.

---

## O Problema com Hibernate/JPA

Hibernate resolve problemas reais:
- Mapeamento objeto-relacional
- Queries tipadas
- Lazy loading
- Transações
- Cache

Mas introduz complexidade massiva:
- EntityManager/Session
- Repository pattern
- JPQL
- Annotations (@Entity, @Column, @Id, etc.)
- XML de configuração
- DTOs artificiais
- Mapeamentos repetitivos

A pergunta é: **quanto disso existe porque Java não conhece banco de dados?**

---

## Filosofia Kof

> Se a linguagem pudesse definir entidades diretamente, precisaríamos de ORM?

### Entidades como Linguagem

```kof
entity User {
    id: Long generated
    name: String
    email: String unique
    age: Int
}
```

Isso define:
- Tabela `user`
- Colunas `id`, `name`, `email`, `age`
- `id` é auto-increment
- `email` tem unique constraint
- Tipos são mapeados automaticamente

### Queries Tipadas

```kof
// Query simples
users.find(1)

// Query com条件
users.where(User.age > 18)

// Query com joins
users.with(User.address).where(User.name == "Mel")
```

Sem:
- EntityManager
- Repository
- JPQL
- Criteria API
- Annotations

---

## Comparação

### Hibernate/JPA

```java
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "name", nullable = false, length = 50)
    private String name;
    
    @Column(name = "email", unique = true)
    private String email;
    
    // getters, setters, constructors...
}

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    List<User> findByAgeGreaterThan(int age);
}
```

### Kof (PROPOSTA)

```kof
entity User {
    id: Long generated
    name: String
    email: String unique
    age: Int
}

// Queries são parte da linguagem
users.find(1)
users.where(User.age > 18)
```

---

## Arquitetura Proposta

### Nível 1: Schema Definition

```kof
entity User {
    id: Long generated
    name: String
    email: String unique
    age: Int
    createdAt: DateTime auto
}
```

O compilador:
1. Valida os tipos
2. Gera SQL DDL automaticamente
3. Cria mappings objeto-relacionais

### Nível 2: Query System

```kof
// Find by ID
var user = users.find(1)

// Where clause
var adults = users.where(User.age > 18)

// With joins
var usersWithAddress = users.with(User.address)
```

O compilador:
1. Valida tipos das queries
2. Gera SQL otimizado
3. Tipa o resultado

### Nível 3: Transactions

```kof
transaction {
    users.save(user)
    addresses.save(address)
}
```

### Nível 4: Migrations

```kof
migration "add_phone_to_users" {
    add Column("phone", String) to User
}
```

---

---

# Nível 0 — Implementado (kof.db)

```kof
var db = db.connect("jdbc:h2:mem:test")     // JVM: JDBC idiomático
db.execute(db, "create table users(id int, name varchar)")
db.execute(db, "insert into users values (?, ?)", 1, "Mel")
var rows = db.query<User>(db, "select * from users where id = ?", 1)
transaction {
    db.execute(db, "insert into users values (2, 'Kof')")
}
db.close(db)
```

- **JVM:** `connect(url)` / `connect(url, user, pass)`, `execute` (0-4 binds),
  `query<T>` (0-4 binds, mapping de records), `transaction { }` (commit/
  rollback), `close`.
- **Native:** SQLite via link direto de `libsqlite3.so.0` (sem JDBC driver) —
  `db.connect("sqlite:/path.db")`, execute/query tipado, roundtrip testado.
- **Native MySQL/MariaDB:** wire protocol próprio (handshake `kof_db_mysql_scramble` SHA-1 27/08, lenenc, execução de queries) sobre sockets nativos — em andamento
  (handshake scramble válido; query/prepared pendentes; sem teste E2E contra servidor real ainda).
- **JS:** `DB001` (diagnóstico claro em compile-time).
- Testes: `KofDbE2ETest` (8) + `KofOrmE2ETest` (16, inclui MariaDB/PostgreSQL/MongoDB com skip condicional). O link nativo inclui a lib do MySQL apenas
  quando o programa a usa (URL literal detectado em compile-time).

Limitações conhecidas do nível 0: bind máximo de 4 parâmetros; sem
connection pooling (cada `connect` abre conexão própria); sem timeouts/
retries/observability; `db.execute(db, ...)` exige o receiver como primeiro
argumento (a API idiomática será `db.execute(sql, binds...)`).

---

## Por Que Não ORM?

ORM tradicional:
- Mapeia objetos para tabelas
- Usa reflection para descobrir campos
- Requer annotations para configuração
- Introduz abstrações pesadas (Session, EntityManager)

Kof propõe:
- Entidades são definidas na linguagem
- O compilador gera o SQL
- Queries são tipadas e validadas em compile-time
- Sem reflection, sem annotations, sem abstrações pesadas

---

## Conexão com Banco de Dados

### Configuração

```kof
config {
    database {
        url = "jdbc:postgresql://localhost/mydb"
        user = "admin"
        password = "secret"
    }
}
```

### Connection Pool

O runtime pode gerenciar connection pool automaticamente. O programador não precisa configurar.

---

## Limitações Conhecidas

1. **Sem suporte a múltiplos bancos** — foco inicial em PostgreSQL
2. **Sem lazy loading** — pode ser adicionado futuramente
3. **Sem cache** — pode ser adicionado futuramente
4. **Sem migrations automáticas** — requer definição explícita
