# NpcDialogue (Paper 26.2 / Java 25)

Compilation : `mvn clean package` → `target/NpcDialogue-1.0.0.jar` dans `plugins/`.

## Fonctionnement
- **PNJ skin joueur** : entité `Mannequin` (skin résolu via Mojang : pseudo ou UUID). Aucun paquet / NMS.
- **PNJ ModelEngine** : ArmorStand invisible portant le modèle + hitbox `Interaction` cliquable (réflexion, pas de dépendance).
- **Dialogues** : écrans `Dialog` natifs de Paper, habillés par le resource pack (portrait, séparateur, flèche, boutons).
- **Permissions** : LuckPerms alimente les permissions Bukkit ; `!perm` = "ne doit pas avoir".
- Fichiers : `plugins/NpcDialogue/{config.yml, npcs.yml, dialogues/*.yml}`.

## Resource pack
`NpcDialogue-ResourcePack.zip` → hébergé quelque part, puis dans `server.properties` :
`resource-pack=<url>` et `resource-pack-sha1=04a0a0a372c61a9f1850b8bbf067aa515ac0315b` (à recalculer si tu modifies le zip).

## Démarrage rapide
```
/dialogue test example
/npcd create garde player Notch <gold>Garde
/npcd interact garde add-dialogue example
# autre dialogue pour ceux qui ont une perm (prioritaire sur la règle par défaut) :
/npcd interact garde add-dialogue example_vip group.vip
# sans dialogue : juste une commande / un menu DeluxeMenus :
/npcd interact garde add-command - RIGHT [console] dm open shop %player%
/npcd interact garde add-command npcdialogue.admin LEFT [player] say Salut staff
# PNJ modèle :
/npcd create mage model mon_modele <purple>Mage
/npcd hitbox mage 1.0 2.2
```
Règle choisie : première règle **avec permission** satisfaite (ordre `/npcd interact <id> list`), sinon la règle **sans permission**.

## Créer un dialogue en jeu
```
/dialogue create forge
/dialogue node add forge intro Bienvenue %player% !\nQue veux-tu ?
/dialogue choice add forge intro suite Je veux voir tes armes
/dialogue choice add forge intro close Rien, merci
/dialogue node add forge suite Voici ce que j'ai.
/dialogue choice add forge suite close Ouvrir la boutique
/dialogue choice command forge suite 1 [console] dm open forge %player%
/dialogue choice perm forge intro 1 !group.banni      # choix masqué si perm
/dialogue node add forge router
/dialogue node branch forge router group.vip vip      # redirection par permission
/dialogue node branch forge router - intro
/dialogue start forge router
```
Tout est aussi éditable à la main dans `dialogues/<id>.yml` puis `/dialogue reload`.

## Actions (commandes de choix / règles / noeuds)
`[console] cmd` · `[player] cmd` (défaut) · `[op] cmd` · `[message] texte` · `[sound] id` · `[dialogue] id [noeud]` · `[close]`
Placeholders : `%player%`, `%npc%`, `%npc_name%` (+ PlaceholderAPI). Pour ouvrir un menu depuis un choix, mets `close: true`.

Permissions : `npcdialogue.admin` (op), `npcdialogue.interact` (tous).
