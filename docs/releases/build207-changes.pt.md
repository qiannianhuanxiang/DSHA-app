# DSHA 0.2.1-rc.3-20261028.0843-dsh0.2.1-rc.3 / build207

- Corrigido o problema da área de entrada permanecer no viewport baixo da altura do teclado após ir para o segundo plano com o teclado aberto e voltar: ao retomar o foco, a visibilidade real do IME é verificada e, se estiver oculto, o espaçamento do teclado é liberado e uma passagem de layout é forçada (#190, lado nativo).
- O caminho normal de espaçamentos com o IME visível permanece inalterado; o tratamento das variáveis de viewport no lado web mantém a implementação atual do upstream e não é modificado nesta rodada.
- Manter dsh-web-mobile 3.2.2, DSH rc.3, acesso local e à rede local e dez idiomas. A validação no telefone continua suspensa.
