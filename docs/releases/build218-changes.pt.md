# DSHA 0.2.1-rc.3-bxroot.16-dsh0.2.1-rc.3 / build218

- Corrigida a falha permanente de `begin` no controle de tela (o impasse `ASSISTANT_EXPANDED`): expandir o painel do assistente não pausa mais o controle de tela. Essa janela não é focável e nunca vira alvo do teclado, então a barreira era desnecessária; pior, criava um impasse — expandir definia `expanded`, a leitura de tela falhava com `ASSISTANT_LOCAL_RESUME_REQUIRED` e, com a janela removida, o painel não podia ser recolhido, então a pausa nunca era removida.
- Também removida a segunda pausa da mesma origem, disparada quando a geometria do overlay é publicada (`ASSISTANT_PANEL_EXPANDED`), e a flag de expansão que sobrava ao destruir a janela.
- Editor e voz continuam pausando o controle de tela por conta própria (`ASSISTANT_IME`, `ASSISTANT_VOICE`); esses caminhos não mudam.
- Atualizar de uma compilação antiga não trava: `collapse()` ainda remove pausas de expansão herdadas.
- No restante igual ao build214: runtime bxroot, compatibilidade de caminhos Termux, aceleração de inicialização e extração, comandos de chamadas nativas.
- Não verificado em aparelho nesta rodada. Se `SCREEN_CONTROL_PAUSED` ainda aparecer, `adb logcat -s DSHA` imprime `checkBegin refused: paused reason=...`.
