NpcDialogue - Resource pack
Police  npcdialog:ui : \uE000 portrait | \uE001 separateur | \uE002 fleche | \uE003 bulle
Boutons : remplace les sprites widget/button* (supprime ce dossier si tu ne veux pas changer les boutons du jeu).
Nouveau portrait : ajoute une image (ex 32x32) dans assets/npcdialog/textures/font/, un provider "bitmap" dans font/ui.json
avec un nouveau caractere (\uE010...), puis declare-le dans config.yml (ui.portraits.<nom>: "\uE010").
