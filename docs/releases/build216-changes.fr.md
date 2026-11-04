# DSHA 0.2.1-rc.3-bxroot.14-dsh0.2.1-rc.3 / build216

- Correction : `begin` échouait définitivement pour le contrôle d'écran — la vérification de la notification d'arrêt comparait un id fixe, alors que la notification est publiée sous un autre id, donc chaque appel était traité comme « notification d'arrêt absente » et mettait le contrôle en pause. Rien ne levait cette pause après la sortie du service de l'assistant, et redémarrer l'application n'aidait que brièvement. La vérification repose maintenant sur l'action d'arrêt, et ce motif de pause précis est levé lorsque le service qui l'exigeait n'existe plus ; les pauses de l'utilisateur et les autres motifs restent intacts.
- Conserve le correctif du build215 : reprendre le contrôle d'écran replie d'abord la session de l'assistant, pour que l'indicateur d'extension ne remette pas en pause le contrôle qui vient d'être repris.
- Pour le reste identique au build214 : runtime bxroot, compatibilité des chemins Termux, accélération du démarrage et de l'extraction, commandes d'appels natifs.
- Non vérifié sur appareil cette fois : après installation, appelez `/app/ui/task-begin` et confirmez que `SCREEN_CONTROL_PAUSED` n'apparaît plus.
