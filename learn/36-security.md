# 36 — Segurança (kof.security)

> **Kof 0.2.6-beta — 658 testes — `kof_db` MySQL + free-list GC Native**

`kof.security` é a camada de segurança da Standard Library: senhas, criptografia,
JWT, segredos e autenticação para aplicações web — com **secure by default**.

```kof
var hash = passwords.hash("hunter2")
println(passwords.verify("hunter2", hash))   // true
```

## Passwords

Nunca use `sha256(password)` para armazenar senha. Use `passwords`:

```kof
var hash = passwords.hash("senha")            // pbkdf2$sha256$600000$salt$hash
var ok = passwords.verify("senha", hash)      // comparação constant-time
var rehash = passwords.needsRehash(hash)      // parâmetros defasados?
```

A escolha de algoritmo/iterações/salt é automática e segura. O formato do
hash é versionado — quando os parâmetros recomendados mudarem,
`needsRehash` retorna `true` e a aplicação pode re-hashear.

## Crypto

```kof
var digest = crypto.sha256("kof")             // hex
var mac = crypto.hmacSha256(key, data)        // hex
var key = crypto.randomHex(32)                // 32 bytes seguros
var ct = crypto.encryptAesGcm("segredo", key) // aesgcm$iv$ct
var pt = crypto.decryptAesGcm(ct, key)        // falha em tamper
```

Gaps de target são erros de compilação claros (`SECN00x`), nunca
comportamento silencioso.

## JWT

```kof
var token = jwt.create("{\"sub\":\"u1\",\"roles\":[\"admin\"]}", secret)
var claims = jwt.verify(token, secret)
var claims2 = jwt.verify(token, secret, "kof", "api")   // iss + aud
```

O algoritmo é fixo (HS256) — **nunca aceito do token** (sem confusão de
algoritmo). `exp`, `iss` e `aud` são validados. O secret padrão vem de
`KOF_JWT_SECRET` (`jwt.secret()`).

## Segredos

```kof
var apiKey = secrets.get("API_KEY")           // variável de ambiente
var apiKey = secrets.get("API_KEY", "dev")    // com fallback
var logLine = secrets.redact(token)           // nunca vaze segredos em logs
```

## Web auth (middleware)

```kof
var app = web.app()
auth.secret("s3cret")
app.use {
    if (!auth.authenticated()) {
        return "{\"error\":\"unauthorized\"}"
    }
    if (!auth.hasRole("admin")) {
        return "{\"error\":\"forbidden\"}"
    }
    return null
}
app.get("/admin") { return "admin area" }
app.listen(8080)
```

## Comparação constant-time

```kof
if (security.constantTimeEquals(a, b)) { ... }
```

Use para comparar tokens, hashes e segredos — nunca `==` em valores
sensíveis.

## Suporte por target

| Área | JVM | Native | JS |
|------|-----|--------|----|
| passwords | ✅ | ❌ (SECN001) | ✅ |
| sha256/hmac | ✅ | ✅ | ✅ |
| aes-gcm | ✅ | ❌ (SECN002) | ❌ (SECN002) |
| sha512 | ✅ | ❌ (SECN003) | ✅ |
| jwt | ✅ | ❌ | ✅ |
| secrets | ✅ | ✅ | ✅ |
| constant-time | ✅ | ✅ | ✅ |
| auth web | ✅ | ❌ | ❌ |

Referência completa: `docs/security.md`.