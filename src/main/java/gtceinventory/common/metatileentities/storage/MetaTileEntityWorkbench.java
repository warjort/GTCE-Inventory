/*
 * This file is part of GTCE Inventory.
 * Copyright (c) 2021, warjort and others, All rights reserved.
 *
 * It is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * It is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License.
 * If not, see <http://www.gnu.org/licenses/lgpl>.
 */
package gtceinventory.common.metatileentities.storage;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.ColourMultiplier;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Cuboid6;
import codechicken.lib.vec.Matrix4;
import com.google.common.base.Preconditions;
import gregtech.api.gui.GuiTextures;
import gregtech.api.gui.ModularUI;
import gregtech.api.gui.ModularUI.Builder;
import gregtech.api.gui.Widget.ClickData;
import gregtech.api.gui.resources.TextureArea;
import gregtech.api.gui.widgets.*;
import gregtech.api.gui.widgets.TabGroup.TabLocation;
import gregtech.api.gui.widgets.tab.ItemTabInfo;
import gregtech.api.items.toolitem.ToolMetaItem;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.render.Textures;
import gregtech.api.unification.material.Materials;
import gregtech.api.util.GTUtility;
import gregtech.api.util.Position;
import gregtech.common.gui.widget.CraftingSlotWidget;
import gregtech.common.gui.widget.ItemListGridWidget;
import gregtech.common.gui.widget.MemorizedRecipeWidget;
import gregtech.common.inventory.IItemList;
import gregtech.common.inventory.itemsource.ItemSourceList;
import gregtech.common.inventory.itemsource.sources.InventoryItemSource;
import gregtech.common.metatileentities.storage.CraftingRecipeMemory;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemTool;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.ItemStackHandler;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nonnull;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class MetaTileEntityWorkbench extends MetaTileEntity {

    private final ItemStackHandler internalInventory = new ItemStackHandler(18);

    private final ItemStackHandler craftingGrid = new ItemStackHandler(9) {
        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }
    };

    private final ItemStackHandler toolInventory = new ItemStackHandler(9) {
        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Nonnull
        @Override
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            if (!(stack.getItem() instanceof ToolMetaItem) &&
                !(stack.getItem() instanceof ItemTool) &&
                !(stack.isItemStackDamageable())) {
                return stack;
            }
            return super.insertItem(slot, stack, simulate);
        }
    };

    private final CraftingRecipeMemory recipeMemory = new CraftingRecipeMemory(9);
    private CraftingRecipeResolver recipeResolver = null;
    private int itemsCrafted = 0;

    public MetaTileEntityWorkbench(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(MetaTileEntityHolder holder) {
        return new MetaTileEntityWorkbench(metaTileEntityId);
    }

    @SideOnly(Side.CLIENT)
    private int getResultRenderingColor() {
        int paintingColor = getPaintingColorForRendering();
        if (paintingColor == DEFAULT_PAINTING_COLOR) {
            paintingColor = Materials.Bronze.materialRGB;
        }
        return paintingColor;
    }

    @Override
    public Pair<TextureAtlasSprite, Integer> getParticleTexture() {
        return Pair.of(Textures.CRAFTING_TABLE.getParticleSprite(), getResultRenderingColor());
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        int paintingColor = getPaintingColorForRendering();
        pipeline = ArrayUtils.add(pipeline, new ColourMultiplier(GTUtility.convertRGBtoOpaqueRGBA_CL(paintingColor)));
        Textures.CRAFTING_TABLE.render(renderState, translation, pipeline, Cuboid6.full, getFrontFacing());
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setTag("CraftingGridInventory", craftingGrid.serializeNBT());
        data.setTag("ToolInventory", toolInventory.serializeNBT());
        data.setTag("InternalInventory", internalInventory.serializeNBT());
        data.setInteger("ItemsCrafted", recipeResolver == null ? itemsCrafted : recipeResolver.getItemsCrafted());
        data.setTag("RecipeMemory", recipeMemory.serializeNBT());
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.craftingGrid.deserializeNBT(data.getCompoundTag("CraftingGridInventory"));
        this.toolInventory.deserializeNBT(data.getCompoundTag("ToolInventory"));
        this.internalInventory.deserializeNBT(data.getCompoundTag("InternalInventory"));
        this.itemsCrafted = data.getInteger("ItemsCrafted");
        this.recipeMemory.deserializeNBT(data.getCompoundTag("RecipeMemory"));
    }

    private void createRecipeResolver() {
        this.recipeResolver = new CraftingRecipeResolver(getWorld(), craftingGrid, recipeMemory);
        this.recipeResolver.setItemsCrafted(itemsCrafted);
        ItemSourceList itemSourceList = this.recipeResolver.getItemSourceList();
        itemSourceList.addItemHandler(InventoryItemSource.direct(getWorld(), toolInventory, -2));
        itemSourceList.addItemHandler(InventoryItemSource.direct(getWorld(), internalInventory, -1));
        this.recipeResolver.checkNeighbourInventories(getPos());
    }

    @Override
    public void update() {
        super.update();
        if (!getWorld().isRemote && recipeResolver == null) {
            createRecipeResolver();
        }
        if (!getWorld().isRemote) {
            getRecipeResolver().update();
        }
    }

    private CraftingRecipeResolver getRecipeResolver() {
        Preconditions.checkState(getWorld() != null, "getRecipeResolver called too early");
        return recipeResolver;
    }

    @Override
    public void onNeighborChanged() {
        super.onNeighborChanged();
        if (recipeResolver != null) {
            this.recipeResolver.checkNeighbourInventories(getPos());
        }
    }

    @Override
    public void clearMachineInventory(NonNullList<ItemStack> itemBuffer) {
        super.clearMachineInventory(itemBuffer);
        clearInventory(itemBuffer, internalInventory);
        clearInventory(itemBuffer, toolInventory);
    }

    private AbstractWidgetGroup createWorkbenchTab() {
        WidgetGroup widgetGroup = new WidgetGroup();
        CraftingRecipeResolver recipeResolver = getRecipeResolver();

        widgetGroup.addWidget(new ImageWidget(88 - 13, 44 - 13, 26, 26, GuiTextures.SLOT));
        widgetGroup.addWidget(new CraftingSlotWidget(recipeResolver, 0, 88 - 9, 44 - 9));

        //crafting grid
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 3; ++j) {
                widgetGroup.addWidget(new PhantomSlotWidget(craftingGrid, j + i * 3, 8 + j * 18, 17 + i * 18).setBackgroundTexture(GuiTextures.SLOT));
            }
        }
        Supplier<String> textSupplier = () -> Integer.toString(recipeResolver.getItemsCrafted());
        widgetGroup.addWidget(new SimpleTextWidget(88, 44 + 20, "", textSupplier));

        Consumer<ClickData> clearAction = (clickData) -> recipeResolver.clearCraftingGrid();
        widgetGroup.addWidget(new ClickButtonWidget(8 + 18 * 3 + 1, 17, 8, 8, "", clearAction).setButtonTexture(GuiTextures.BUTTON_CLEAR_GRID));

        widgetGroup.addWidget(new ImageWidget(168 - 18 * 3, 44 - 18 * 3 / 2, 18 * 3, 18 * 3, TextureArea.fullImage("textures/gui/base/darkened_slot.png")));
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 3; ++j) {
                widgetGroup.addWidget(new MemorizedRecipeWidget(recipeMemory, j + i * 3, craftingGrid, 168 - 18 * 3 / 2 - 27 + j * 18, 44 - 27 + i * 18));
            }
        }
        //tool inventory
        for (int i = 0; i < 9; i++) {
            widgetGroup.addWidget(new SlotWidget(toolInventory, i, 8 + i * 18, 76).setBackgroundTexture(GuiTextures.SLOT, GuiTextures.TOOL_SLOT_OVERLAY));
        }
        //internal inventory
        for (int i = 0; i < 2; ++i) {
            for (int j = 0; j < 9; ++j) {
                widgetGroup.addWidget(new SlotWidget(internalInventory, j + i * 9, 8 + j * 18, 99 + i * 18).setBackgroundTexture(GuiTextures.SLOT));
            }
        }
        return widgetGroup;
    }

    private AbstractWidgetGroup createItemListTab() {
        WidgetGroup widgetGroup = new WidgetGroup();
        widgetGroup.addWidget(new LabelWidget(5, 20, "gregtech.machine.workbench.storage_note_1"));
        widgetGroup.addWidget(new LabelWidget(5, 30, "gregtech.machine.workbench.storage_note_2"));
        CraftingRecipeResolver recipeResolver = getRecipeResolver();
        IItemList itemList = recipeResolver == null ? null : recipeResolver.getItemSourceList();
        widgetGroup.addWidget(new ItemListGridWidget(2, 45, 9, 5, itemList));
        return widgetGroup;
    }

    @Override
    protected ModularUI createUI(EntityPlayer entityPlayer) {
        Builder builder = ModularUI.builder(GuiTextures.BORDERED_BACKGROUND, 176, 221)
            .bindPlayerInventory(entityPlayer.inventory, 140);
        builder.label(5, 5, getMetaFullName());

        TabGroup tabGroup = new TabGroup(TabLocation.HORIZONTAL_TOP_LEFT, Position.ORIGIN);
        tabGroup.addTab(new ItemTabInfo("gregtech.machine.workbench.tab.workbench", new ItemStack(Blocks.CRAFTING_TABLE)), createWorkbenchTab());
        tabGroup.addTab(new ItemTabInfo("gregtech.machine.workbench.tab.item_list", new ItemStack(Blocks.CHEST)), createItemListTab());
        builder.widget(tabGroup);

        return builder.build(getHolder(), entityPlayer);
    }
}
