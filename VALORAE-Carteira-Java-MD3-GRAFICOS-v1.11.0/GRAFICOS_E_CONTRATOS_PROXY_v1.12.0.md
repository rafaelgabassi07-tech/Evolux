# Gráficos e Contratos Proxy v1.12.0

Este documento atesta as implementações feitas sobre os gráficos do App VALORAE Carteira (MD3) orientados pelo novo Proxy.

### Implementações Gráficas:
- **`LineChartView`**: Componente nativo de Canvas (Android Java) que desenha séries temporais. Usado no Modal de Ativos para renderizar as chaves `history` ou `series` do Proxy, e adaptável para `revenue` multianual caso entregue dessa forma.
- **`DonutChartView`**: Usado ostensivamente para ler e renderizar desagregações financeiras como:
	- Faturamento por Negócio (extraído de payloads `statements` ou `revenueSegment`).
	- Receita por Geografia (extraído de `revenueGeography`).
	- Alocação do portfólio.
- **Gráficos Dinâmicos e Rastreabilidade**: O app lê `appMobileSnapshot` e injeta pontuação e confiabilidade. O `SettingsFragment` possui o `Diagnóstico de Gráficos e Contratos` que valida o payload retornado da API. Sem Firebase, sem Jetpack Compose.
