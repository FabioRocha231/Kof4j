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

## Window e Label (webview)

A janela do webview e os labels que se ligam a ela:

```kof
main() {
    var w = Window("Minha Janela")
    var label = Label("Olá, Kof!")

    w.title = "Kof App"       // bind: título da janela
    w.bind(label)             // monta o label na janela

    label.text = "Olá, janela!"   // bind: atualiza o texto do label

    println(label.text)
    println(w.title)
}
```

| Operação | Descrição |
|----------|-----------|
| `Window("título")` | cria a janela (handle) |
| `w.title = v` / `w.title()` | bind do título |
| `w.bind(label)` | monta um label na janela |
| `w.show()` / `w.close()` | janela |
| `Label("texto")` | cria um label (handle) |
| `label.text = v` / `label.text()` | bind do texto |
| `label.remove()` | remove o label da janela |

**Renderização é KofJS.** `kof build --target=js` gera `index.html` +
`kof-runtime.mjs`: a página tem o ponto de montagem `#kof-root` e o runtime
DOM cria/atualiza os elementos (`document.title`, `span.kof-label`, ...).
Um webview nativo é o shell que carrega essa página — frente futura.

No JVM e Native os handles são no-ops (a UI é KofJS) — o programa executa,
mas nada é renderizado.

## Representação

- `Color` e `Theme` são valores de 32 bits (Int) — sem objetos.
- Canais são manipulação de bits no compilador — zero custo de runtime.
- `toCss()` é o único ponto que precisa de runtime (construção de string),
  com implementações idênticas em JVM, Native e JS.

## Referências

- `kof-compiler/src/main/java/dev/kof/compiler/KofUi.java`
- Testes: `kof-compiler/src/test/java/dev/kof/compiler/UiE2ETest.java`