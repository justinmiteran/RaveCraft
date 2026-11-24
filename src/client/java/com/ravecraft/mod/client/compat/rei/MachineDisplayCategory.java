package com.ravecraft.mod.client.compat.rei;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.client.compat.MachineEnergyView;
import com.ravecraft.mod.compat.rei.MachineDisplay;
import com.ravecraft.mod.core.machine.MachineLayout;
import com.ravecraft.mod.core.machine.MachineRecipeLayout;
import com.ravecraft.mod.recipe.MachineKind;
import com.ravecraft.mod.recipe.MachineRecipe;
import java.util.ArrayList;
import java.util.List;
import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Slot;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** The REI page of one machine, laid out like its screen by {@link MachineRecipeLayout}. */
final class MachineDisplayCategory implements DisplayCategory<MachineDisplay> {
	private static final int PADDING = 5;
	private static final int TIME_COLOR = 0xFF808080;
	private static final Identifier BOLT_SPRITE = RaveCraft.id("container/machine/energy");

	private final MachineKind kind;
	private final MachineRecipeLayout layout;

	/**
	 * Creates the page of a machine.
	 *
	 * @param kind the machine kind
	 */
	MachineDisplayCategory(MachineKind kind) {
		this.kind = kind;
		this.layout = new MachineRecipeLayout(kind.inputs(), kind.needsMold());
	}

	@Override
	public CategoryIdentifier<? extends MachineDisplay> getCategoryIdentifier() {
		return MachineDisplay.category(kind);
	}

	@Override
	public Component getTitle() {
		return new ItemStack(kind.machineItem()).getHoverName();
	}

	@Override
	public Renderer getIcon() {
		return EntryStacks.of(kind.machineItem());
	}

	@Override
	public int getDisplayWidth(MachineDisplay display) {
		return layout.width() + 2 * PADDING;
	}

	@Override
	public int getDisplayHeight() {
		return MachineRecipeLayout.HEIGHT + 2 * PADDING;
	}

	@Override
	public List<Widget> setupDisplay(MachineDisplay display, Rectangle bounds) {
		Point corner = new Point(bounds.x + PADDING, bounds.y + PADDING);
		MachineRecipe recipe = display.data().recipe();
		List<Widget> widgets = new ArrayList<>();
		widgets.add(Widgets.createRecipeBase(bounds));
		addInputs(widgets, corner, display);
		widgets.add(Widgets.createSlot(at(corner, layout.energy())).entries(energy()));
		Point output = at(corner, layout.output());
		widgets.add(Widgets.createResultSlotBackground(output));
		widgets.add(Widgets.createSlot(output).entries(display.getOutputEntries().getFirst()).disableBackground()
				.markOutput());
		Point bolt = at(corner, layout.bolt());
		widgets.add(Widgets.createDrawableWidget((graphics, mouseX, mouseY, delta) -> graphics.blitSprite(
				RenderPipelines.GUI_TEXTURED, BOLT_SPRITE, bolt.x, bolt.y, MachineLayout.BOLT_SIZE,
				MachineLayout.BOLT_SIZE)));
		widgets.add(Widgets.createArrow(at(corner, layout.arrow())).animationDurationTicks(recipe.processingTime()));
		widgets.add(Widgets.createLabel(new Point(corner.x + layout.width(), corner.y + MachineRecipeLayout.TIME_Y),
				Component.translatable("gui.ravecraft.jei.time", MachineRecipeLayout.seconds(recipe.processingTime())))
				.rightAligned().noShadow().color(TIME_COLOR));
		return widgets;
	}

	/** The counted inputs, then the mold: the order of the display's entries, by which REI marks a missing one. */
	private void addInputs(List<Widget> widgets, Point corner, MachineDisplay display) {
		List<EntryIngredient> entries = display.getInputEntries();
		int inputs = display.data().recipe().ingredients().size();
		for (int i = 0; i < entries.size(); i++) {
			MachineRecipeLayout.Pos pos = i < inputs ? layout.input(i) : layout.moldSlot();
			Slot slot = Widgets.createSlot(at(corner, pos)).entries(entries.get(i)).markInput();
			widgets.add(slot);
		}
	}

	/** Every machine energy item, each telling how many operations it gives. */
	private static List<EntryStack<ItemStack>> energy() {
		List<EntryStack<ItemStack>> stacks = new ArrayList<>();
		for (ItemStack item : MachineEnergyView.items()) {
			stacks.add(EntryStacks.of(item).tooltip(
					Component.translatable("gui.ravecraft.jei.energy", MachineEnergyView.operations(item))));
		}
		return stacks;
	}

	private static Point at(Point corner, MachineRecipeLayout.Pos pos) {
		return new Point(corner.x + pos.x(), corner.y + pos.y());
	}
}
