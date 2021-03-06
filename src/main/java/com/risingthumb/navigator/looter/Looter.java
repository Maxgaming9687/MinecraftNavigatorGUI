package com.risingthumb.navigator.looter;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import com.risingthumb.navigator.classes.Marker;
import com.risingthumb.navigator.gui.GuiOptions;
import com.risingthumb.navigator.scheduling.ScheduledEvent;
import com.risingthumb.navigator.scheduling.Scheduler;
import com.risingthumb.navigator.util.CameraUtil;
import com.risingthumb.navigator.util.DijkstraAlgorithm;

import baritone.api.BaritoneAPI;
import baritone.api.event.events.PathEvent;
import baritone.api.event.listener.AbstractGameEventListener;
import baritone.api.pathing.goals.GoalBlock;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;

public class Looter implements AbstractGameEventListener {

	// Marker could probably be replaced with Vec3d
	public static LinkedList<Marker> chests = new LinkedList<>();
	public static ArrayList<Marker> blacklistedChests = new ArrayList<>();
	public static boolean firstLoot = true;
	public static int tickWaitTime = 80;
	public static boolean waitForNextEvent = true;
	
	public static void readAllChestLocations() {
		for (Marker m: chests) {
			Minecraft.getMinecraft().player.sendMessage(new TextComponentString("X:"+m.getX()+"Y:"+m.getY()+"Z"+m.getZ()));
		}
	}
	
	public static void fillNewChestLocations() {
		
		List<TileEntity> tileEntities = Minecraft.getMinecraft().world.loadedTileEntityList;
		ArrayList<Marker> chestUnqueued = new ArrayList<>();
		
		for(TileEntity te : tileEntities) {
			if(te instanceof TileEntityChest) {
				Marker mark = new Marker(te.getPos().getX(),te.getPos().getY(),te.getPos().getZ());
				chestUnqueued.add(mark);
			}
		}
		for(Marker c : blacklistedChests) {
			if(chestUnqueued.contains(c)) {
				chestUnqueued.remove(c);
			}
		}
		
		chests = DijkstraAlgorithm.calculateQueue(chestUnqueued);
	}
	
	public static void continueLooting() {
		Marker ncl = chests.peek();
		if (ncl == null) {
			fillNewChestLocations();
			ncl = chests.peek();
			if (ncl==null) {
				GuiOptions.looting=false; // Read below if you change this line
			}
		}
		
		if(GuiOptions.looting) { // Please note if you change the GuiOptions.looting part above, YOU NEED TO FIX THIS. If ncl is still null, IT WILL CRASH
			Looter.waitForNextEvent = true;
			BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(ncl.getX(), ncl.getY()+1, ncl.getZ()));
		}
		
	}

	@Override
	public void onPathEvent(PathEvent event) {
		if (event == PathEvent.CANCELED) {
			if(GuiOptions.looting && waitForNextEvent) {
				waitForNextEvent = false;
				Marker chestLoc = chests.remove();
				//Minecraft.getMinecraft().player.sendMessage(new TextComponentString(""+event));
				// This is to stop annoying anticheat as you jump onto it and remove the block.
				new Scheduler(10, new ScheduledEvent() {
					@Override
					public void run() {
						Minecraft.getMinecraft().playerController.clickBlock(new BlockPos(chestLoc.getX(),chestLoc.getY(),chestLoc.getZ()), EnumFacing.UP);
						Minecraft.getMinecraft().player.swingArm(EnumHand.MAIN_HAND);
						CameraUtil.lookAtCoordinates(Minecraft.getMinecraft().player, new Vec3d(chestLoc.getX(),chestLoc.getY(),chestLoc.getZ()));
						
						
						//Minecraft.getMinecraft().player.rotationPitch=90f;
					}
				});
				
				Looter.checkOnChest(chestLoc);
				
				new Scheduler(tickWaitTime, new ScheduledEvent() {
					@Override
					public void run() {
						Looter.waitForNextEvent = true;
						//Minecraft.getMinecraft().player.sendMessage(new TextComponentString("[!] Continuing looting"));
						Looter.continueLooting();
					}
				});
				
			}
		}
	}

	public static void checkOnChest(Marker chestLoc) {
		// Wait after punching chest to see how long it should take
		new Scheduler(200, new ScheduledEvent() {
			@Override
			public void run() {
				Block block = Minecraft.getMinecraft().world.getBlockState(new BlockPos(chestLoc.getX(),chestLoc.getY(),chestLoc.getZ())).getBlock();
				if (block != Blocks.AIR) {
					Looter.blacklistedChests.add(chestLoc);
					//Minecraft.getMinecraft().player.sendMessage(new TextComponentString("[!] Blacklisted chest"));
				}
			}
		});
		
		
	}
}
