# Melhorias do modal de ativos — v1.11.0

## O que mudou

O modal de ativo deixou de ser texto simples e passou a ser uma área visual rica, montada dinamicamente em Java/XML.

## Dados exibidos

### Posição do usuário

- Classe do ativo.
- Quantidade.
- Preço médio.
- Data inicial de compra.
- Tempo de posse.
- Valor investido.
- Valor atual estimado.
- Resultado absoluto e percentual.

### Cotação e indicadores

- Preço atual.
- Variação percentual.
- Dividend Yield.
- P/VP.
- P/L.
- ROE.
- ROIC.
- Margem líquida.
- Payout.
- Dívida líquida/EBITDA.
- Liquidez média.

### Ações

- Receita líquida.
- Lucro líquido.
- Ativos.
- Patrimônio líquido.
- Segmento de listagem.
- Faturamento por região.
- Faturamento por negócio.

### FIIs

- VP por cota.
- Patrimônio.
- Vacância física.
- Vacância financeira.
- Tipo do fundo.
- Gestão.
- Rendimentos e dados patrimoniais.

### Gráficos

- Gráfico de linha com séries vindas do Proxy.
- Donut chart para faturamento por região.
- Donut chart para faturamento por negócio.

## Fontes de dados priorizadas

- `/api/v1/asset?view=app`.
- `/api/v1/asset/history`.
- `/api/v1/asset/dividends`.
- `/api/v1/asset/source-map`.
- `/api/v1/asset/valuation`.
- `/api/v1/asset/profitability`.
- `/api/v1/asset/debt`.
- `/api/v1/asset/statements`.
- `/api/v1/fii/income`.
- `/api/v1/fii/patrimonial`.
- `/api/v1/fii/portfolio`.
- `/api/v1/fii/vacancy`.

## Política de fallback

Se o Proxy falhar, o app mantém:

- posição local;
- preço médio;
- quantidade;
- data de compra;
- cache salvo de respostas anteriores;
- mensagem visual de modo offline/cache.
