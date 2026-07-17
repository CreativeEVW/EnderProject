package com.enderproject.creativeevw.block;

import com.enderproject.creativeevw.EnderProject;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, EnderProject.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StemCellBlockEntity>> STEM_CELL =
            BLOCK_ENTITIES.register("stem_cell", () ->
                    BlockEntityType.Builder.of(StemCellBlockEntity::new, ModBlocks.STEM_CELL.get())
                            .build(null));

    private ModBlockEntities() {}
}
