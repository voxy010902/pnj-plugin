package fr.npcdialogue;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.*;

public final class NpcCommand implements TabExecutor {
    private static final List<String> SUBS = List.of("create", "remove", "list", "info", "tphere", "skin", "model",
            "name", "namevisible", "portrait", "hitbox", "interact", "talk", "reload", "help");
    private static final List<String> ISUBS = List.of("add-dialogue", "add-command", "addcmd", "list", "remove", "clear");
    private static final List<String> CLICKS = List.of("RIGHT", "LEFT", "ANY");

    private final NpcDialoguePlugin plugin;

    public NpcCommand(NpcDialoguePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (a.length == 0) {
            help(s);
            return true;
        }
        NpcManager m = plugin.npcs();
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "create" -> create(s, a);
            case "remove", "delete" -> {
                Npc n = need(s, a, 1);
                if (n != null) {
                    m.remove(n.id);
                    s.sendMessage(Msg.ok("PNJ supprimé : " + n.id));
                }
            }
            case "list" -> {
                s.sendMessage(Msg.info("PNJ (" + m.all().size() + ") :"));
                for (Npc n : m.all()) {
                    s.sendMessage(Msg.info(n.id + " [" + n.type + "] " + (n.type == Npc.Type.PLAYER ? n.skin : n.model)
                            + " @ " + n.world + " " + (int) n.x + " " + (int) n.y + " " + (int) n.z));
                }
            }
            case "info" -> {
                Npc n = need(s, a, 1);
                if (n != null) info(s, n);
            }
            case "tphere" -> {
                Npc n = need(s, a, 1);
                if (n != null && s instanceof Player p) {
                    n.setLocation(p.getLocation());
                    m.saveAndRespawn(n);
                    s.sendMessage(Msg.ok("PNJ déplacé."));
                } else if (n != null) s.sendMessage(Msg.err("Réservé aux joueurs."));
            }
            case "skin" -> {
                Npc n = need(s, a, 1);
                if (n == null) break;
                if (a.length < 3) { s.sendMessage(Msg.err("/npcd skin <id> <pseudo|uuid>")); break; }
                n.type = Npc.Type.PLAYER;
                n.skin = a[2];
                m.saveAndRespawn(n);
                s.sendMessage(Msg.ok("Skin appliqué : " + a[2]));
            }
            case "model" -> {
                Npc n = need(s, a, 1);
                if (n == null) break;
                if (a.length < 3) { s.sendMessage(Msg.err("/npcd model <id> <modèle ModelEngine>")); break; }
                n.type = Npc.Type.MODEL;
                n.model = a[2];
                n.nameVisible = false;
                m.saveAndRespawn(n);
                s.sendMessage(Msg.ok("Modèle appliqué : " + a[2]));
            }
            case "name" -> {
                Npc n = need(s, a, 1);
                if (n == null) break;
                if (a.length < 3) { s.sendMessage(Msg.err("/npcd name <id> <nom MiniMessage>")); break; }
                n.displayName = join(a, 2);
                m.saveAndRespawn(n);
                s.sendMessage(Msg.ok("Nom : " + n.displayName));
            }
            case "namevisible" -> {
                Npc n = need(s, a, 1);
                if (n == null) break;
                if (a.length < 3) { s.sendMessage(Msg.err("/npcd namevisible <id> <true|false>")); break; }
                n.nameVisible = Boolean.parseBoolean(a[2]);
                m.saveAndRespawn(n);
                s.sendMessage(Msg.ok("Nom visible : " + n.nameVisible));
            }
            case "portrait" -> {
                Npc n = need(s, a, 1);
                if (n == null) break;
                if (a.length < 3) { s.sendMessage(Msg.err("/npcd portrait <id> <clé|->")); break; }
                n.portrait = a[2].equals("-") ? null : a[2];
                m.save();
                s.sendMessage(Msg.ok("Portrait : " + (n.portrait == null ? "défaut" : n.portrait)));
            }
            case "hitbox" -> {
                Npc n = need(s, a, 1);
                if (n == null) break;
                try {
                    n.hitboxWidth = Double.parseDouble(a[2]);
                    n.hitboxHeight = Double.parseDouble(a[3]);
                } catch (RuntimeException ex) {
                    s.sendMessage(Msg.err("/npcd hitbox <id> <largeur> <hauteur>"));
                    break;
                }
                m.saveAndRespawn(n);
                s.sendMessage(Msg.ok("Hitbox : " + n.hitboxWidth + " x " + n.hitboxHeight));
            }
            case "interact" -> interact(s, a);
            case "talk" -> {
                Npc n = need(s, a, 1);
                if (n == null) break;
                Player t = a.length > 2 ? Bukkit.getPlayerExact(a[2]) : (s instanceof Player pl ? pl : null);
                if (t == null) { s.sendMessage(Msg.err("Joueur introuvable.")); break; }
                m.interact(t, n, "RIGHT");
            }
            case "reload" -> {
                plugin.reloadAll();
                s.sendMessage(Msg.ok("Configuration, dialogues et PNJ rechargés."));
            }
            default -> help(s);
        }
        return true;
    }

    // ───────────────────────── création ─────────────────────────

    private void create(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) {
            s.sendMessage(Msg.err("Commande réservée aux joueurs."));
            return;
        }
        if (a.length < 4) {
            s.sendMessage(Msg.err("/npcd create <id> <player|model> <pseudo-skin|modèle> [nom MiniMessage...]"));
            return;
        }
        String id = a[1].toLowerCase(Locale.ROOT);
        if (!id.matches("[a-z0-9_\\-]+")) {
            s.sendMessage(Msg.err("Id invalide (a-z, 0-9, _ et - uniquement)."));
            return;
        }
        if (plugin.npcs().get(id) != null) {
            s.sendMessage(Msg.err("Ce PNJ existe déjà."));
            return;
        }
        Npc.Type type;
        if (a[2].equalsIgnoreCase("player")) type = Npc.Type.PLAYER;
        else if (a[2].equalsIgnoreCase("model")) type = Npc.Type.MODEL;
        else {
            s.sendMessage(Msg.err("Type : player ou model."));
            return;
        }
        if (type == Npc.Type.MODEL && !ModelEngineHook.available()) {
            s.sendMessage(Msg.err("ModelEngine n'est pas installé sur le serveur."));
            return;
        }
        String name = a.length > 4 ? join(a, 4) : "<yellow>" + id;
        plugin.npcs().create(id, type, a[3], name, p.getLocation());
        s.sendMessage(Msg.ok("PNJ créé : " + id + ". Ajoute une interaction : /npcd interact " + id + " add-dialogue <dialogue>"));
    }

    private void info(CommandSender s, Npc n) {
        s.sendMessage(Msg.info("PNJ " + n.id + " [" + n.type + "] " + (n.type == Npc.Type.PLAYER ? "skin=" + n.skin : "modèle=" + n.model)));
        s.sendMessage(Msg.info("Nom : " + n.displayName + " | visible : " + n.nameVisible));
        listRules(s, n);
    }

    // ───────────────────────── interactions ─────────────────────────

    private void interact(CommandSender s, String[] a) {
        Npc n = need(s, a, 1);
        if (n == null) return;
        if (a.length < 3) {
            s.sendMessage(Msg.err("/npcd interact <id> <" + String.join("|", ISUBS) + "> ..."));
            return;
        }
        NpcManager m = plugin.npcs();
        switch (a[2].toLowerCase(Locale.ROOT)) {
            case "add-dialogue" -> {
                // add-dialogue <dialogue> [permission|-] [RIGHT|LEFT|ANY] [noeud]
                if (a.length < 4) { s.sendMessage(Msg.err("/npcd interact <id> add-dialogue <dialogue> [permission|-] [clic] [noeud]")); return; }
                Npc.Rule r = new Npc.Rule();
                r.dialogue = a[3];
                if (a.length > 4 && !a[4].equals("-")) r.permission = a[4];
                if (a.length > 5) r.click = a[5].toUpperCase(Locale.ROOT);
                if (a.length > 6) r.start = a[6];
                if (!CLICKS.contains(r.click)) { s.sendMessage(Msg.err("Clic : RIGHT, LEFT ou ANY.")); return; }
                if (plugin.dialogues().get(r.dialogue) == null) s.sendMessage(Msg.info("Attention : le dialogue '" + r.dialogue + "' n'existe pas (encore)."));
                n.rules.add(r);
                m.save();
                s.sendMessage(Msg.ok("Règle ajoutée (#" + n.rules.size() + ")."));
            }
            case "add-command" -> {
                // add-command <permission|-> <RIGHT|LEFT|ANY> <commande...>
                if (a.length < 6) { s.sendMessage(Msg.err("/npcd interact <id> add-command <permission|-> <clic> <commande...>")); return; }
                Npc.Rule r = new Npc.Rule();
                if (!a[3].equals("-")) r.permission = a[3];
                r.click = a[4].toUpperCase(Locale.ROOT);
                if (!CLICKS.contains(r.click)) { s.sendMessage(Msg.err("Clic : RIGHT, LEFT ou ANY.")); return; }
                r.commands.add(join(a, 5));
                n.rules.add(r);
                m.save();
                s.sendMessage(Msg.ok("Règle ajoutée (#" + n.rules.size() + ")."));
            }
            case "addcmd" -> {
                // addcmd <index> <commande...>  : ajoute une commande à une règle existante
                Npc.Rule r = rule(s, n, a, 3);
                if (r == null) return;
                if (a.length < 5) { s.sendMessage(Msg.err("/npcd interact <id> addcmd <index> <commande...>")); return; }
                r.commands.add(join(a, 4));
                m.save();
                s.sendMessage(Msg.ok("Commande ajoutée."));
            }
            case "list" -> listRules(s, n);
            case "remove" -> {
                Npc.Rule r = rule(s, n, a, 3);
                if (r == null) return;
                n.rules.remove(r);
                m.save();
                s.sendMessage(Msg.ok("Règle supprimée."));
            }
            case "clear" -> {
                n.rules.clear();
                m.save();
                s.sendMessage(Msg.ok("Toutes les règles supprimées."));
            }
            default -> s.sendMessage(Msg.err("Sous-commande inconnue."));
        }
    }

    private Npc.Rule rule(CommandSender s, Npc n, String[] a, int idx) {
        try {
            int i = Integer.parseInt(a[idx]) - 1;
            return n.rules.get(i);
        } catch (RuntimeException ex) {
            s.sendMessage(Msg.err("Index de règle invalide (voir /npcd interact " + n.id + " list)."));
            return null;
        }
    }

    private void listRules(CommandSender s, Npc n) {
        if (n.rules.isEmpty()) {
            s.sendMessage(Msg.info("Aucune règle d'interaction."));
            return;
        }
        int i = 1;
        for (Npc.Rule r : n.rules) {
            s.sendMessage(Msg.info("#" + i++ + " perm=" + (r.permission == null ? "(défaut)" : r.permission)
                    + " clic=" + r.click
                    + (r.dialogue != null ? " dialogue=" + r.dialogue + (r.start != null ? "/" + r.start : "") : "")
                    + (r.commands.isEmpty() ? "" : " commandes=" + r.commands)));
        }
    }

    // ───────────────────────── utilitaires ─────────────────────────

    private Npc need(CommandSender s, String[] a, int idx) {
        if (a.length <= idx) {
            s.sendMessage(Msg.err("Id de PNJ manquant."));
            return null;
        }
        Npc n = plugin.npcs().get(a[idx]);
        if (n == null) s.sendMessage(Msg.err("PNJ introuvable : " + a[idx]));
        return n;
    }

    static String join(String[] a, int from) {
        return String.join(" ", Arrays.copyOfRange(a, from, a.length));
    }

    private void help(CommandSender s) {
        for (String l : List.of(
                "/npcd create <id> <player|model> <pseudo|modèle> [nom]",
                "/npcd remove|info|tphere|talk <id>",
                "/npcd skin <id> <pseudo|uuid>   |   /npcd model <id> <modèle>",
                "/npcd name <id> <nom>   |   namevisible <id> <true|false>   |   portrait <id> <clé|->",
                "/npcd hitbox <id> <largeur> <hauteur>   (PNJ modèle)",
                "/npcd interact <id> add-dialogue <dialogue> [permission|-] [RIGHT|LEFT|ANY] [noeud]",
                "/npcd interact <id> add-command <permission|-> <RIGHT|LEFT|ANY> <commande>",
                "/npcd interact <id> addcmd <index> <commande>   |   list   |   remove <index>   |   clear",
                "/npcd list   |   /npcd reload")) {
            s.sendMessage(Msg.info(l));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (a.length == 1) return filter(SUBS, a[0]);
        String sub = a[0].toLowerCase(Locale.ROOT);
        if (a.length == 2 && !Set.of("create", "list", "reload", "help").contains(sub)) return filter(plugin.npcs().ids(), a[1]);
        if (sub.equals("create") && a.length == 3) return filter(List.of("player", "model"), a[2]);
        if (sub.equals("skin") && a.length == 3 || sub.equals("talk") && a.length == 3) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), a[2]);
        }
        if (sub.equals("namevisible") && a.length == 3) return filter(List.of("true", "false"), a[2]);
        if (sub.equals("interact")) {
            if (a.length == 3) return filter(ISUBS, a[2]);
            String is = a[2].toLowerCase(Locale.ROOT);
            if (is.equals("add-dialogue")) {
                if (a.length == 4) return filter(plugin.dialogues().ids(), a[3]);
                if (a.length == 6) return filter(CLICKS, a[5]);
            }
            if (is.equals("add-command") && a.length == 5) return filter(CLICKS, a[4]);
        }
        return List.of();
    }

    static List<String> filter(Collection<String> src, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return src.stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith(p)).sorted().toList();
    }
}
