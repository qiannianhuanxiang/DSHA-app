# DSHA 0.2.1-rc.3-bxroot.7-dsh0.2.1-rc.3 / build209

- - Correction du problème où des fenêtre système minuscule 1×1 — glyphes HyperDuo, badge de notes, etc. — déclenchaient TARGET_WINDOW_OCCLUDED sur les systèmes de type HyperOS, rendant tap et swipe par coordonnées totalement inutilisables : les fenêtres d’une aire négligeable ne participent plus à la décision d’occlusion (#212).
- - Lorsque l’arrêt d’une application est bloqué par « format de la liste d’applications non vérifiable », l’erreur embarque désormais la première ligne inattendue (masquée, tronquée) et précise la liste utilisateur ou système, afin de cerner les différences de sortie pm propres à chaque ROM (#210 complément).
- - Conserver dsh-web-mobile 3.2.2, DSH rc.3, l’accès local et réseau local et les dix langues. La validation sur téléphone reste suspendue.
