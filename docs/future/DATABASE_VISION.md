# Database Vision — Persistência como Parte da Linguagem

**Última atualização:** 21 de agosto de 2026
**Status:** Análise arquitetural — NÃO implementado

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
