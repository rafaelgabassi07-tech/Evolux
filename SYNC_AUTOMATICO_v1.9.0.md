# VALORAE Carteira v1.9.0 — Sincronização automática com o Proxy

## Objetivo

Esta versão transforma a integração com o VALORAE Proxy em comportamento padrão de entrada: ao abrir o aplicativo, o APK consulta automaticamente o Proxy em segundo plano, atualiza o cache local e deixa as telas prontas para renderizar informações finais ao usuário.

## O que foi implementado

- `MainActivity` inicia uma sincronização automática no `onCreate`.
- `StartupSyncStore` registra estado, última execução, sucesso parcial/total e falhas da sincronização automática.
- `PortfolioRepository.syncOnAppOpen()` executa a consulta automática das rotas críticas.
- A barra superior mostra status como:
  - `Consultando Proxy automaticamente…`
  - `Proxy atualizado automaticamente`
  - `Proxy atualizado parcialmente`
  - `Proxy offline • cache local preservado`
- A tela **Mais / Configurações** mostra o status da última consulta automática.
- A tela **Início** exibe o resumo compacto da consulta automática junto ao status do Proxy.

## Rotas consultadas automaticamente

- `/api/v1/ready`
- `/api/v1/manifest`
- `/api/v1/source/status`
- `/api/v1/assets`
- `/api/v1/portfolio/summary`
- `/api/v1/portfolio/allocation`
- `/api/v1/portfolio/income`
- `/api/v1/portfolio/risk`
- `/api/v1/portfolio/history`
- `/api/v1/portfolio/events`
- `/api/v1/portfolio/next-dividends`
- `/api/v1/portfolio/rebalance`
- `/api/v1/market/indices`
- `/api/v1/news`
- `/api/v1/watchlist/analyze`

## Pré-carga de detalhes dos ativos

A nova função `preloadPortfolioAssetDetails(6)` pré-carrega os dados dos primeiros ativos da carteira:

- cartão do ativo;
- indicadores;
- dividendos;
- fundamentos para ações/ETFs;
- perfil e indicadores para FIIs.

Isso melhora a abertura da tela de detalhe do ativo e aumenta a chance de o app permanecer útil mesmo quando o Proxy ou a internet falham depois.

## Regras preservadas

- Nada de Rankings no APK.
- Nada de JSON bruto nas telas.
- Java/XML preservado.
- Sem Firebase.
- Sem dependências pagas.
- Sem Compose/Kotlin como base.
- Cache local continua sendo fallback das respostas válidas do Proxy.

## Controle de repetição

A consulta automática evita duplicar chamadas em rotações de tela ou recriações rápidas. Ela pode rodar novamente após alguns minutos ou quando o usuário força a sincronização manual em **Mais**.
