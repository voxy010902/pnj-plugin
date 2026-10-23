package fr.npcdialogue;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class Msg {
    private Msg() {}

    public static Component ok(String s) {
        return Component.text("✔ ", NamedTextColor.GREEN).append(Component.text(s, NamedTextColor.GRAY));
    }

    public static Component err(String s) {
        return Component.text("✘ ", NamedTextColor.RED).append(Component.text(s, NamedTextColor.GRAY));
    }

    public static Component info(String s) {
        return Component.text("• ", NamedTextColor.GOLD).append(Component.text(s, NamedTextColor.GRAY));
    }
}
