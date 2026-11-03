# DSHA 0.2.1-rc.3-20261028.0843-dsh0.2.1-rc.3 / build207

- Correction du problème où la zone de saisie restait sur le viewport bas de la hauteur du clavier après un passage en arrière-plan avec le clavier ouvert puis un retour : au rétablissement du focus, la visibilité réelle du clavier logiciel est vérifiée et, s’il est absent, la marge clavier est libérée et une passe de mise en page est forcée (#190, côté natif).
- Le chemin de marges habituel avec le clavier visible reste inchangé ; la gestion des variables de viewport côté web conserve l’implémentation amont actuelle et n’est pas modifiée dans cette passe.
- Conserver dsh-web-mobile 3.2.2, DSH rc.3, l’accès local et réseau local et les dix langues. La validation sur téléphone reste suspendue.
