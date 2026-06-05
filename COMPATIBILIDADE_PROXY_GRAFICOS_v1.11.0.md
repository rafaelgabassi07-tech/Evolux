# Compatibilidade com VALORAE Proxy v21.12.58 — gráficos e contratos

## Base auditada

- App: VALORAE Carteira Java/XML `versionName 1.11.0`.
- Proxy analisado: `VALORAE_PROXY_v21.12.58_TESTES_GRAFICOS_CORRIGIDOS.zip`.
- Release do Proxy: `21.12.58-revenue-breakdowns-app-contract`.

## Novidades do Proxy v21.12.58 consumidas pelo app

O Proxy passou a entregar blocos mais estáveis para o app:

- `appMobileSnapshot`: primeira renderização do ativo, cotação, métricas, gráficos e resumo de dividendos.
- `appPayload`: payload oficial para hidratação visual.
- `chartSeries.series`: séries de gráficos normalizadas, incluindo formatos ricos, OHLC, tabelas e mapas por data.
- `appRenderContract.chartTemplates`: orientação de renderização para line/bar/candlestick.
- `assetClassContract`: contrato especializado por classe do ativo.
- `assetClassContract.groups.statements.fields.revenueGeography`.
- `assetClassContract.groups.statements.fields.revenueByBusiness`.
- `appPayload.charts.revenueGeography`.
- `appPayload.charts.revenueByBusiness`.
- `appMobileSnapshot.revenueBreakdowns`.

## Ajustes feitos no APK

### Cliente HTTP

`ValoraeClient` passou a chamar o ativo principal com:

```text
/api/v1/asset?ticker={ticker}&view=app&complete=1&profile=full&maxChartSeries=8&chartSeriesLimit=8&maxItems=120
```

Também foram adicionados endpoints especializados:

```text
/api/v1/asset/valuation
/api/v1/asset/profitability
/api/v1/asset/debt
/api/v1/asset/statements
/api/v1/asset/peers
/api/v1/asset/source-map
/api/v1/fii/income
/api/v1/fii/portfolio
/api/v1/fii/vacancy
/api/v1/fii/communications
/api/v1/fii/checklist
```

### Headers de consumidor

O app agora envia:

```text
x-valorae-app: VALORAE-Carteira
x-valorae-channel: android-java
x-valorae-app-version: 1.11.0
x-valorae-build: v1.11.0-proxy-21.12.58-graphs
```

### Modal dos ativos

O modal agora prioriza, nesta ordem:

1. `appMobileSnapshot` para primeira leitura.
2. `appPayload` para métricas e gráficos.
3. `chartSeries.series` para gráfico de linha.
4. `assetClassContract` para dados Ação/FII.
5. Endpoints especializados como fallback/hidratação.
6. Cache local do Proxy quando a rede falhar.

## Validação do Proxy anexado

Foi executado o teste local do Proxy:

```bash
node test/revenue-breakdowns-app-contract-v21-12-58.test.js
```

Resultado:

```text
revenue-breakdowns-app-contract-v21-12-58 OK
```

Isso confirma que os blocos de faturamento por região/negócio estão presentes em `appPayload`, `appMobileSnapshot` e `assetClassContract`.

## Validações no app

- XML sem erro de parse.
- IDs Java x XML sem pendências.
- Nenhuma referência a Rankings em `app/src/main`.
- Nenhuma renderização direta de JSON bruto por `setMessage(json)`.
- Compilação sintática Java validada com stubs locais de Android/Material/org.json.

## Observação

O ambiente desta conversa não possui Android SDK/Gradle real para gerar APK final. O projeto está pronto para build no Android Studio/Google AI Studio.
