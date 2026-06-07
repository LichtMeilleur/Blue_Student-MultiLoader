package com.licht_meilleur.blue_student.neoforge.loot;

import com.licht_meilleur.blue_student.neoforge.BlueStudentMod;

public final class ModLoot {
    private ModLoot() {
    }

    public static void init() {
        // TODO NeoForge:
        // Fabric の LootTableEvents.MODIFY は使えないため、
        // NeoForge の LootTableLoadEvent / DataPack loot injection 方式へ後で移植する。
        BlueStudentMod.LOGGER.info("[BlueStudent] ModLoot init skipped for NeoForge");
    }
}