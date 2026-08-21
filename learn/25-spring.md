# 25 — Spring

> **Status: futuro**
>
> A integração com Spring é um dos objetivos de longo prazo da Kof. Este capítulo documenta a visão planejada.

## O objetivo

Usar Spring Boot real, não um "Kof Spring".

```kf
@SpringBootApplication
class Application {
    static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

## REST Controller

```kf
@RestController
class UserController(UserService service) {

    @GetMapping("/users/{id}")
    User find(@PathVariable UUID id) {
        return service.find(id);
    }

    @GetMapping("/users")
    List<User> findAll() {
        return service.findAll();
    }

    @PostMapping("/users")
    User create(@RequestBody CreateUserRequest request) {
        return service.create(request);
    }
}
```

## Service

```kf
@Service
class UserService(UserRepository repository) {

    User find(UUID id) {
        return repository.findById(id)
            .orElseThrow(() -> new UserNotFound(id.toString()));
    }

    List<User> findAll() {
        return repository.findAll();
    }
}
```

## Repository

```kf
interface UserRepository extends CrudRepository<User, UUID> {
    List<User> findByActiveTrue();
}
```

## Como funciona

1. Kof gera bytecode JVM padrão
2. Spring enxerga as annotations no bytecode
3. Spring cria proxies normalmente
4. Injeção de dependência funciona
5. AOP funciona
6. Transaction management funciona

Kof não precisa de módulo especial para Spring. O bytecode é Java.

## Configuration

```kf
@Configuration
class AppConfig {
    @Bean
    DataSource dataSource() {
        return new HikariDataSource();
    }
}
```

## Testes com Spring

```kf
@SpringBootTest
class UserServiceTest {
    @Autowired
    UserService service;

    @Test
    void deveEncontrarUser() {
        var user = service.find(UUID.randomUUID());
        assertNotNull(user);
    }
}
```

## Próximo passo

[Aplicação Real →](26-real-world-application.md)
