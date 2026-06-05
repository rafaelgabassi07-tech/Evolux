# Auditoria de compatibilidade Proxy/APK — v1.8.0

## Resultado

A v1.8.0 mantém a integração com o VALORAE Proxy e adiciona sincronização manual com cache detalhado.

## Verificações realizadas no pacote

- `versionName` atualizado para `1.8.0`.
- User-Agent atualizado para `VALORAE-Carteira-AndroidJava/1.8.0`.
- Parser continua aceitando envelope direto e wrappers como `data`, `result`, `results` e `payload`.
- Payload de carteira mantém aliases temporais de data de compra.
- Tela de Configurações recebeu seção de sincronização inteligente.
- Cache seletivo preserva diagnósticos estáveis e invalida dados dependentes da carteira.
- Código Java/XML continua sem Compose e sem Kotlin como base.

## Pontos de atenção para Android Studio / Google AI Studio

Executar:

```bash
./gradlew assembleDebug
```

Depois testar no app:

1. abrir **Mais**;
2. tocar em **Sincronizar agora com o Proxy**;
3. tocar em **Testar chegada das informações**;
4. verificar se as rotas críticas aparecem com status OK ou pendência específica;
5. abrir Início, Carteira, Análise e Mercado para validar renderização final.

## Política de dados

O APK não inventa dado remoto. Quando o Proxy não responde, o app mostra informação local, cache identificado ou mensagem de indisponibilidade.
