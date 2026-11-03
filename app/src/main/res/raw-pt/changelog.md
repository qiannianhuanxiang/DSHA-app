# DSHA 0.2.1-rc.3-bxroot.14-dsh0.2.1-rc.3 / build216

- Corrigida a falha permanente de `begin` no controle de tela: a verificação da notificação de parada comparava um id fixo, mas a notificação é publicada com outro id, então toda chamada era tratada como "notificação de parada ausente" e pausava o controle de tela. Nada limpava essa pausa depois que o serviço do assistente saía, e reiniciar o app ajudava só por pouco tempo. Agora a verificação usa a ação de parada, e esse motivo exato de pausa é liberado quando o serviço que o exigia não existe mais; pausas do usuário e outros motivos não são afetados.
- Mantém a correção do build215: retomar o controle de tela recolhe primeiro a sessão do assistente, para que a flag de expansão não pause de novo o controle recém-retomado.
- No restante igual ao build214: runtime bxroot, compatibilidade de caminhos Termux, aceleração de inicialização e extração, comandos de chamadas nativas.
- Não verificado em aparelho nesta rodada: após instalar, chame `/app/ui/task-begin` e confirme que `SCREEN_CONTROL_PAUSED` não aparece mais.
