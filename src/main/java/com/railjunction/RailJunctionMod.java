package com.railjunction;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(RailJunctionMod.MOD_ID)
public final class RailJunctionMod {
    public static final String MOD_ID = "railjunction";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    public static final DeferredBlock<ConfigurableRailBlock> CONFIGURABLE_RAIL = BLOCKS.register(
        "configurable_rail",
        () -> new ConfigurableRailBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(0.7F)
                .sound(SoundType.METAL)
                .noCollision()
        )
    );
    public static final DeferredItem<BlockItem> CONFIGURABLE_RAIL_ITEM = ITEMS.registerSimpleBlockItem("configurable_rail", CONFIGURABLE_RAIL);
    public static final DeferredItem<Item> RAIL_CONFIGURATOR = ITEMS.register(
        "rail_configurator",
        () -> new RailConfiguratorItem(new Item.Properties().stacksTo(1))
    );

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = CREATIVE_MODE_TABS.register(
        "railjunction",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.railjunction"))
            .withTabsBefore(CreativeModeTabs.REDSTONE_BLOCKS)
            .icon(() -> CONFIGURABLE_RAIL_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(CONFIGURABLE_RAIL_ITEM.get());
                output.accept(RAIL_CONFIGURATOR.get());
            })
            .build()
    );

    public RailJunctionMod(IEventBus modEventBus, ModContainer modContainer) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        modEventBus.addListener(this::addCreativeTabEntries);
    }

    private void addCreativeTabEntries(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(CONFIGURABLE_RAIL_ITEM);
        }
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES || event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) {
            event.accept(RAIL_CONFIGURATOR);
        }
    }
}
