# Filosofia do Kof

**Última atualização:** 21 de agosto de 2026

---

## Princípio Central

> O programador deve escrever a intenção.
> A linguagem e o runtime cuidam da complexidade.

Kof não existe para ser "mais um Java". Kof existe para resolver problemas que Java não resolve bem — ou que resolve apenas com frameworks complexos.

---

## Princípios Arquiteturais

### 1. Simplicidade por Padrão

O caso comum deve ser o mais simples possível. Se o programador precisa escrever mais de 3 linhas para algo comum, algo está errado.

```kof
// Kof: simples
fun main() {
    println("Hello")
}

// Java equivalente:
public class Main {
    public static void main(String[] args) {
        System.out.println("Hello");
    }
}
```

### 2. Zero Boilerplate

Se o compilador pode deduzir algo, o programador não deve precisar escrever.

- Construtores: gerados automaticamente quando possível
- Getters/Setters: não necessários (fields são acessíveis diretamente)
- toString: gerado para records
- Igualdade: gerada para records

### 3. Runtime Esconde Complexidade

O programador NÃO deve conhecer:
- malloc/free
- Ponteiros
- GC
- ABI
- Calling conventions
- Layout de memória
- Detalhes da JVM
- Detalhes do Native runtime

```kof
var a = new Int[100]  // aloca, inicializa, gerencia
var s = "Hello"       // aloca KofString
```

### 4. Mesmo Código, Múltiplos Targets

O mesmo programa Kof deve funcionar semanticamente em JVM e Native. O programador não deve precisar alterar seu código para mudar de target.

### 5. Memória é Responsabilidade do Runtime

O programador NÃO deve precisar:
- Liberar memória manualmente
- Gerenciar ownership
- Evitar memory leaks
- Conhecer o ciclo de vida dos objetos

O runtime deve resolver isso automaticamente.

### 6. Compilador Elimina Classes de Problemas

Erros que podem ser detectados em compile-time NÃO devem existir em runtime:
- Tipos incompatíveis
- Métodos inexistentes
- Campos inexistentes
- Quantidade errada de argumentos

### 7. Convenção > Configuração

Se algo pode ser resolvido por convenção, não precisa de configuração.

```kof
// Por convenção, main() é o ponto de entrada
fun main() {
    // ...
}

// Por convenção, o nome do arquivo define o módulo
```

### 8. Segurança de Tipos em Compile-time

Erros de tipo devem ser capturados antes da execução. O compilador deve ser rigoroso.

### 9. APIs Pequenas

Menos é mais. Uma API com 5 métodos úteis é melhor que uma com 50 métodos dos quais 40 são raramente usados.

### 10. Linguagem Resolve, Framework Não

Se a linguagem pode resolver um problema diretamente, não crie um framework para isso.

| Problema | Solução Framework | Solução Kof |
|----------|------------------|-------------|
| HTTP routing | Spring MVC | Proposta: `route GET "/users" { ... }` |
| Validação | Bean Validation | Proposta: `name: String required` |
| Serialização | Jackson | Proposta: implícita |
| Configuração | application.properties | Proposta: `config { port = 8080 }` |

### 11. Não Copiar o Java

Kof não deve copiar features do Java apenas porque elas existem. Cada feature deve ser questionada:

- "Isso resolve um problema real?"
- "Existe uma forma mais simples?"
- "A complexidade vale a pena?"

### 12. Não Exigir Infraestrutura para Recursos Básicos

Criar um servidor HTTP não deve exigir:
- Spring Boot
- Tomcat
- Servlet container
- XML de configuração
- Annotations

Deveria ser algo como:
```kof
route GET "/users" {
    return users.all()
}
```

### 13. Performance Sem Sacrificar Ergonomia

A linguagem deve ser ergonômica E performática. Não deve ser necessário escrever código feio para ter performance.

### 14. Native e JVM Compartilham Semântica

A semântica da linguagem é única. Os backends implementam essa semântica de forma diferente, mas o comportamento observável deve ser o mesmo.

---

## O que Kof NÃO é

- Não é Java com outra sintaxe
- Não é Kotlin 2
- Não é um transpiler para Java
- Não é um interpretador
- Não é uma linguagem para scripts (embora possa ser usada para isso)
- Não é uma linguagem para web (embora possa ser usada para isso)

Kof é uma linguagem de programação geral, compilada, com múltiplos backends.

---

## Visão de Futuro

Kof deve evoluir para ser uma plataforma onde:

1. **Backend APIs** são construídas na linguagem, não em frameworks
2. **Persistência** é parte da linguagem, não de um ORM
3. **Segurança** é parte da linguagem, não de um framework
4. **Observabilidade** é parte da linguagem, não de bibliotecas
5. **Concorrência** é parte da linguagem, não de APIs

O objetivo é que a complexidade que hoje vive em Spring, Hibernate, e dezenas de outras bibliotecas, seja resolvida pelo compilador e runtime do Kof.
