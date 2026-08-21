# 20 — Annotations

> **Status: planejado**
>
> O token `@` existe no lexer, mas annotations não são parseadas nem geradas no bytecode.

## O que são annotations

Annotations são metadados que podem ser adicionados a classes, métodos, campos e parâmetros.

## Annotations em Kof

```kf
@Entity
class User {
    @Id
    UUID id;

    @Column("user_name")
    String name;
}
```

## Annotations com parâmetros

```kf
@GetMapping("/users/{id}")
User findUser(@PathVariable UUID id) {
    // ...
}
```

## Annotations que o compilador gera no bytecode

O compilador Kof gera:
- `RuntimeVisibleAnnotations` — visíveis em runtime via reflection
- `RuntimeInvisibleAnnotations` — não visíveis em runtime
- Anotações em parâmetros
- Anotações em campos
- Anotações em métodos

## Interoperabilidade com frameworks Java

Annotations funcionam normalmente com:
- Spring (`@Service`, `@Autowired`, `@RestController`)
- JPA (`@Entity`, `@Table`, `@Column`)
- Jackson (`@JsonProperty`, `@JsonIgnore`)
- JUnit (`@Test`, `@BeforeEach`)
- Qualquer framework que use annotations

```kf
@Service
class UserService(UserRepository repository) {
    User find(UUID id) {
        return repository.findById(id);
    }
}
```

O Spring enxerga `@Service` normalmente porque a annotation está no bytecode.

## Próximo passo

[Java Interoperability →](21-java-interoperability.md)
