package fr.npcdialogue;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.*;

public final class DialogueCommand implements TabExecutor {
    private static final List<String> SUBS = List.of("create", "delete", "list", "show", "start", "node", "choice", "test", "reload", "help");
    private static final List<String> NODE_SUBS = List.of("add", "remove", "text", "speaker", "portrait", "next", "command", "clearcommands", "branch", "clearbranches");
    private static final List<String> CHOICE_SUBS = List.of("add", "remove", "text", "perm", "command", "close", "next");

    private final NpcDialoguePlugin plugin;

    public DialogueCommand(NpcDialoguePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (a.length == 0) {
            help(s);
            return true;
        }
        DialogueManager dm = plugin.dialogues();
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "create" -> {
                if (a.length < 2) { s.sendMessage(Msg.err("/dialogue create <id>")); break; }
                String id = a[1];
                if (!valid(id)) { s.sendMessage(Msg.err("Id invalide (lettres, chiffres, _ et -).")); break; }
                if (dm.get(id) != null) { s.sendMessage(Msg.err("Ce dialogue existe déjà.")); break; }
                dm.create(id);
                s.sendMessage(Msg.ok("Dialogue créé. Ajoute un noeud : /dialogue node add " + id + " intro Bonjour !"));
            }
            case "delete" -> {
                if (a.length < 2) { s.sendMessage(Msg.err("/dialogue delete <id>")); break; }
                s.sendMessage(dm.delete(a[1]) ? Msg.ok("Dialogue supprimé.") : Msg.err("Introuvable."));
            }
            case "list" -> {
                s.sendMessage(Msg.info("Dialogues (" + dm.all().size() + ") : " + String.join(", ", dm.ids())));
            }
            case "show" -> {
                Dialogue d = need(s, a, 1);
                if (d != null) show(s, d);
            }
            case "start" -> {
                Dialogue d = need(s, a, 1);
                Dialogue.Node n = d == null ? null : node(s, d, a, 2);
                if (n != null) {
                    d.start = n.id;
                    dm.save(d);
                    s.sendMessage(Msg.ok("Noeud de départ : " + n.id));
                }
            }
            case "node" -> node(s, a);
            case "choice" -> choice(s, a);
            case "test" -> {
                if (!(s instanceof Player p)) { s.sendMessage(Msg.err("Réservé aux joueurs.")); break; }
                if (a.length < 2) { s.sendMessage(Msg.err("/dialogue test <id> [noeud]")); break; }
                plugin.ui().open(p, null, a[1], a.length > 2 ? a[2] : null);
            }
            case "reload" -> {
                dm.load();
                s.sendMessage(Msg.ok("Dialogues rechargés (" + dm.all().size() + ")."));
            }
            default -> help(s);
        }
        return true;
    }

    // ───────────────────────── /dialogue node ─────────────────────────

    private void node(CommandSender s, String[] a) {
        if (a.length < 4) {
            s.sendMessage(Msg.err("/dialogue node <" + String.join("|", NODE_SUBS) + "> <dialogue> <noeud> ..."));
            return;
        }
        Dialogue d = need(s, a, 2);
        if (d == null) return;
        String sub = a[1].toLowerCase(Locale.ROOT);
        String nodeId = a[3];
        DialogueManager dm = plugin.dialogues();

        if (sub.equals("add")) {
            if (!valid(nodeId)) { s.sendMessage(Msg.err("Id de noeud invalide.")); return; }
            if (d.nodes.containsKey(nodeId)) { s.sendMessage(Msg.err("Ce noeud existe déjà.")); return; }
            Dialogue.Node n = new Dialogue.Node(nodeId);
            if (a.length > 4) n.text.addAll(paragraphs(NpcCommand.join(a, 4)));
            d.nodes.put(nodeId, n);
            if (d.start == null) d.start = nodeId;
            dm.save(d);
            s.sendMessage(Msg.ok("Noeud créé : " + nodeId));
            return;
        }

        Dialogue.Node n = d.nodes.get(nodeId);
        if (n == null) { s.sendMessage(Msg.err("Noeud introuvable : " + nodeId)); return; }

        switch (sub) {
            case "remove" -> {
                d.nodes.remove(nodeId);
                if (nodeId.equals(d.start)) d.start = d.nodes.isEmpty() ? null : d.nodes.keySet().iterator().next();
                s.sendMessage(Msg.ok("Noeud supprimé."));
            }
            case "text" -> {
                if (a.length < 5) { s.sendMessage(Msg.err("/dialogue node text <dialogue> <noeud> <texte> (\\n = nouveau paragraphe)")); return; }
                n.text.clear();
                n.text.addAll(paragraphs(NpcCommand.join(a, 4)));
                s.sendMessage(Msg.ok("Texte modifié."));
            }
            case "speaker" -> {
                if (a.length < 5) { s.sendMessage(Msg.err("/dialogue node speaker <dialogue> <noeud> <nom|->")); return; }
                String v = NpcCommand.join(a, 4);
                n.speaker = v.equals("-") ? null : v;
                s.sendMessage(Msg.ok("Orateur modifié."));
            }
            case "portrait" -> {
                if (a.length < 5) { s.sendMessage(Msg.err("/dialogue node portrait <dialogue> <noeud> <clé|->")); return; }
                n.portrait = a[4].equals("-") ? null : a[4];
                s.sendMessage(Msg.ok("Portrait modifié."));
            }
            case "next" -> {
                if (a.length < 5) { s.sendMessage(Msg.err("/dialogue node next <dialogue> <noeud> <suivant|->")); return; }
                n.next = a[4].equals("-") ? null : a[4];
                s.sendMessage(Msg.ok("Suivant : " + (n.next == null ? "aucun" : n.next)));
            }
            case "command" -> {
                if (a.length < 5) { s.sendMessage(Msg.err("/dialogue node command <dialogue> <noeud> <commande>")); return; }
                n.commands.add(NpcCommand.join(a, 4));
                s.sendMessage(Msg.ok("Commande ajoutée (exécutée à l'affichage du noeud)."));
            }
            case "clearcommands" -> {
                n.commands.clear();
                s.sendMessage(Msg.ok("Commandes supprimées."));
            }
            case "branch" -> {
                if (a.length < 6) { s.sendMessage(Msg.err("/dialogue node branch <dialogue> <noeud> <permission|-> <noeud-cible>")); return; }
                n.branches.add(new Dialogue.Branch(a[4].equals("-") ? null : a[4], a[5]));
                s.sendMessage(Msg.ok("Branche ajoutée (la 1re qui correspond gagne ; '-' = par défaut)."));
            }
            case "clearbranches" -> {
                n.branches.clear();
                s.sendMessage(Msg.ok("Branches supprimées."));
            }
            default -> {
                s.sendMessage(Msg.err("Sous-commande inconnue."));
                return;
            }
        }
        dm.save(d);
    }

    // ───────────────────────── /dialogue choice ─────────────────────────

    private void choice(CommandSender s, String[] a) {
        if (a.length < 4) {
            s.sendMessage(Msg.err("/dialogue choice <" + String.join("|", CHOICE_SUBS) + "> <dialogue> <noeud> ..."));
            return;
        }
        Dialogue d = need(s, a, 2);
        if (d == null) return;
        Dialogue.Node n = node(s, d, a, 3);
        if (n == null) return;
        String sub = a[1].toLowerCase(Locale.ROOT);

        if (sub.equals("add")) {
            // choice add <dialogue> <noeud> <suivant|close> <texte...>
            if (a.length < 6) { s.sendMessage(Msg.err("/dialogue choice add <dialogue> <noeud> <suivant|close> <texte>")); return; }
            Dialogue.Choice c = new Dialogue.Choice();
            if (a[4].equalsIgnoreCase("close")) c.close = true;
            else c.next = a[4];
            c.text = NpcCommand.join(a, 5);
            n.choices.add(c);
            plugin.dialogues().save(d);
            s.sendMessage(Msg.ok("Choix #" + n.choices.size() + " ajouté."));
            return;
        }

        Dialogue.Choice c;
        try {
            c = n.choices.get(Integer.parseInt(a[4]) - 1);
        } catch (RuntimeException ex) {
            s.sendMessage(Msg.err("Index de choix invalide (voir /dialogue show " + d.id + ")."));
            return;
        }
        switch (sub) {
            case "remove" -> {
                n.choices.remove(c);
                s.sendMessage(Msg.ok("Choix supprimé."));
            }
            case "text" -> {
                if (a.length < 6) { s.sendMessage(Msg.err("/dialogue choice text <dialogue> <noeud> <index> <texte>")); return; }
                c.text = NpcCommand.join(a, 5);
                s.sendMessage(Msg.ok("Texte modifié."));
            }
            case "perm" -> {
                if (a.length < 6) { s.sendMessage(Msg.err("/dialogue choice perm <dialogue> <noeud> <index> <permission|->")); return; }
                c.permission = a[5].equals("-") ? null : a[5];
                s.sendMessage(Msg.ok("Permission : " + (c.permission == null ? "aucune" : c.permission)));
            }
            case "command" -> {
                if (a.length < 6) { s.sendMessage(Msg.err("/dialogue choice command <dialogue> <noeud> <index> <commande>")); return; }
                c.commands.add(NpcCommand.join(a, 5));
                s.sendMessage(Msg.ok("Commande ajoutée."));
            }
            case "close" -> {
                if (a.length < 6) { s.sendMessage(Msg.err("/dialogue choice close <dialogue> <noeud> <index> <true|false>")); return; }
                c.close = Boolean.parseBoolean(a[5]);
                s.sendMessage(Msg.ok("Fermeture : " + c.close));
            }
            case "next" -> {
                if (a.length < 6) { s.sendMessage(Msg.err("/dialogue choice next <dialogue> <noeud> <index> <suivant|->")); return; }
                c.next = a[5].equals("-") ? null : a[5];
                s.sendMessage(Msg.ok("Suivant : " + (c.next == null ? "aucun" : c.next)));
            }
            default -> {
                s.sendMessage(Msg.err("Sous-commande inconnue."));
                return;
            }
        }
        plugin.dialogues().save(d);
    }

    // ───────────────────────── utilitaires ─────────────────────────

    private void show(CommandSender s, Dialogue d) {
        s.sendMessage(Msg.info("Dialogue " + d.id + " (départ : " + d.start + ")"));
        for (Dialogue.Node n : d.nodes.values()) {
            s.sendMessage(Msg.info("▸ " + n.id + (n.speaker != null ? " [" + n.speaker + "]" : "")
                    + (n.next != null ? " → " + n.next : "")));
            for (String t : n.text) s.sendMessage(Msg.info("    “" + t + "”"));
            for (Dialogue.Branch b : n.branches) s.sendMessage(Msg.info("    branche perm=" + b.permission() + " → " + b.next()));
            for (String c : n.commands) s.sendMessage(Msg.info("    on-enter: " + c));
            int i = 1;
            for (Dialogue.Choice c : n.choices) {
                s.sendMessage(Msg.info("    " + i++ + ". " + c.text
                        + (c.close ? " [fermer]" : c.next != null ? " → " + c.next : "")
                        + (c.permission != null ? " perm=" + c.permission : "")
                        + (c.commands.isEmpty() ? "" : " cmds=" + c.commands)));
            }
        }
    }

    private Dialogue need(CommandSender s, String[] a, int idx) {
        if (a.length <= idx) {
            s.sendMessage(Msg.err("Id de dialogue manquant."));
            return null;
        }
        Dialogue d = plugin.dialogues().get(a[idx]);
        if (d == null) s.sendMessage(Msg.err("Dialogue introuvable : " + a[idx]));
        return d;
    }

    private Dialogue.Node node(CommandSender s, Dialogue d, String[] a, int idx) {
        if (a.length <= idx) {
            s.sendMessage(Msg.err("Id de noeud manquant."));
            return null;
        }
        Dialogue.Node n = d.nodes.get(a[idx]);
        if (n == null) s.sendMessage(Msg.err("Noeud introuvable : " + a[idx]));
        return n;
    }

    private static boolean valid(String id) {
        return id.matches("[a-zA-Z0-9_\\-]+");
    }

    private static List<String> paragraphs(String raw) {
        List<String> out = new ArrayList<>();
        for (String p : raw.split("\\\\n")) {
            if (!p.isBlank()) out.add(p.trim());
        }
        return out;
    }

    private void help(CommandSender s) {
        for (String l : List.of(
                "/dialogue create|delete|show <id>   |   list   |   reload",
                "/dialogue start <id> <noeud>",
                "/dialogue node add <id> <noeud> [texte]   (\\n = nouveau paragraphe)",
                "/dialogue node text|speaker|portrait|next|command <id> <noeud> <valeur>",
                "/dialogue node branch <id> <noeud> <permission|-> <noeud-cible>   (redirection selon perm)",
                "/dialogue node remove|clearcommands|clearbranches <id> <noeud>",
                "/dialogue choice add <id> <noeud> <suivant|close> <texte>",
                "/dialogue choice text|perm|command|close|next <id> <noeud> <index> <valeur>",
                "/dialogue choice remove <id> <noeud> <index>",
                "/dialogue test <id> [noeud]")) {
            s.sendMessage(Msg.info(l));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (a.length == 1) return NpcCommand.filter(SUBS, a[0]);
        String sub = a[0].toLowerCase(Locale.ROOT);
        if (a.length == 2 && Set.of("delete", "show", "start", "test").contains(sub)) {
            return NpcCommand.filter(plugin.dialogues().ids(), a[1]);
        }
        if (sub.equals("node") || sub.equals("choice")) {
            if (a.length == 2) return NpcCommand.filter(sub.equals("node") ? NODE_SUBS : CHOICE_SUBS, a[1]);
            if (a.length == 3) return NpcCommand.filter(plugin.dialogues().ids(), a[2]);
            Dialogue d = plugin.dialogues().get(a[2]);
            if (d != null && a.length == 4) return NpcCommand.filter(d.nodes.keySet(), a[3]);
            if (d != null && sub.equals("node") && a.length == 6 && a[1].equalsIgnoreCase("branch")) {
                return NpcCommand.filter(d.nodes.keySet(), a[5]);
            }
            if (d != null && sub.equals("choice") && a.length == 5 && a[1].equalsIgnoreCase("add")) {
                List<String> opts = new ArrayList<>(d.nodes.keySet());
                opts.add("close");
                return NpcCommand.filter(opts, a[4]);
            }
        }
        if (sub.equals("test") && a.length == 3) {
            Dialogue d = plugin.dialogues().get(a[1]);
            if (d != null) return NpcCommand.filter(d.nodes.keySet(), a[2]);
        }
        return List.of();
    }
}
