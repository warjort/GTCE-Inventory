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
package gtceinventory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import gtceinventory.api.capability.GTCEInventoryCapabilityManager;
import gtceinventory.common.CommonProxy;
import gtceinventory.common.blocks.GTCEInventoryMetaBlocks;
import gtceinventory.common.covers.GTCEInventoryCoverBehaviors;
import gtceinventory.common.items.GTCEInventoryMetaItems;
import gtceinventory.common.metatileentities.GTCEInventoryMetaTileEntities;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod(modid = GTCEInventory.MODID, name = GTCEInventory.NAME, version = GTCEInventory.VERSION, dependencies = "required-after:gregtech@[1.17.1.770,)")
public class GTCEInventory {
    public static final String MODID = "gtceinventory";
    public static final String NAME = "GTCE Inventory";
    public static final String VERSION = "@VERSION@";

    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public GTCEInventory() {
    }

    @SidedProxy(modId = GTCEInventory.MODID, clientSide = "gtceinventory.common.ClientProxy", serverSide = "gtceinventory.common.CommonProxy")
    public static CommonProxy proxy;

	@EventHandler
	public void preInit(FMLPreInitializationEvent event) {
	    GTCEInventoryCapabilityManager.init();
        GTCEInventoryMetaBlocks.init();
        GTCEInventoryMetaItems.init();
        GTCEInventoryMetaTileEntities.init();
        proxy.onPreLoad();
        MinecraftForge.EVENT_BUS.register(this);
	}

    @Mod.EventHandler
    public void onInit(FMLInitializationEvent event) {
        proxy.onLoad();
        GTCEInventoryCoverBehaviors.init();
    }

    @SubscribeEvent
    public void onConfigChangedEvent(final ConfigChangedEvent.OnConfigChangedEvent event) {
        if (event.getModID().equals(MODID)) {
            ConfigManager.sync(MODID, Config.Type.INSTANCE);
        }
    }
}
