package com.railjunction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class RailConfiguratorItem extends Item {
    public RailConfiguratorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ConfigurableRailBlock)) {
            return InteractionResult.PASS;
        }

        Direction targetDirection = resolveTargetDirection(context);
        if (!targetDirection.getAxis().isHorizontal()) {
            return InteractionResult.PASS;
        }

        BlockState updatedState = context.isSecondaryUseActive()
            ? ConfigurableRailBlock.cycleDefaultExit(state)
            : ConfigurableRailBlock.toggleConnection(state, targetDirection);

        if (!level.isClientSide()) {
            level.setBlock(pos, updatedState, net.minecraft.world.level.block.Block.UPDATE_ALL);
            if (context.getPlayer() != null) {
                Component feedback = Component.literal(
                    context.isSecondaryUseActive()
                        ? "Default exit set: " + ConfigurableRailBlock.describeState(updatedState)
                        : "Updated rail: " + ConfigurableRailBlock.describeState(updatedState)
                );
                context.getPlayer().displayClientMessage(feedback, true);
            }
        }

        return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
    }

    private static Direction resolveTargetDirection(UseOnContext context) {
        if (context.getClickedFace().getAxis().isHorizontal()) {
            return context.getClickedFace();
        }

        Vec3 local = context.getClickLocation().subtract(Vec3.atLowerCornerOf(context.getClickedPos()));
        double dx = local.x - 0.5;
        double dz = local.z - 0.5;
        return Math.abs(dx) > Math.abs(dz) ? (dx >= 0.0 ? Direction.EAST : Direction.WEST) : (dz >= 0.0 ? Direction.SOUTH : Direction.NORTH);
    }
}
