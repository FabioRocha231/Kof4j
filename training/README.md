# Kof Training — Corpus para LLMs

Este diretório contém conhecimento estruturado sobre a linguagem Kof, otimizado para modelos de linguagem.

## Propósito

O corpus permite que LLMs:
- Entendam a sintaxe e semântica do Kof
- G código válido e idiomático
- Corrijam código Kof
- Expliquem código Kof
- Migrem Java/Spring para Kof
- G APIs, testes e documentação

## Estrutura

```
training/
├── README.md              # Este arquivo
├── language/              # Conceitos da linguagem
│   ├── overview.md
│   ├── syntax.md
│   ├── types.md
│   ├── classes.md
│   ├── inheritance.md
│   ├── interfaces.md
│   ├── exceptions.md
│   ├── arrays.md
│   └── strings.md
├── reference/             # Referência técnica
│   ├── compiler.md
│   └── targets.md
├── patterns/              # Padrões idiomáticos
│   └── common-patterns.md
├── examples/              # Exemplos executáveis
│   ├── hello.kf
│   ├── classes.kf
│   ├── inheritance.kf
│   └── web.kf
├── anti-patterns/         # O que não fazer
│   └── common-mistakes.md
└── migration/             # Migração
    └── java-to-kof.md
```

## Regras

1. Todo conteúdo deve refletir o código REAL
2. Não documentar features inexistentes
3. Exemplos devem ser verificáveis
4. Evitar ambiguidades
5. Ser conciso e preciso
