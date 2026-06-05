# Sincronização Proxy ↔ APK — v1.8.0

## Objetivo

Garantir que o usuário consiga atualizar as informações críticas do VALORAE Proxy em uma ação manual dentro do app, com resultado legível e cache local preparado para falhas temporárias de rede.

## Implementado

### 1. Botão de sincronização

Em **Configurações/Mais**, foi adicionada a ação **Sincronizar agora com o Proxy**.

Ela chama as rotas principais de conectividade, carteira, ativos, mercado, notícias e watchlist.

### 2. Cache local mais inteligente

`ProxyDataCache` agora informa:

- quantidade de respostas salvas;
- idade da resposta mais nova;
- idade da resposta mais antiga;
- tamanho aproximado do cache;
- lista resumida das chaves guardadas.

### 3. Invalidação seletiva

Quando a carteira muda, o app não apaga mais todo o cache. Ele remove apenas respostas dependentes de carteira, ativos, notícias, watchlist e detalhe de ativo. Diagnósticos estáveis como `ready`, `manifest`, `fields` e `source/status` são preservados.

### 4. Relatório de sincronização

A sincronização mostra no app:

- quantidade de integrações válidas;
- estado do cache após a atualização;
- resultado por rota;
- pendências quando alguma rota falha.

## Rotas sincronizadas

- ready
- manifest
- source/status
- assets
- portfolio/summary
- portfolio/allocation
- portfolio/income
- portfolio/risk
- portfolio/history
- portfolio/events
- portfolio/next-dividends
- portfolio/rebalance
- market/indices
- news
- watchlist/analyze

## Observação

O app continua sem uso de rotas de listas ranqueadas/de destaque na interface Android.
