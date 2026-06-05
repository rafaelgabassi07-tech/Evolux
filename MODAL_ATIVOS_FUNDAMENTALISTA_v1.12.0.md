# Modal de Ativos Fundamentalista v1.12.0

As maiores transformações referentes à experiência e quantidade de dados do investidor foram direcionadas para o Modal de Ativo e Fragmento de Análise (`PortfolioFragment.java` & `AnalysisFragment.java`).

### Adições e Funcionalidades:
- A seção "Gráfico Histórico" desenha dinamicamente os pontos via API ou cache no Canvas.
- A fragmentou o modal para consumir as URLs e payloads `indicators`, `fundamentals`, `valuation`, `statements`, e formatá-los de forma user-friendly. Dados incluem: ROE, ROIC, Payout, Dívida Líquida/EBITDA, Liquidez Média, P/L e P/VP.
- Respostas sobre FIIs também foram especializadas (`/fii/income`, `/fii/patrimonial`, etc.) apresentando: Vacância financeira/física, Gestão e Tipo de Fundo.
- As informações nunca são apresentadas como RAW JSON, sempre em cards `MetricCard` minimalistas usando Google Material Design 3 puro.
