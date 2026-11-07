package com.example.starfall;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

public class StarfallMod implements ModInitializer {
    public static final String MOD_ID = "starfall";

    public static Item STARFALL_BLADE;

    @Override
    public void onInitialize() {
        Scheduler.init();

        STARFALL_BLADE = Registry.register(
                Registries.ITEM,
                Identifier.of(MOD_ID, "starfall_blade"),
                new StarfallBladeItem(new Item.Settings()
                        .rarity(Rarity.EPIC)
                        .fireproof()
                        .attributeModifiers(SwordItem.createAttributeModifiers(ToolMaterials.NETHERITE, 6, -2.2f)))
        );

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> entries.add(STARFALL_BLADE));
    }
}
