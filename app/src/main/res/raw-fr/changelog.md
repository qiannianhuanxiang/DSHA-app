# DSHA 0.2.1-rc.3-bxroot.16-dsh0.2.1-rc.3 / build218

- Correction de l'échec définitif de `begin` pour le contrôle d'écran (interblocage `ASSISTANT_EXPANDED`) : déployer le panneau de l'assistant ne met plus le contrôle d'écran en pause. Cette fenêtre n'est pas focalisable et ne devient jamais la cible du clavier, donc cette barrière était inutile ; pire, elle provoquait un interblocage — le déploiement posait `expanded`, la lecture d'écran échouait avec `ASSISTANT_LOCAL_RESUME_REQUIRED` et, la fenêtre retirée, le panneau ne pouvait plus être replié, donc la pause n'était jamais levée.
- Suppression également de la seconde pause de même origine, déclenchée à la publication de la géométrie de la superposition (`ASSISTANT_PANEL_EXPANDED`), et de l'indicateur d'extension qui subsistait à la destruction de la fenêtre.
- L'éditeur et la voix mettent toujours en pause de leur côté (`ASSISTANT_IME`, `ASSISTANT_VOICE`) ; ces chemins ne changent pas.
- Mettre à jour depuis une ancienne compilation ne bloque pas : `collapse()` lève toujours les pauses d'extension héritées.
- Pour le reste identique au build214 : runtime bxroot, compatibilité des chemins Termux, accélération du démarrage et de l'extraction, commandes d'appels natifs.
- Non vérifié sur appareil cette fois. Si `SCREEN_CONTROL_PAUSED` apparaît encore, `adb logcat -s DSHA` affiche `checkBegin refused: paused reason=...`.
