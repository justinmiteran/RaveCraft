package com.ravecraft.mod.block;

import com.ravecraft.mod.blockentity.MachineBlockEntity;
import com.ravecraft.mod.recipe.MachineKind;
import com.ravecraft.mod.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * A processing machine, like a furnace: it faces the player who placed it, is lit while it works, keeps its contents
 * in a {@link MachineBlockEntity} (dropped when broken) and ticks on the server only. Each machine gives its kind.
 */
public abstract class MachineBlock extends BaseEntityBlock {
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
	public static final BooleanProperty LIT = BlockStateProperties.LIT;
	private static final double SOUND_CHANCE = 0.1;
	private static final float SOUND_VOLUME = 0.4F;
	private static final float PITCH_SPREAD = 0.2F;
	private static final double FRONT_OFFSET = 0.52;
	private static final double SPREAD = 0.6;
	private static final double PARTICLE_HEIGHT = 0.4;

	/**
	 * A machine facing north, unlit.
	 *
	 * @param properties the block properties
	 */
	protected MachineBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
	}

	/**
	 * The machine's kind, which gives its recipes.
	 *
	 * @return the machine kind
	 */
	public abstract MachineKind kind();

	/**
	 * What the machine plays and shows while it works.
	 *
	 * @return the machine's sound and particle
	 */
	protected abstract MachineEffects effects();

	/** While it works: now and then the machine's sound, and its particle at the front. */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (!state.getValue(LIT)) {
			return;
		}
		MachineEffects effects = effects();
		double x = pos.getX() + 0.5;
		double y = pos.getY();
		double z = pos.getZ() + 0.5;
		if (random.nextDouble() < SOUND_CHANCE) {
			level.playLocalSound(x, y, z, effects.sound(), SoundSource.BLOCKS, SOUND_VOLUME,
					effects.pitch() + random.nextFloat() * PITCH_SPREAD, false);
		}
		Direction facing = state.getValue(FACING);
		double across = random.nextDouble() * SPREAD - SPREAD / 2;
		double dx = facing.getAxis() == Direction.Axis.X ? facing.getStepX() * FRONT_OFFSET : across;
		double dz = facing.getAxis() == Direction.Axis.Z ? facing.getStepZ() * FRONT_OFFSET : across;
		double dy = PARTICLE_HEIGHT + random.nextDouble() * PARTICLE_HEIGHT;
		level.addParticle(effects.particle(), x + dx, y + dy, z + dz, 0, 0, 0);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, LIT);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	/** Opens the machine's menu. */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hit) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof MachineBlockEntity machine) {
			player.openMenu(machine);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos,
			boolean movedByPiston) {
		Containers.updateNeighboursAfterDestroy(state, level, pos);
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
		return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MachineBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		return level instanceof ServerLevel
				? createTickerHelper(type, ModBlockEntities.MACHINE, MachineBlockEntity::serverTick)
				: null;
	}
}
