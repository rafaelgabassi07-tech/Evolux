# Melhorias v1.10.0

## Proventos futuros e passados
- A tela Início agora possui duas seções separadas:
  - Próximos proventos.
  - Últimos proventos recebidos.
- A abertura automática agora consulta também `/api/v1/portfolio/dividends`.
- A auditoria manual também testa `/api/v1/portfolio/dividends`.

## Atualização automática mais forte
- A consulta automática foi movida para `onResume`, permitindo nova checagem quando o usuário retorna ao app.
- O intervalo mínimo continua protegido por `StartupSyncStore`, evitando excesso de chamadas.

## Integração Proxy preservada
- Cache local preservado.
- Fallback offline/local preservado.
- Backup, restauração e importação B3 preservados.
- Nenhuma função de Rankings foi adicionada.

## Versão
- `versionName`: `1.10.0`
- `versionCode`: `12`
- User-Agent: `VALORAE-Carteira-AndroidJava/1.10.0`
