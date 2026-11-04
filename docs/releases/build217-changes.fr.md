# DSHA 0.2.1-rc.3-bxroot.15-dsh0.2.1-rc.3 / build217

- Correction de la cause structurelle empêchant de lever les pauses du contrôle d'écran : il existait 11 motifs de pause mais la plupart sans chemin de levée, et rien n'enregistrait si la condition de la pause tenait encore. Les composants publient désormais leur condition (panneau déployé, éditeur visible, reconnaissance vocale active, superposition attachée) et la pause n'est levée que lorsque la condition a réellement pris fin ; une réponse absente compte comme pause maintenue.
- Correction de la saisie vocale : toucher le micro mettait le contrôle d'écran en pause, mais rien ne la levait après la fermeture de l'interface vocale, seul l'arrêt du processus aidait. La levée a maintenant lieu à la fin de la reconnaissance.
- Correction de la saisie de texte : ouvrir le composeur mettait en pause et le fermer laissait la pause. Fermer l'éditeur la lève désormais.
- Correction du panneau flottant de l'assistant : déployer puis replier ne laisse plus de pause `ASSISTANT_PANEL_EXPANDED` derrière soi.
- Les pauses de l'utilisateur, les règles de notification d'arrêt, les politiques et les échecs d'état ne sont toujours jamais levés automatiquement.
- Pour le reste identique au build214 : runtime bxroot, compatibilité des chemins Termux, accélération du démarrage et de l'extraction, commandes d'appels natifs.
- Non vérifié sur appareil cette fois. Si `SCREEN_CONTROL_PAUSED` apparaît encore, `adb logcat -s DSHA` affiche `checkBegin refused: paused reason=...` et nomme la cause.
