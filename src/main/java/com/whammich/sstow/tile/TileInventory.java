package com.whammich.sstow.tile;

import com.whammich.sstow.util.Utils;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraftforge.items.wrapper.SidedInvWrapper;
import tehnut.lib.util.helper.TextHelper;

public class TileInventory extends TileEntity implements IInventory {

    protected int[] syncedSlots = new int[0];
    private ItemStack[] inventory;
    private int size;
    private String name;

    public TileInventory(int size, String name) {
        this.inventory = new ItemStack[size];
        this.size = size;
        this.name = name;

        setItemHandlers();
    }

    private boolean isSyncedSlot(int slot) {
        for (int s : this.syncedSlots)
            if (s == slot)
                return true;

        return false;
    }

    @Override
    public void readFromNBT(NBTTagCompound tagCompound) {
        super.readFromNBT(tagCompound);
        NBTTagList tags = tagCompound.getTagList("Items", 10);
        inventory = new ItemStack[getSizeInventory()];

        for (int i = 0; i < tags.tagCount(); i++) {
            if (!isSyncedSlot(i)) {
                NBTTagCompound data = tags.getCompoundTagAt(i);
                byte j = data.getByte("Slot");

                if (j >= 0 && j < inventory.length)
                    inventory[j] = ItemStack.loadItemStackFromNBT(data);
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tagCompound) {
        super.writeToNBT(tagCompound);
        NBTTagList tags = new NBTTagList();

        for (int i = 0; i < inventory.length; i++) {
            if ((inventory[i] != null) && !isSyncedSlot(i)) {
                NBTTagCompound data = new NBTTagCompound();
                data.setByte("Slot", (byte) i);
                inventory[i].writeToNBT(data);
                tags.appendTag(data);
            }
        }

        tagCompound.setTag("Items", tags);
        return tagCompound;
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        NBTTagCompound nbt = new NBTTagCompound();
        writeToNBT(nbt);
        return new SPacketUpdateTileEntity(getPos(), -999, nbt);
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        super.onDataPacket(net, pkt);
        readFromNBT(pkt.getNbtCompound());
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Override
    public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }

    public void dropItems() {
        InventoryHelper.dropInventoryItems(getWorld(), getPos(), this);
    }

    public boolean insertItem(EntityPlayer player, EnumHand hand, int slot) {
        if (getStackInSlot(slot) == null && player.getHeldItem(hand) != null && isItemValidForSlot(slot, player.getHeldItem(hand))) {
            ItemStack input = player.getHeldItem(hand).copy();
            input.stackSize = 1;
            player.getHeldItem(hand).stackSize--;
            setInventorySlotContents(slot, input);
            return true;
        } else if (getStackInSlot(slot) != null && player.getHeldItem(hand) == null) {
            if (!getWorld().isRemote) {
                EntityItem invItem = new EntityItem(getWorld(), player.posX, player.posY + 0.25, player.posZ, getStackInSlot(slot));
                getWorld().spawnEntityInWorld(invItem);
            }
            clear();
            return false;
        }

        return false;
    }

    // Capability cheat codes

    private IItemHandler handler;
    private IItemHandler handlerUp;
    private IItemHandler handlerDown;
    private IItemHandler handlerNorth;
    private IItemHandler handlerSouth;
    private IItemHandler handlerEast;
    private IItemHandler handlerWest;

    protected void setItemHandlers() {
        if (this instanceof ISidedInventory) {
            handlerUp = new SidedInvWrapper((ISidedInventory) this, EnumFacing.UP);
            handlerDown = new SidedInvWrapper((ISidedInventory) this, EnumFacing.DOWN);
            handlerNorth = new SidedInvWrapper((ISidedInventory) this, EnumFacing.NORTH);
            handlerSouth = new SidedInvWrapper((ISidedInventory) this, EnumFacing.SOUTH);
            handlerEast = new SidedInvWrapper((ISidedInventory) this, EnumFacing.EAST);
            handlerWest = new SidedInvWrapper((ISidedInventory) this, EnumFacing.WEST);
        } else {
            handler = new InvWrapper(this);
        }
    }

    @Override
    public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
        return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY || super.hasCapability(capability, facing);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            if (this instanceof ISidedInventory && facing != null) {
                switch (facing) {
                    case UP:
                        return (T) handlerUp;
                    case DOWN:
                        return (T) handlerDown;
                    case NORTH:
                        return (T) handlerNorth;
                    case SOUTH:
                        return (T) handlerSouth;
                    case EAST:
                        return (T) handlerEast;
                    case WEST:
                        return (T) handlerWest;
                }
            } else {
                return (T) handler;
            }
        }

        return super.getCapability(capability, facing);
    }

    // IInventory

    @Override
    public int getSizeInventory() {
        return size;
    }

    @Override
    public ItemStack getStackInSlot(int index) {
        return inventory[index];
    }

    @Override
    public ItemStack decrStackSize(int index, int count) {
        if (inventory[index] != null) {
            if (!getWorld().isRemote)
                Utils.notifyUpdateBasic(getWorld(), getPos());
            if (inventory[index].stackSize <= count) {
                ItemStack itemStack = inventory[index];
                inventory[index] = null;
                markDirty();
                return itemStack;
            }

            ItemStack itemStack = inventory[index].splitStack(count);
            if (inventory[index].stackSize == 0)
                inventory[index] = null;

            markDirty();
            return itemStack;
        }

        return null;
    }

    @Override
    public ItemStack removeStackFromSlot(int slot) {
        if (inventory[slot] != null) {
            ItemStack itemStack = inventory[slot];
            setInventorySlotContents(slot, null);
            return itemStack;
        }
        return null;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        inventory[slot] = stack;
        if (stack != null && stack.stackSize > getInventoryStackLimit())
            stack.stackSize = getInventoryStackLimit();
        markDirty();
        if (!getWorld().isRemote)
            Utils.notifyUpdateBasic(getWorld(), getPos());
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return true;
    }

    @Override
    public void openInventory(EntityPlayer player) {

    }

    @Override
    public void closeInventory(EntityPlayer player) {

    }

    @Override
    public boolean isItemValidForSlot(int index, ItemStack stack) {
        return true;
    }

    @Override
    public int getField(int id) {
        return 0;
    }

    @Override
    public void setField(int id, int value) {

    }

    @Override
    public int getFieldCount() {
        return 0;
    }

    @Override
    public void clear() {
        this.inventory = new ItemStack[size];
    }

    // IWorldNameable

    @Override
    public String getName() {
        return TextHelper.localize("tile.SoulShardsTOW." + name + ".name");
    }

    @Override
    public boolean hasCustomName() {
        return true;
    }

    @Override
    public ITextComponent getDisplayName() {
        return new TextComponentString(getName());
    }
}
