Continue o app VALORAE Carteira v1.11.0 preservando:

- Java/XML, sem Kotlin e sem Compose.
- Material Design limpo.
- Nenhuma função de Rankings por enquanto.
- Não exibir JSON bruto ao usuário.
- Consultar o Proxy automaticamente na abertura do app.
- Usar cache local do Proxy como fallback.
- Consumir `/api/v1/asset?view=app&complete=1` para detalhe rico de ativo.
- Priorizar `appMobileSnapshot`, `appPayload`, `chartSeries`, `appRenderContract` e `assetClassContract`.
- Manter backup, restauração e importação Excel B3.
- Manter lógica temporal por `purchaseDate`.

Ao evoluir o modal dos ativos, continuar adicionando dados visuais vindos do Proxy, mas sempre com fallback local e sem travar a UI.
