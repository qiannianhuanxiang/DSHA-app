# DSHA 0.2.1-rc.3-bxroot.15-dsh0.2.1-rc.3 / build217

- Corrigida a causa estrutural de as pausas do controle de tela não serem removidas: havia 11 motivos de pausa, mas a maioria sem caminho de remoção, e nada registrava se a condição da pausa ainda valia. Agora os componentes publicam sua condição (painel expandido, editor visível, reconhecimento de voz ativo, overlay anexado) e a pausa só é removida quando a condição realmente terminou; resposta ausente conta como ainda pausado.
- Corrigido o ditado por voz: tocar no microfone pausava o controle de tela, mas nada removia a pausa depois que a interface de voz saía, restando apenas encerrar o processo. Agora é removida quando o reconhecimento termina.
- Corrigido o campo de texto: abrir o compositor pausava o controle de tela e fechá-lo deixava a pausa. Fechar o editor agora a remove.
- Corrigido o painel flutuante do assistente: expandir e recolher não deixa mais uma pausa `ASSISTANT_PANEL_EXPANDED` para trás.
- Pausas do usuário, regras de notificação de parada, políticas e falhas de estado continuam nunca sendo removidas automaticamente.
- No restante igual ao build214: runtime bxroot, compatibilidade de caminhos Termux, aceleração de inicialização e extração, comandos de chamadas nativas.
- Não verificado em aparelho nesta rodada. Se `SCREEN_CONTROL_PAUSED` ainda aparecer, `adb logcat -s DSHA` imprime `checkBegin refused: paused reason=...` e indica o motivo.
