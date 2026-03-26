package com.railjunction;

import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class ConfigurableRailBlock extends BaseRailBlock implements SimpleWaterloggedBlock {
    public static final MapCodec<ConfigurableRailBlock> CODEC = simpleCodec(ConfigurableRailBlock::new);
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    public static final EnumProperty<Direction> DEFAULT_EXIT = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<RailShape> SHAPE = BlockStateProperties.RAIL_SHAPE;

    private static final List<Direction> CARDINALS = List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);
    private static final Map<Direction, BooleanProperty> CONNECTION_PROPERTIES = new EnumMap<>(Direction.class);

    static {
        CONNECTION_PROPERTIES.put(Direction.NORTH, NORTH);
        CONNECTION_PROPERTIES.put(Direction.EAST, EAST);
        CONNECTION_PROPERTIES.put(Direction.SOUTH, SOUTH);
        CONNECTION_PROPERTIES.put(Direction.WEST, WEST);
    }

    public ConfigurableRailBlock(BlockBehaviour.Properties properties) {
        super(false, properties);
        this.registerDefaultState(
            this.stateDefinition.any()
                .setValue(NORTH, true)
                .setValue(EAST, false)
                .setValue(SOUTH, true)
                .setValue(WEST, false)
                .setValue(DEFAULT_EXIT, Direction.NORTH)
                .setValue(SHAPE, RailShape.NORTH_SOUTH)
                .setValue(WATERLOGGED, false)
        );
    }

    @Override
    public MapCodec<ConfigurableRailBlock> codec() {
        return CODEC;
    }

    @Override
    public Property<RailShape> getShapeProperty() {
        return SHAPE;
    }

    @Override
    public boolean canMakeSlopes(BlockState state, BlockGetter level, BlockPos pos) {
        return false;
    }

    @Override
    public boolean isValidRailShape(RailShape shape) {
        return !shape.isSlope();
    }

    @Override
    public RailShape getRailDirection(BlockState state, BlockGetter level, BlockPos pos, @Nullable AbstractMinecart cart) {
        if (cart == null) {
            return resolvedShape(state);
        }

        List<Direction> active = getActiveDirections(state);
        if (active.size() <= 2) {
            return resolvedShape(state);
        }

        Direction travelDirection = resolveTravelDirection(state, cart);
        Direction entryDirection = travelDirection.getOpposite();
        Direction exitDirection = chooseExitDirection(state, cart, travelDirection);
        RailShape routedShape = toRailShape(entryDirection, exitDirection);
        return routedShape != null ? routedShape : resolvedShape(state);
    }

    @Override
    protected BlockState updateDir(Level level, BlockPos pos, BlockState state, boolean alwaysPlace) {
        return syncState(state);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluidState = context.getLevel().getFluidState(context.getClickedPos());
        Direction playerFacing = context.getHorizontalDirection();
        boolean eastWest = playerFacing.getAxis() == Direction.Axis.X;
        BlockState state = this.defaultBlockState()
            .setValue(NORTH, !eastWest)
            .setValue(SOUTH, !eastWest)
            .setValue(EAST, eastWest)
            .setValue(WEST, eastWest)
            .setValue(DEFAULT_EXIT, eastWest ? Direction.EAST : Direction.NORTH)
            .setValue(WATERLOGGED, fluidState.getType() == Fluids.WATER);
        return syncState(state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        return InteractionResult.PASS;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, net.minecraft.world.entity.InsideBlockEffectApplier applier, boolean intersects) {
        super.entityInside(state, level, pos, entity, applier, intersects);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(
        BlockState state,
        LevelReader level,
        ScheduledTickAccess scheduledTickAccess,
        BlockPos pos,
        Direction direction,
        BlockPos neighborPos,
        BlockState neighborState,
        RandomSource random
    ) {
        if (state.getValue(WATERLOGGED)) {
            scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return syncState(state);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        Direction rotatedDefault = rotation.rotate(state.getValue(DEFAULT_EXIT));
        BlockState rotated = state
            .setValue(DEFAULT_EXIT, rotatedDefault)
            .setValue(NORTH, isConnected(state, rotation.rotate(Direction.NORTH)))
            .setValue(EAST, isConnected(state, rotation.rotate(Direction.EAST)))
            .setValue(SOUTH, isConnected(state, rotation.rotate(Direction.SOUTH)))
            .setValue(WEST, isConnected(state, rotation.rotate(Direction.WEST)));
        return syncState(rotated);
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        BlockState mirrored = state
            .setValue(NORTH, isConnected(state, mirror.mirror(Direction.NORTH)))
            .setValue(EAST, isConnected(state, mirror.mirror(Direction.EAST)))
            .setValue(SOUTH, isConnected(state, mirror.mirror(Direction.SOUTH)))
            .setValue(WEST, isConnected(state, mirror.mirror(Direction.WEST)))
            .setValue(DEFAULT_EXIT, mirror.mirror(state.getValue(DEFAULT_EXIT)));
        return syncState(mirrored);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, DEFAULT_EXIT, SHAPE, WATERLOGGED);
    }

    public static BlockState toggleConnection(BlockState state, Direction direction) {
        if (!direction.getAxis().isHorizontal()) {
            return state;
        }
        return syncState(state.setValue(CONNECTION_PROPERTIES.get(direction), !isConnected(state, direction)));
    }

    public static BlockState cycleDefaultExit(BlockState state) {
        List<Direction> active = getActiveDirections(state);
        if (active.isEmpty()) {
            return state;
        }
        Direction current = normalizeDefaultDirection(state);
        int index = active.indexOf(current);
        Direction next = active.get((index + 1 + active.size()) % active.size());
        return syncState(state.setValue(DEFAULT_EXIT, next));
    }

    public static List<Direction> getActiveDirections(BlockState state) {
        List<Direction> active = new ArrayList<>();
        for (Direction direction : CARDINALS) {
            if (isConnected(state, direction)) {
                active.add(direction);
            }
        }
        return active;
    }

    public static boolean isConnected(BlockState state, Direction direction) {
        BooleanProperty property = CONNECTION_PROPERTIES.get(direction);
        return property != null && state.getValue(property);
    }

    public static String describeState(BlockState state) {
        List<String> active = getActiveDirections(state).stream()
            .map(direction -> direction.getName().toLowerCase(Locale.ROOT))
            .toList();
        return "connections=" + String.join(", ", active) + " default=" + normalizeDefaultDirection(state).getName().toLowerCase(Locale.ROOT);
    }

    private static BlockState syncState(BlockState state) {
        List<Direction> active = getActiveDirections(state);
        Direction normalizedDefault = active.isEmpty() ? state.getValue(DEFAULT_EXIT) : normalizeDefaultDirection(state);
        RailShape shape = resolveShapeForDefault(active, normalizedDefault);
        return state.setValue(DEFAULT_EXIT, normalizedDefault).setValue(SHAPE, shape);
    }

    private static Direction normalizeDefaultDirection(BlockState state) {
        Direction preferred = state.getValue(DEFAULT_EXIT);
        if (isConnected(state, preferred)) {
            return preferred;
        }
        List<Direction> active = getActiveDirections(state);
        return active.isEmpty() ? preferred : active.getFirst();
    }

    private static RailShape resolvedShape(BlockState state) {
        return resolveShapeForDefault(getActiveDirections(state), normalizeDefaultDirection(state));
    }

    private static RailShape resolveShapeForDefault(List<Direction> active, Direction defaultExit) {
        if (active.contains(defaultExit) && active.contains(defaultExit.getOpposite())) {
            RailShape straight = toRailShape(defaultExit, defaultExit.getOpposite());
            if (straight != null) {
                return straight;
            }
        }

        if (active.size() == 2) {
            RailShape pairShape = toRailShape(active.get(0), active.get(1));
            if (pairShape != null) {
                return pairShape;
            }
        }

        if (active.size() >= 2) {
            for (Direction candidate : active) {
                if (candidate == defaultExit) {
                    continue;
                }
                RailShape shape = toRailShape(defaultExit, candidate);
                if (shape != null) {
                    return shape;
                }
            }
            RailShape fallback = toRailShape(active.get(0), active.get(1));
            if (fallback != null) {
                return fallback;
            }
        }

        return defaultExit.getAxis() == Direction.Axis.X ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;
    }

    private static Direction resolveTravelDirection(BlockState state, AbstractMinecart cart) {
        Vec3 movement = cart.getDeltaMovement();
        if (movement.horizontalDistanceSqr() > 1.0E-4) {
            return Math.abs(movement.x) > Math.abs(movement.z)
                ? (movement.x >= 0.0 ? Direction.EAST : Direction.WEST)
                : (movement.z >= 0.0 ? Direction.SOUTH : Direction.NORTH);
        }

        Direction motionDirection = cart.getMotionDirection();
        if (motionDirection.getAxis().isHorizontal()) {
            return motionDirection;
        }

        return normalizeDefaultDirection(state);
    }

    private static Direction chooseExitDirection(BlockState state, AbstractMinecart cart, Direction travelDirection) {
        List<Direction> active = getActiveDirections(state);
        Direction entryDirection = travelDirection.getOpposite();
        List<Direction> exits = active.stream()
            .filter(direction -> direction != entryDirection)
            .toList();

        if (exits.isEmpty()) {
            return travelDirection;
        }

        ServerPlayer rider = cart.getFirstPassenger() instanceof ServerPlayer player ? player : null;
        if (rider != null) {
            if (rider.getLastClientInput().left()) {
                Direction left = travelDirection.getCounterClockWise();
                if (exits.contains(left)) {
                    return left;
                }
            }
            if (rider.getLastClientInput().right()) {
                Direction right = travelDirection.getClockWise();
                if (exits.contains(right)) {
                    return right;
                }
            }
        }

        if (exits.contains(travelDirection)) {
            return travelDirection;
        }

        Direction defaultExit = normalizeDefaultDirection(state);
        if (exits.contains(defaultExit)) {
            return defaultExit;
        }

        return exits.getFirst();
    }

    private static @Nullable RailShape toRailShape(Direction first, Direction second) {
        if (first == second) {
            return first.getAxis() == Direction.Axis.X ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;
        }

        if (first.getAxis() == second.getAxis()) {
            return first.getAxis() == Direction.Axis.X ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;
        }

        if (contains(first, second, Direction.NORTH, Direction.EAST)) {
            return RailShape.NORTH_EAST;
        }
        if (contains(first, second, Direction.NORTH, Direction.WEST)) {
            return RailShape.NORTH_WEST;
        }
        if (contains(first, second, Direction.SOUTH, Direction.EAST)) {
            return RailShape.SOUTH_EAST;
        }
        if (contains(first, second, Direction.SOUTH, Direction.WEST)) {
            return RailShape.SOUTH_WEST;
        }
        return null;
    }

    private static boolean contains(Direction first, Direction second, Direction expectedA, Direction expectedB) {
        return (first == expectedA && second == expectedB) || (first == expectedB && second == expectedA);
    }
}
