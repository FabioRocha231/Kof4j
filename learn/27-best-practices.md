# 27 — Boas Práticas

## Naming

- **Classes**: PascalCase (`UserService`, `TaskRepository`)
- **Records**: PascalCase (`User`, `Point`)
- **Métodos**: camelCase (`findUser`, `isActive`)
- **Campos**: camelCase (`userName`, `createdAt`)
- **Variáveis locais**: camelCase (`indice`, `tamanho`)
- **Constants**: SCREAMING_SNAKE_CASE (`MAX_SIZE`, `DEFAULT_TIMEOUT`)
- **Packages**: lowercase (`com.exemplo.users`)

## Organização

Um arquivo `.kf` deve conter uma principal declaração de tipo.

```
src/main/kof/
├── com/exemplo/
│   ├── model/
│   │   ├── User.kf
│   │   └── Task.kf
│   ├── service/
│   │   ├── UserService.kf
│   │   └── TaskService.kf
│   ├── repository/
│   │   ├── UserRepository.kf
│   │   └── TaskRepository.kf
│   └── controller/
│       ├── UserController.kf
│       └── TaskController.kf
```

## Composition vs Inheritance

Prefira composição:

```kf
// BOM
class Motorista(Carro carro) {
    void dirigir() {
        carro.mover();
    }
}

// EVITAR (quando não faz sentido)
class Motorista extends Carro {
    // ...
}
```

Use herança apenas quando a relação for "é um tipo de":
- `Cachorro` é um `Animal`
- `Exception` é um `Exception`
- `AdminController` é um `Controller`

## Error Handling

```kf
// BOM: tratamento explícito
User findUser(UUID id) {
    return repository.findById(id)
        .orElseThrow(() -> new UserNotFound(id.toString()));
}

// EVITAR: swallowed exceptions
try {
    riskyOperation();
} catch (Exception e) {
    // silenciosamente ignorado
}
```

## Imutabilidade

Prefira `val` sobre `var`:

```kf
val nome = "Mel";        // bom
var nome = "Mel";        // ok se precisar reatribuir
```

Prefira records sobre classes mutáveis para dados:

```kf
record User(String name, String email)  // imutável
```

## Próximo passo

[Design da Linguagem →](28-language-design.md)
