# DSHA 0.2.1-rc.3-bxroot.13-dsh0.2.1-rc.3 / build215

- Corrigido o controle de tela que ainda retornava `SCREEN_CONTROL_PAUSED` após "Retomar": retomar não limpava a flag de expansão do assistente, e o painel do assistente republica a geometria a cada mudança de layout, pausando o controle de tela de novo. Agora retomar recolhe primeiro a sessão ativa do assistente, limpando as duas flags juntas; a pausa intencional ao expandir não muda.
- Mantém do build214: runtime bxroot, compatibilidade de caminhos Termux, aceleração de inicialização e extração, comandos de chamadas nativas, dsh-web-mobile 3.2.2, DSH rc.3, acesso local e LAN, e dez idiomas.
- Esta compilação contém apenas essa correção e não foi verificada em aparelho: toque em "Retomar controle de tela do assistente" em Configurações → Permissões do dispositivo → Controle de tela e chame `/app/ui/task-begin` para confirmar que `SCREEN_CONTROL_PAUSED` não volta.
