# Auditoria de funcionamento — VALORAE Carteira v1.10.0

## Objetivo
Verificar se as informações críticas do VALORAE Proxy estão contempladas no APK, com foco em:

- consulta automática ao abrir e ao retornar ao app;
- cotações/snapshots atuais dos ativos;
- carteira consolidada;
- proventos futuros;
- proventos passados/recebidos;
- gráficos e histórico;
- ausência completa de Rankings no APK.

## Resultado da auditoria local

### Código do APK
- `versionName`: `1.10.0`.
- `versionCode`: `12`.
- User-Agent: `VALORAE-Carteira-AndroidJava/1.10.0`.
- XML validado sem erro de parse.
- Todos os IDs chamados no Java existem nos layouts/menus.
- Nenhuma referência a Rankings em `app/src/main`.
- Nenhum fluxo de tela exibe JSON bruto ao usuário.

### Consulta automática
O app consulta o Proxy automaticamente:

- ao abrir/entrar no aplicativo (`MainActivity.onResume`);
- respeitando intervalo mínimo via `StartupSyncStore` para não bombardear o Proxy;
- salvando respostas válidas no `ProxyDataCache`;
- mantendo fallback de cache/local quando a rede falha.

### Rotas automáticas auditadas no app
A rotina `syncOnAppOpen()` cobre:

- `/api/v1/ready`
- `/api/v1/manifest`
- `/api/v1/source/status`
- `/api/v1/assets`
- `/api/v1/portfolio/summary`
- `/api/v1/portfolio/allocation`
- `/api/v1/portfolio/income`
- `/api/v1/portfolio/dividends`
- `/api/v1/portfolio/risk`
- `/api/v1/portfolio/history`
- `/api/v1/portfolio/events`
- `/api/v1/portfolio/next-dividends`
- `/api/v1/portfolio/rebalance`
- `/api/v1/market/indices`
- `/api/v1/news`
- `/api/v1/watchlist/analyze`
- pré-carga de detalhes dos ativos da carteira.

## Correção aplicada nesta versão
Na v1.9.0 a rota `/portfolio/dividends` existia no cliente, mas não estava sendo usada na abertura automática nem renderizada na tela inicial como histórico da carteira. A v1.10.0 corrige isso.

Agora o app separa claramente:

- **Próximos proventos**: `/api/v1/portfolio/next-dividends`.
- **Últimos proventos recebidos**: `/api/v1/portfolio/dividends`.
- **Proventos por ativo**: `/api/v1/asset/dividends?ticker=...` ao tocar no ativo.

## Verificação do Proxy empacotado
O ZIP do VALORAE Proxy fornecido no projeto contém as rotas necessárias:

- `routes/portfolio/dividends.js`
- `routes/portfolio/next-dividends.js`
- `routes/portfolio/income.js`
- `routes/portfolio/history.js`
- `routes/asset/dividends.js`
- `routes/asset/history.js`
- `routes/assets.js`
- `routes/market/indices.js`
- `routes/watchlist/analyze.js`

## Limitação do ambiente atual
Este ambiente não conseguiu resolver DNS externo para `servidor-valorae.vercel.app`, então o teste HTTP real contra o deploy público precisa ser executado no aparelho/emulador ou Android Studio. O app inclui a auditoria interna em **Mais > Testar chegada das informações** para executar essa validação com internet real.

## Conclusão
A estrutura do app agora está preparada para receber e renderizar as informações críticas do Proxy, incluindo proventos futuros e passados. A validação online final deve ser feita no Android Studio/dispositivo porque o ambiente atual não possui rede externa confiável nem Android SDK.
