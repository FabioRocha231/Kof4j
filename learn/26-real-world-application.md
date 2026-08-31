# 26 — Aplicação Real

> **Status: futuro (pós 0.2.6-beta — `kof.web` + `kof_db` já cobrem o caso sem Spring)**
>
> Este capítulo mostra como construir uma aplicação completa em Kof com Spring Boot.

## Task Manager

Vamos construir uma API simples de gerenciamento de tarefas.

### Estrutura

```
src/main/kof/com/exemplo/tasks/
├── Task.kf
├── TaskRepository.kf
├── TaskService.kf
├── TaskController.kf
└── Application.kf
```

### Domain

```kf
record Task(
    UUID id,
    String title,
    String description,
    Bool completed,
    java.time.LocalDateTime createdAt
)
```

### Repository

```kf
interface TaskRepository extends CrudRepository<Task, UUID> {
    List<Task> findByCompletedFalse();
    List<Task> findByCompletedTrue();
}
```

### Service

```kf
@Service
class TaskService(TaskRepository repository) {

    Task create(String title, String description) {
        var task = new Task(
            UUID.randomUUID(),
            title,
            description,
            false,
            java.time.LocalDateTime.now()
        );
        return repository.save(task);
    }

    Task complete(UUID id) {
        var task = repository.findById(id)
            .orElseThrow(() -> new RuntimeException("task not found"));
        var updated = new Task(task.id(), task.title(), task.description(), true, task.createdAt());
        return repository.save(updated);
    }

    List<Task> pendingTasks() {
        return repository.findByCompletedFalse();
    }
}
```

### Controller

```kf
@RestController
@RequestMapping("/tasks")
class TaskController(TaskService service) {

    @PostMapping
    Task create(@RequestBody CreateTaskRequest request) {
        return service.create(request.title(), request.description());
    }

    @PutMapping("/{id}/complete")
    Task complete(@PathVariable UUID id) {
        return service.complete(id);
    }

    @GetMapping("/pending")
    List<Task> pending() {
        return service.pendingTasks();
    }
}
```

### Request DTO

```kf
record CreateTaskRequest(String title, String description)
```

### Application

```kf
@SpringBootApplication
class Application {
    static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### Testes

```kf
@SpringBootTest
class TaskServiceTest {
    @Autowired
    TaskService service;

    @Test
    void deveCriarTask() {
        var task = service.create("Estudar Kof", "ler documentação");
        assertFalse(task.completed());
        assertEquals("Estudar Kof", task.title());
    }

    @Test
    void deveCompletarTask() {
        var task = service.create("Tarefa", "descrição");
        var completed = service.complete(task.id());
        assertTrue(completed.completed());
    }
}
```

## Próximo passo

[Boas Práticas →](27-best-practices.md)
