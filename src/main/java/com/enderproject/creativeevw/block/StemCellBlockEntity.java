package com.enderproject.creativeevw.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class StemCellBlockEntity extends BlockEntity {

    private static final int ASSIMILATE_INTERVAL = 20;  // 1秒
    private static final int DECAY_TIME = 60;            // 3秒无分裂则消失
    private static final int MAX_ASSIMILATIONS = 6;
    public static final int INITIAL_SPLIT_COUNT = 100;

    private int splitCount = INITIAL_SPLIT_COUNT;
    private int assimilated = 0;
    private int tickCounter = 0;
    private int idleTicks = 0;

    public StemCellBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STEM_CELL.get(), pos, state);
    }

    public void setSplitCount(int count) {
        this.splitCount = count;
        setChanged();
    }

    public int getSplitCount() {
        return splitCount;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, StemCellBlockEntity be) {
        if (level.isClientSide) return;
        // 分裂次数耗尽 → 自动死亡
        if (be.splitCount <= 0) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            return;
        }
        if (be.assimilated >= MAX_ASSIMILATIONS) return;

        be.tickCounter++;
        be.idleTicks++;

        // 3 秒无分裂 → 消失
        if (be.idleTicks >= DECAY_TIME) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            return;
        }

        if (be.tickCounter < ASSIMILATE_INTERVAL) return;
        be.tickCounter = 0;

        // 收集可同化的相邻方块
        List<Direction> candidates = new ArrayList<>();
        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = pos.relative(dir);
            BlockState neighborState = level.getBlockState(neighborPos);
            if (canAssimilate(level, neighborPos, neighborState)) {
                candidates.add(dir);
            }
        }

        if (candidates.isEmpty()) return;

        // 随机选一个同化
        Direction target = candidates.get(level.random.nextInt(candidates.size()));
        BlockPos targetPos = pos.relative(target);

        level.setBlock(targetPos, state, Block.UPDATE_ALL);
        BlockEntity newBe = level.getBlockEntity(targetPos);
        if (newBe instanceof StemCellBlockEntity stemBe) {
            stemBe.setSplitCount(be.splitCount - 1);
        }

        be.assimilated++;
        be.idleTicks = 0; // 成功分裂，重置闲置计时器

        if (be.assimilated >= MAX_ASSIMILATIONS) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        be.setChanged();
    }

    private static boolean canAssimilate(Level level, BlockPos pos, BlockState state) {
        if (state.isAir()) return false;
        if (state.getBlock() == ModBlocks.STEM_CELL.get()) return false;
        // 不可破坏的方块（基岩、命令方块等）不能被同化
        if (state.getDestroySpeed(level, pos) < 0) return false;
        return true;
    }

    // === NBT ===

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("splitCount", splitCount);
        tag.putInt("assimilated", assimilated);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        splitCount = tag.getInt("splitCount");
        assimilated = tag.getInt("assimilated");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("splitCount", splitCount);
        return tag;
    }

    @Override
    public @Nullable ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
