package com.github.toxuin.griswold;

public class ClassProxy {
    // THIS WHOLE FILE IS ABSOLUTELY GAY
    // THANK FOR VERSIONED NMS PACKAGES, EvilSeph
    // THERE ARE PEOPLE WHO HATE YOU.

    // RELATIVE TO net.minecraft.server.vX_X_RX.
    public static Class getClass(String className) {
        if (className.equals("EnchantmentInstance") && Griswold.apiVersion.contains("v1_8")) {
            className = "WeightedRandomEnchant";
        }

        try {
            return Class.forName("net.minecraft.server." + Griswold.apiVersion + "." + className);
        } catch (ClassNotFoundException e) {
            try {
                return Class.forName("org.bukkit.craftbukkit." + Griswold.apiVersion + "." + className);
            } catch (ClassNotFoundException e1) {
                return null;
            }
        }
    }
}
