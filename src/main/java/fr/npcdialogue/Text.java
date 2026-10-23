package fr.npcdialogue;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Text {
    private Text() {}

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Pattern LEGACY = Pattern.compile("&([0-9a-fk-orA-FK-OR])");
    private static final Pattern UNICODE = Pattern.compile("\\\\u([0-9a-fA-F]{4})");
    private static final Map<Character, String> TAGS = Map.ofEntries(
            Map.entry('0', "black"), Map.entry('1', "dark_blue"), Map.entry('2', "dark_green"),
            Map.entry('3', "dark_aqua"), Map.entry('4', "dark_red"), Map.entry('5', "dark_purple"),
            Map.entry('6', "gold"), Map.entry('7', "gray"), Map.entry('8', "dark_gray"),
            Map.entry('9', "blue"), Map.entry('a', "green"), Map.entry('b', "aqua"),
            Map.entry('c', "red"), Map.entry('d', "light_purple"), Map.entry('e', "yellow"),
            Map.entry('f', "white"), Map.entry('k', "obfuscated"), Map.entry('l', "bold"),
            Map.entry('m', "strikethrough"), Map.entry('n', "underlined"), Map.entry('o', "italic"),
            Map.entry('r', "reset"));

    private static boolean papiChecked;
    private static Method papiMethod;

    /** Transforme "\\uE000" (texte littéral) en vrai caractère. */
    public static String unescape(String s) {
        if (s == null) return "";
        Matcher m = UNICODE.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            char c = (char) Integer.parseInt(m.group(1), 16);
            m.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf(c)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String legacyToMini(String s) {
        Matcher m = LEGACY.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String tag = TAGS.get(Character.toLowerCase(m.group(1).charAt(0)));
            m.appendReplacement(sb, Matcher.quoteReplacement("<" + tag + ">"));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Remplace %player%, %npc%, %npc_name% puis PlaceholderAPI (si présent). */
    public static String apply(String s, Player p, Npc npc) {
        if (s == null) return "";
        String r = s;
        if (p != null) r = r.replace("%player%", p.getName());
        if (npc != null) {
            r = r.replace("%npc%", npc.id);
            if (r.contains("%npc_name%")) {
                r = r.replace("%npc_name%", PlainTextComponentSerializer.plainText()
                        .serialize(component(npc.displayName, null, null)));
            }
        }
        if (p != null) r = papi(p, r);
        return r;
    }

    public static Component component(String s, Player p, Npc npc) {
        String r = legacyToMini(unescape(apply(s, p, npc)));
        return MM.deserialize(r);
    }

    private static String papi(Player p, String s) {
        if (!s.contains("%")) return s;
        try {
            if (!papiChecked) {
                papiChecked = true;
                if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                    papiMethod = Class.forName("me.clip.placeholderapi.PlaceholderAPI")
                            .getMethod("setPlaceholders", OfflinePlayer.class, String.class);
                }
            }
            if (papiMethod != null) return (String) papiMethod.invoke(null, p, s);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return s;
    }
}
