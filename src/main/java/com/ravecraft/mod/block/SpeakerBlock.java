package com.ravecraft.mod.block;

import com.ravecraft.mod.blockentity.SpeakerBlockEntity;
import com.ravecraft.mod.component.SpeakerData;
import com.ravecraft.mod.core.speaker.SpeakerKind;
import com.ravecraft.mod.registry.ModDataComponents;
import com.ravecraft.mod.world.audio.SpeakerHosts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A speaker block of any kind: facing, power and waterlogging, the dyed fabric, the screen and the block entity are
 * shared; each kind gives its acoustic kind and its shapes. Waterproof, so it can sit in water.
 */
public abstract class SpeakerBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
	public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

	/** Fabric colour of an undyed speaker (charcoal black), also the item model's default tint. */
	public static final int DEFAULT_COLOR = 0x3C3C44;

	private final SpeakerKind kind;
	private final VoxelShape shapeNorthSouth;
	private final VoxelShape shapeEastWest;

	/**
	 * A speaker of {@code kind}, unpowered and facing north by default, outlined by {@code northSouth} when facing
	 * north or south and by {@code eastWest} otherwise.
	 */
	protected SpeakerBlock(BlockBehaviour.Properties properties, SpeakerKind kind, VoxelShape northSouth, VoxelShape eastWest) {
		super(properties);
		this.kind = kind;
		this.shapeNorthSouth = northSouth;
		this.shapeEastWest = eastWest;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false).setValue(WATERLOGGED, false));
	}

	/**
	 * The kind of speaker, which picks its acoustic profile.
	 *
	 * @return the speaker kind
	 */
	public SpeakerKind kind() {
		return kind;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, POWERED, WATERLOGGED);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		SpeakerData data = context.getItemInHand().get(ModDataComponents.SPEAKER);
		boolean powered = data != null && data.settings().powered();
		return defaultBlockState()
				.setValue(FACING, context.getHorizontalDirection().getOpposite())
				.setValue(POWERED, powered)
				.setValue(WATERLOGGED, context.getLevel().getFluidState(context.getClickedPos()).is(Fluids.WATER));
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
		super.setPlacedBy(level, pos, state, by, stack);
		// Creative placement does not consume the stack, so copies would share one identity: give each placed speaker a fresh id.
		if (!level.isClientSide() && by instanceof Player player && player.hasInfiniteMaterials()
				&& level.getBlockEntity(pos) instanceof SpeakerBlockEntity speaker) {
			speaker.setData(SpeakerData.create().withSettings(speaker.data().settings()));
		}
		// The id is final here (from the item, or refreshed above): register where it lives.
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof SpeakerBlockEntity speaker) {
			SpeakerHosts.placed(speaker);
		}
	}

	/** Middle-click keeps the fabric colour, like picking a coloured block; ctrl adds the full data on top. */
	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		ItemStack stack = super.getCloneItemStack(level, pos, state, includeData);
		if (level.getBlockEntity(pos) instanceof SpeakerBlockEntity speaker && speaker.dyedColor() != null) {
			stack.set(DataComponents.DYED_COLOR, speaker.dyedColor());
		}
		return stack;
	}

	/** Stands on a sturdy floor only, like a lantern: it cannot float and drops when its support is broken. */
	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return Block.canSupportCenter(level, pos.below(), Direction.UP);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
			Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		if (state.getValue(WATERLOGGED)) {
			ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
		}
		if (directionToNeighbour == Direction.DOWN && !state.canSurvive(level, pos)) {
			return Blocks.AIR.defaultBlockState();
		}
		return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
	}

	@Override
	protected FluidState getFluidState(BlockState state) {
		return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(FACING).getAxis() == Direction.Axis.X ? shapeEastWest : shapeNorthSouth;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (player.isSecondaryUseActive()) {
			if (!level.isClientSide() && level.getBlockEntity(pos) instanceof SpeakerBlockEntity speaker) {
				speaker.togglePower();
			}
		} else if (level.isClientSide() && level.getBlockEntity(pos) instanceof SpeakerBlockEntity speaker) {
			// Titled with the item's custom name when it has one, otherwise the block's name.
			SpeakerScreenOpener.open(speaker.data().id(), speaker.customName() != null ? speaker.customName() : getName());
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new SpeakerBlockEntity(pos, state);
	}
}
