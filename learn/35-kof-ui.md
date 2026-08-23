# 35 — kof.ui — Cores, Paletas e Temas

`kof.ui` é a fundação da plataforma de UI do Kof. A renderização (widgets,
DOM, canvas) é uma frente futura — hoje temos a base de cores que tudo
consumirá, com a mesma semântica em JVM, Native e JS.

## Color

Cores são valores de 32 bits (`0xRRGGBBAA`) com canais 0-255:

```kof
var red = Color(255, 0, 0)          // r, g, b (alpha = 255)
var rgba = Color.rgba(10, 20, 30, 128)
var v = Color(0xFF0000FF)           // valor empacotado direto
```

| Operação | Descrição |
|----------|-----------|
| `red()` / `green()` / `blue()` / `alpha()` | canais 0-255 |
| `isOpaque()` | alpha == 255 |
| `withAlpha(a)` | nova cor com alpha substituído |
| `toCss()` | `rgb(r, g, b)` ou `rgba(r, g, b, a)` |

```kof
Color(255, 0, 0).toCss()        // rgb(255, 0, 0)
Color.rgba(10, 20, 30, 128).toCss()   // rgba(10, 20, 30, 128)
```

## Palette

Cores nomeadas por constantes:

```kof
Palette.red
Palette.green
Palette.blue
Palette.yellow
Palette.cyan
Palette.magenta
Palette.black
Palette.white
Palette.gray
Palette.orange
Palette.purple
Palette.pink
Palette.brown
Palette.transparent
```

## Theme

Temas light/dark com cores semânticas:

```kof
var theme = Theme.dark()      // ou Theme.light()
theme.isDark()
theme.background()
theme.surface()
theme.primary()
theme.secondary()
theme.text()
theme.error()
```

```kof
var dark = Theme.dark()
println(dark.background().toCss())   // rgb(18, 18, 18)
println(dark.text().toCss())         // rgb(255, 255, 255)
```

## Representação

- `Color` e `Theme` são valores de 32 bits (Int) — sem objetos.
- Canais são manipulação de bits no compilador — zero custo de runtime.
- `toCss()` é o único ponto que precisa de runtime (construção de string),
  com implementações idênticas em JVM, Native e JS.

## Referências

- `kof-compiler/src/main/java/dev/kof/compiler/KofUi.java`
- Testes: `kof-compiler/src/test/java/dev/kof/compiler/UiE2ETest.java`