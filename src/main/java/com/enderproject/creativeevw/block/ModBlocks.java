package com.enderproject.creativeevw.block;

import com.enderproject.creativeevw.EnderProject;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(EnderProject.MODID);

    public static final DeferredBlock<Block> STEM_CELL = BLOCKS.register("stem_cell",
            () -> new StemCellBlock(BlockBehaviour.Properties.of()
                    .strength(0.0F, 0.0F)
                    .noLootTable()
                    .sound(SoundType.SLIME_BLOCK)
                    .instabreak()));

    private ModBlocks() {}
}
