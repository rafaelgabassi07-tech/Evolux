# Revisão técnica v1.11.0

## Resultado

A versão v1.11.0 integra as atualizações do VALORAE Proxy v21.12.58 relacionadas a gráficos, contratos de app, contratos de classe de ativo e faturamento por região/negócio.

## Alterações principais

- `versionName`: `1.11.0`.
- `versionCode`: `13`.
- User-Agent: `VALORAE-Carteira-AndroidJava/1.11.0`.
- Headers de consumidor do app adicionados.
- Modal de ativo convertido para visual rico com cards e gráficos.
- Consumo de `view=app&complete=1` para ativos.
- Leitura de `appMobileSnapshot`, `appPayload`, `chartSeries`, `appRenderContract` e `assetClassContract`.
- Novos endpoints especializados de Ação/FII adicionados ao cliente/repositório.
- Pré-carga automática passou a incluir histórico e endpoints especializados.

## Validações executadas

- XML parseado sem erro.
- IDs usados em Java conferidos contra XML.
- Busca por Rankings em `app/src/main`: zero ocorrências.
- Busca por renderização direta de JSON bruto: sem `setMessage(json)`, sem botão “Ver JSON”.
- Proxy v21.12.58: teste `revenue-breakdowns-app-contract-v21-12-58.test.js` passou.
- Compilação sintática Java com stubs locais passou.

## Limitação

Não foi gerado APK final porque o ambiente não possui Android SDK/Gradle.
