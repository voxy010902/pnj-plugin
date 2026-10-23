package fr.npcdialogue;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Exécute des actions. Format : "[type] contenu"
 *   [console] dm open shop %player%     commande console
 *   [player]  warp spawn                commande exécutée par le joueur (défaut si pas de préfixe)
 *   [op]      gamemode creative         commande en OP temporaire
 *   [message] <green>Texte              message au joueur
 *   [sound]   entity.player.levelup     son
 *   [dialogue] autre_dialogue [noeud]   ouvre un autre dialogue
 *   [close]                             ferme le dialogue
 */
public final class Actions {
    private Actions() {}

    public static void run(NpcDialoguePlugin plugin, Player p, Npc npc, List<String> actions) {
        for (String raw : actions) {
            String line = Text.apply(raw, p, npc).trim();
            if (line.isEmpty()) continue;
            String type = "player";
            if (line.startsWith("[")) {
                int end = line.indexOf(']');
                if (end > 0) {
                    type = line.substring(1, end).trim().toLowerCase();
                    line = line.substring(end + 1).trim();
                }
            }
            try {
                switch (type) {
                    case "console" -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), strip(line));
                    case "op" -> {
                        boolean was = p.isOp();
                        try {
                            p.setOp(true);
                            p.performCommand(strip(line));
                        } finally {
                            p.setOp(was);
                        }
                    }
                    case "message" -> p.sendMessage(Text.component(line, p, npc));
                    case "sound" -> p.playSound(p.getLocation(), line, 1f, 1f);
                    case "close" -> p.closeDialog();
                    case "dialogue" -> {
                        String[] parts = line.split("\\s+");
                        plugin.ui().open(p, npc, parts[0], parts.length > 1 ? parts[1] : null);
                    }
                    default -> p.performCommand(strip(line));
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("Action '" + raw + "' en échec : " + ex.getMessage());
            }
        }
    }

    private static String strip(String s) {
        return s.startsWith("/") ? s.substring(1) : s;
    }
}
