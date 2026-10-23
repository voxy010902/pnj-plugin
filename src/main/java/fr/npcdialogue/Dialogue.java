package fr.npcdialogue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Dialogue {

    public static final class Choice {
        public String text = "...";
        public String next;                 // noeud suivant (null + close = ferme)
        public boolean close;               // ferme le dialogue
        public String permission;           // visible seulement si Perm.has(...)
        public final List<String> commands = new ArrayList<>();
    }

    public record Branch(String permission, String next) {}

    public static final class Node {
        public final String id;
        public String speaker;              // null = nom du PNJ
        public String portrait;             // clé config ui.portraits
        public String next;                 // utilisé si aucun choix
        public final List<String> text = new ArrayList<>();
        public final List<String> commands = new ArrayList<>();   // exécutées à l'affichage
        public final List<Choice> choices = new ArrayList<>();
        public final List<Branch> branches = new ArrayList<>();   // redirection selon permission

        public Node(String id) { this.id = id; }
    }

    public final String id;
    public String start;
    public final Map<String, Node> nodes = new LinkedHashMap<>();

    public Dialogue(String id) { this.id = id; }
}
