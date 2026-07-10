package shit.zen.modules.impl.player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.netty.handler.ssl.NotSslRecordException;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.block.Blocks;
import org.apache.commons.lang3.tuple.Pair;
import shit.zen.event.impl.MotionEvent;
import shit.zen.event.impl.PacketEvent;
import shit.zen.event.impl.SprintEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.modules.impl.combat.KillAura;
import shit.zen.modules.impl.movement.NoSlow;
import shit.zen.modules.impl.movement.Scaffold;
import shit.zen.settings.impl.BooleanSetting;
import shit.zen.settings.impl.ModeSetting;
import shit.zen.settings.impl.NumberSetting;
import shit.zen.utils.animation.Timer;
import shit.zen.utils.game.BlockUtil;
import shit.zen.utils.game.ItemUtil;
import shit.zen.utils.game.MovementUtil;
import shit.zen.utils.math.MathUtil;
import shit.zen.utils.misc.ChatUtil;
import shit.zen.event.EventTarget;

public class InventoryManager extends Module {
    public static InventoryManager INSTANCE;

    // Timing settings (界面上是 Ticks 概念)
    private final NumberSetting minDelaySetting = new NumberSetting("Min Delay (Ticks)", 200, 0, 500, 1);
    private final NumberSetting maxDelaySetting = new NumberSetting("Max Delay (Ticks)",  200, 0,500, 1);

    // Core features
    private final BooleanSetting autoArmorSetting = new BooleanSetting("Auto Armor", true);
    private final BooleanSetting throwItemsSetting = new BooleanSetting("Throw Items", true);

    // Silent management
    private final BooleanSetting silentManageSetting = new BooleanSetting("Bypass Silent Manage", false);
    private final BooleanSetting inventoryOnlySetting = new BooleanSetting("Inventory Only", false);
    // Offhand settings
    private final ModeSetting offhandItemSetting = new ModeSetting("Offhand Items", "None", "Golden Apple", "Projectile", "Fishing Rod", "Block").withDefault("None");

    // Hotbar slot assignments
    private final BooleanSetting switchSwordSetting = new BooleanSetting("Switch Sword", true);
    private final NumberSetting swordSlotSetting = new NumberSetting("Sword Slot", 1, 1, 9, 1);

    private final BooleanSetting switchBlockSetting = new BooleanSetting("Switch Block", true);
    private final NumberSetting blockSlotSetting = new NumberSetting("Block Slot", 2, 1, 9, 1);
    private final NumberSetting maxBlockSizeSetting = new NumberSetting("Max Block Size", 256, 64, 512, 64);

    private final BooleanSetting switchPickaxeSetting = new BooleanSetting("Switch Pickaxe", true);
    private final NumberSetting pickaxeSlotSetting = new NumberSetting("Pickaxe Slot", 3, 1, 9, 1);

    private final BooleanSetting switchAxeSetting = new BooleanSetting("Switch Axe", true);
    private final NumberSetting axeSlotSetting = new NumberSetting("Axe Slot", 4, 1, 9, 1);

    private final BooleanSetting switchBowSetting = new BooleanSetting("Switch Bow or Crossbow", true);
    private final NumberSetting bowSlotSetting = new NumberSetting("Bow Slot", 5, 1, 9, 1);
    private final ModeSetting bowPrioritySetting = new ModeSetting("Bow Priority", "Crossbow", "Power Bow", "Punch Bow").withDefault("Power Bow");
    private final NumberSetting maxArrowSizeSetting = new NumberSetting("Max Arrow Size", 256, 64, 512, 64);

    private final BooleanSetting switchWaterBucketSetting = new BooleanSetting("Switch Water Bucket", true);
    private final NumberSetting waterBucketSlotSetting = new NumberSetting("Water Bucket Slot", 6, 1, 9, 1);
    private final NumberSetting waterBucketCountSetting = new NumberSetting("Keep Water Buckets", 1, 0, 5, 1);

    private final BooleanSetting switchLavaBucketSetting = new BooleanSetting("Switch Lava Bucket", true);
    private final NumberSetting lavaBucketSlotSetting = new NumberSetting("Lava Bucket Slot", 7, 1, 9, 1);
    private final NumberSetting lavaBucketCountSetting = new NumberSetting("Keep Lava Buckets", 1, 0, 5, 1);

    private final BooleanSetting switchEnderPearlSetting = new BooleanSetting("Switch Ender Pearl", true);
    private final NumberSetting pearlSlotSetting = new NumberSetting("Ender Pearl Slot", 8, 1, 9, 1);

    private final BooleanSetting switchFireballSetting = new BooleanSetting("Switch Fireball", true);
    private final NumberSetting fireballSlotSetting = new NumberSetting("Fireball Slot", 9, 1, 9, 1);

    private final BooleanSetting switchGoldenAppleSetting = new BooleanSetting("Switch Golden Apple", true);
    private final NumberSetting goldenAppleSlotSetting = new NumberSetting("Golden Apple Slot", 9, 1, 9, 1);

    // Projectile settings
    private final BooleanSetting keepProjectileSetting = new BooleanSetting("Keep Eggs & Snowballs", true);
    private final BooleanSetting switchProjectileSetting = new BooleanSetting("Switch Eggs & Snowballs", false);
    private final NumberSetting projectileSlotSetting = new NumberSetting("Eggs & Snowballs Slot", 9, 1, 9, 1);
    private final NumberSetting maxProjectileSizeSetting = new NumberSetting("Max Eggs & Snowballs Size", 64, 16, 256, 16);

    // Rod settings
    private final BooleanSetting switchRodSetting = new BooleanSetting("Switch Rod", false);
    private final NumberSetting rodSlotSetting = new NumberSetting("Rod Slot", 9, 1, 9, 1);

    // Timer
    private static final Timer actionTimer = new Timer();

    // State variables
    private int noMoveTicks = 0;
    private int suppressSprintTicks = 0;
    private boolean suppressSprint = false;
    private boolean prevSuppressSprint = false;
    private int resprintCountdown = 0;
    private boolean clickOffHand = false;
    private boolean inventoryOpen = false;
    private boolean cancelNextInventoryOpenPacket = false;
    private boolean silentInventoryClickPrimed = false;
    private boolean sendingSilentInventoryPackets = false;
    private boolean movingSilentAction = false;
    private boolean wasSprinting = false;
    private int movingSilentDelayTicks = 0;
    private final Queue<Packet<?>> pendingMovingSilentPackets = new ConcurrentLinkedQueue<>();
    private int pendingSilentThrowSlot = -1;
    private int pendingSilentThrowButton = 0;
    private int pendingSilentThrowTicks = 0;

    private boolean movingSilentWaiting = false;

    public InventoryManager() {
        super("InventoryManager", Category.PLAYER, 66);
        INSTANCE = this;
    }

    @Override
    protected void onDisable() {
        this.resetSilentManageState();
        super.onDisable();
    }

    public static int getMaxArrows() {
        return 256;
    }

    public static int getMaxBlockSize() {
        return INSTANCE != null ? INSTANCE.maxBlockSizeSetting.getValue().intValue() : 256;
    }

    public static boolean shouldKeepProjectile() {
        return INSTANCE != null && INSTANCE.keepProjectileSetting.getValue();
    }

    public static int getMaxProjectileSize() {
        return INSTANCE != null ? INSTANCE.maxProjectileSizeSetting.getValue().intValue() : 64;
    }

    public static int getMaxArrowSize() {
        return INSTANCE != null ? INSTANCE.maxArrowSizeSetting.getValue().intValue() : 256;
    }

    public static int getWaterBucketCount() {
        return INSTANCE != null ? INSTANCE.waterBucketCountSetting.getValue().intValue() : 1;
    }

    public static int getLavaBucketCount() {
        return INSTANCE != null ? INSTANCE.lavaBucketCountSetting.getValue().intValue() : 1;
    }
    public static int getMaxWaterBuckets() {
        return 1;
    }

    public static int getMaxLavaBuckets() {
        return 1;
    }
    public static int getMaxEggsSnowballsSize() {
        return INSTANCE.maxProjectileSizeSetting .getValue().intValue();
    }


    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!event.isIncoming() || mc.player == null || mc.getConnection() == null) {
            return;
        }

        Packet<?> packet = event.getPacket();

        if (this.handleMovingSilentPacket(event)) {
            return;
        }

        if (this.inventoryOpen && packet instanceof ServerboundInteractPacket) {
            this.forceSilentClose();
        }

        if (this.sendingSilentInventoryPackets) {
            if (packet instanceof ServerboundContainerClosePacket) {
                this.inventoryOpen = false;
            }
            return;
        }

        if (this.cancelNextInventoryOpenPacket
                && packet instanceof ServerboundPlayerCommandPacket command
                && command.getAction() == ServerboundPlayerCommandPacket.Action.OPEN_INVENTORY) {
            event.setCancelled(true);
            this.cancelNextInventoryOpenPacket = false;
            this.silentInventoryClickPrimed = true;
            return;
        }

        if (this.silentInventoryClickPrimed && packet instanceof ServerboundContainerClickPacket clickPacket) {
            event.setCancelled(true);
            this.silentInventoryClickPrimed = false;
            this.sendSilentInventoryPackets(clickPacket);
            return;
        }

        if (packet instanceof ServerboundContainerClosePacket) {
            this.inventoryOpen = false;
        }

        if (packet instanceof ServerboundPlayerCommandPacket command) {
            if (command.getAction() == ServerboundPlayerCommandPacket.Action.START_SPRINTING) {
                this.wasSprinting = true;
            } else if (command.getAction() == ServerboundPlayerCommandPacket.Action.STOP_SPRINTING) {
                this.wasSprinting = false;
            }
        }
    }

    @EventTarget
    public void onSprint(SprintEvent event) {
        if (this.isSuppressingSprint() && mc.player != null) {
            mc.options.keySprint.setDown(false);
            mc.player.setSprinting(false);
        }
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (event.isPost()) {
            this.flushMovingSilentPackets();
            return;
        }

        if (!event.isPre() || mc.player == null || mc.getConnection() == null || mc.gameMode == null) {
            return;
        }

        if (!this.checkConfig()) {
            this.setEnabled(false);
            return;
        }

        if (ItemUtil.hasServerItem()) {
            return;
        }

        boolean moving = MovementUtil.isInputActive();
        if (moving) {
            this.noMoveTicks = 0;
        } else {
            this.noMoveTicks++;
        }

        if (this.isExternalContainerOpen()) {
            this.resetSilentManageState(false);
            return;
        }

        if (moving && this.silentManageSetting.getValue() && this.inventoryOnlySetting.getValue()) {
            this.resetSilentManageState();
            return;
        }

        if (this.silentManageSetting.getValue() && !this.inventoryOnlySetting.getValue() && !this.canRunMovingSilentManage()) {
            this.clickOffHand = false;
            return;
        }

        // Scaffold 或 KillAura 正在执行动作时，不整理、不停止疾跑，释放压制让 Sprint 模块恢复疾跑
        if (this.shouldPauseForAction()) {
            this.suppressSprint = false;
            this.suppressSprintTicks = 0;
            this.movingSilentAction = false;
            this.movingSilentWaiting = false;
            this.movingSilentDelayTicks = 0;
            this.wasSprinting = false;
            this.clickOffHand = false;
            this.pendingMovingSilentPackets.clear();
            this.pendingSilentThrowSlot = -1;
            this.pendingSilentThrowButton = 0;
            this.pendingSilentThrowTicks = 0;
            return;
        }

        boolean hasPendingInventoryActions = this.shouldSuppressSprint();
        boolean movingSilentBusy = this.shouldPauseMovingSilentSprint();

        this.suppressSprint = hasPendingInventoryActions;

        if (hasPendingInventoryActions || movingSilentBusy) {
            if (this.silentManageSetting.getValue() && !this.inventoryOnlySetting.getValue()) {
                if (!this.movingSilentWaiting) {
                    this.movingSilentWaiting = true;
                    this.movingSilentAction = true;
                    this.wasSprinting = this.wasSprinting || mc.player.isSprinting();
                    this.movingSilentDelayTicks = this.wasSprinting || mc.player.isSprinting() ? 4 : 2;
                }
            }
            this.suppressSprintTicks = 2;
            this.pauseSprint();
        } else if (this.suppressSprintTicks > 0) {
            this.suppressSprintTicks--;
        }
        if (this.silentManageSetting.getValue() && !this.inventoryOnlySetting.getValue()) {
            if (this.movingSilentWaiting) {
                if (this.movingSilentDelayTicks > 0) {
                    this.movingSilentDelayTicks--;
                    return;
                }
                // delay 结束，退出等待状态
                this.movingSilentWaiting = false;
                this.movingSilentAction = false;
            }
        }

        if (this.runPendingSilentThrow()) {
            return;
        }

        if (mc.player.isUsingItem()) {
            this.clickOffHand = false;
            return;
        }

        if (mc.screen instanceof AbstractContainerScreen container
                && container.getMenu().containerId != mc.player.inventoryMenu.containerId) {
            return;
        }

        this.performInventoryManagement();

        if (!this.shouldSuppressSprint() && this.pendingMovingSilentPackets.isEmpty()) {
            this.movingSilentAction = false;
        }
    }

    private boolean checkConfig() {
        List<Pair<Boolean, NumberSetting>> pairs = new ArrayList<>();
        if (!this.keepProjectileSetting.getValue()) {
            this.switchProjectileSetting.setValue(false);
        }

        if (this.switchSwordSetting.getValue()) {
            pairs.add(Pair.of(true, this.swordSlotSetting));
        }
        if (this.switchPickaxeSetting.getValue()) {
            pairs.add(Pair.of(true, this.pickaxeSlotSetting));
        }
        if (this.switchAxeSetting.getValue()) {
            pairs.add(Pair.of(true, this.axeSlotSetting));
        }
        if (this.switchBowSetting.getValue()) {
            pairs.add(Pair.of(true, this.bowSlotSetting));
        }
        if (this.switchWaterBucketSetting.getValue()) {
            pairs.add(Pair.of(true, this.waterBucketSlotSetting));
        }
        if (this.switchLavaBucketSetting.getValue()) {
            pairs.add(Pair.of(true, this.lavaBucketSlotSetting));
        }
        if (this.switchEnderPearlSetting.getValue()) {
            pairs.add(Pair.of(true, this.pearlSlotSetting));
        }
        if (this.switchFireballSetting.getValue()) {
            pairs.add(Pair.of(true, this.fireballSlotSetting));
        }
        if (this.switchGoldenAppleSetting.getValue() && !"Golden Apple".equals(this.offhandItemSetting.getValue())) {
            pairs.add(Pair.of(true, this.goldenAppleSlotSetting));
        }
        if (this.switchProjectileSetting.getValue() && !"Projectile".equals(this.offhandItemSetting.getValue())) {
            pairs.add(Pair.of(true, this.projectileSlotSetting));
        }
        if (this.switchRodSetting.getValue() && !"Fishing Rod".equals(this.offhandItemSetting.getValue())) {
            pairs.add(Pair.of(true, this.rodSlotSetting));
        }
        if (this.switchBlockSetting.getValue() && !"Block".equals(this.offhandItemSetting.getValue())) {
            pairs.add(Pair.of(true, this.blockSlotSetting));
        }

        Set<Integer> usedSlot = new HashSet<>();
        for (Pair<Boolean, NumberSetting> pair : pairs) {
            int targetSlot = pair.getValue().getValue().intValue() - 1;
            if (usedSlot.contains(targetSlot)) {
                return false;
            }
            usedSlot.add(targetSlot);
        }

        return true;
    }

    private void performInventoryManagement() {

        float currentDelayMs = (float) MathUtil.randomInt(this.minDelaySetting.getValue().intValue(), this.maxDelaySetting.getValue().intValue()) * 50F;

        if (this.autoArmorSetting.getValue()) {
            for (int i = 0; i < mc.player.getInventory().armor.size(); i++) {
                ItemStack stack = mc.player.getInventory().armor.get(i);
                if (stack.getItem() instanceof ArmorItem item) {
                    if (!stack.isEmpty()
                            && ItemUtil.getBestArmorScore(item.getEquipmentSlot()) > ItemUtil.getArmorScore(stack)) {
                        if (actionTimer.hasPassed(currentDelayMs)) {
                            this.clickInventory(4 + (4 - i), 1, ClickType.THROW);
                            this.inventoryOpen = true;
                            actionTimer.reset();
                            return;
                        }
                    }
                }
            }

            for (int ix = 0; ix < mc.player.getInventory().items.size(); ix++) {
                ItemStack stack = mc.player.getInventory().items.get(ix);
                if (!stack.isEmpty() && stack.getItem() instanceof ArmorItem item) {
                    float currentItemScore = ItemUtil.getArmorScore(stack);
                    boolean isBestItem = ItemUtil.getBestArmorScore(item.getEquipmentSlot()) == currentItemScore;
                    boolean isBetterItem = ItemUtil.getEquippedArmorScore(item.getEquipmentSlot()) < currentItemScore;
                    if (isBestItem && isBetterItem) {
                        if (actionTimer.hasPassed(currentDelayMs)) {
                            int target = ix < 9 ? ix + 36 : ix;
                            this.clickInventory(target, 0, ClickType.QUICK_MOVE);
                            this.inventoryOpen = true;
                            actionTimer.reset();
                            return;
                        }
                    }
                }
            }
        }

        if (this.clickOffHand) {
            if (actionTimer.hasPassed(currentDelayMs)) {
                this.clickInventory(45, 0, ClickType.PICKUP);
                this.inventoryOpen = true;
                this.clickOffHand = false;
                actionTimer.reset();
                return;
            }
        }

        String offhandPref = this.offhandItemSetting.getValue();
        if ("Golden Apple".equals(offhandPref)) {
            ItemStack offHand = mc.player.getInventory().offhand.get(0);
            int slot = ItemUtil.getSlot(Items.GOLDEN_APPLE);
            if (slot != -1) {
                if (offHand.getItem() == Items.GOLDEN_APPLE) {
                    ItemStack goldenAppleStack = mc.player.getInventory().items.get(slot);
                    if (offHand.getCount() + goldenAppleStack.getCount() <= 64) {
                        if (actionTimer.hasPassed(currentDelayMs)) {
                            int target = slot < 9 ? slot + 36 : slot;
                            this.clickInventory(target, 0, ClickType.PICKUP);
                            this.inventoryOpen = true;
                            this.clickOffHand = true;
                            actionTimer.reset();
                            return;
                        }
                    }
                } else {
                    if (actionTimer.hasPassed(currentDelayMs)) {
                        this.swapOffHand(slot);
                        return;
                    }
                }
            }
        } else if ("Projectile".equals(offhandPref)) {
            ItemStack offHand = mc.player.getInventory().offhand.get(0);
            ItemStack bestProjectile = ItemUtil.getBestProjectile();
            if (bestProjectile != null) {
                int slot = ItemUtil.getSlot(bestProjectile);
                boolean shouldSwap = offHand.getItem() != Items.EGG && offHand.getItem() != Items.SNOWBALL;

                if (shouldSwap && slot != -1) {
                    if (actionTimer.hasPassed(currentDelayMs)) {
                        this.swapOffHand(slot);
                        return;
                    }
                }
            }
        } else if ("Fishing Rod".equals(offhandPref)) {
            ItemStack offHand = mc.player.getInventory().offhand.get(0);
            int slot = ItemUtil.getSlot(Items.FISHING_ROD);
            if (slot != -1 && offHand.getItem() != Items.FISHING_ROD) {
                if (actionTimer.hasPassed(currentDelayMs)) {
                    this.swapOffHand(slot);
                    return;
                }
            }
        } else if ("Block".equals(offhandPref)) {
            ItemStack offHand = mc.player.getInventory().offhand.get(0);
            ItemStack bestBlock = ItemUtil.getBestBlock();
            if (bestBlock != null) {
                int slot = ItemUtil.getSlot(bestBlock);
                boolean shouldSwap = !BlockUtil.isPlaceable(offHand);

                if (shouldSwap && slot != -1) {
                    if (actionTimer.hasPassed(currentDelayMs)) {
                        this.swapOffHand(slot);
                        return;
                    }
                }
            }
        }

        if (this.switchGoldenAppleSetting.getValue() && !"Golden Apple".equals(this.offhandItemSetting.getValue())) {
            if (this.swapItem((int) (this.goldenAppleSlotSetting.getValue().intValue() - 1), Items.GOLDEN_APPLE)) {
                return;
            }
        }

        if (this.switchBlockSetting.getValue() && !"Block".equals(this.offhandItemSetting.getValue())) {
            int blockSlot = (int) (this.blockSlotSetting.getValue().intValue() - 1);
            ItemStack currentBlock = mc.player.getInventory().items.get(blockSlot);
            ItemStack bestBlock = ItemUtil.getBestBlock();
            if (bestBlock != null && !BlockUtil.isPlaceable(currentBlock)) {
                if (this.swapItem(blockSlot, bestBlock)) {
                    return;
                }
            }
        }

        if (ItemUtil.countBlocks() > this.maxBlockSizeSetting.getValue().intValue()) {
            ItemStack worstBlock = ItemUtil.getWorstBlock();
            if (this.throwItem(worstBlock)) {
                return;
            }
        }

        if (this.switchSwordSetting.getValue()) {
            int slot = (int) (this.swordSlotSetting.getValue().intValue() - 1);
            ItemStack currentSword = mc.player.getInventory().items.get(slot);
            ItemStack bestSword = ItemUtil.getBestSword();
            ItemStack bestShapeAxe = ItemUtil.getBestSharpAxe();
            if (ItemUtil.getAxeDamage(bestShapeAxe) > ItemUtil.getSwordDamage(bestSword)) {
                bestSword = bestShapeAxe;
            }

            if (bestSword != null) {
                float currentDamage = currentSword.getItem() instanceof SwordItem
                        ? ItemUtil.getSwordDamage(currentSword)
                        : ItemUtil.getAxeDamage(currentSword);
                float bestWeaponDamage = bestSword.getItem() instanceof SwordItem
                        ? ItemUtil.getSwordDamage(bestSword)
                        : ItemUtil.getAxeDamage(bestSword);
                if (bestWeaponDamage > currentDamage) {
                    if (this.swapItem(slot, bestSword)) {
                        return;
                    }
                }
            }
        }

        if (this.switchPickaxeSetting.getValue()) {
            int slot = (int) (this.pickaxeSlotSetting.getValue().intValue() - 1);
            ItemStack bestPickaxe = ItemUtil.getBestPickaxe();
            ItemStack currentPickaxe = mc.player.getInventory().items.get(slot);
            if (bestPickaxe != null
                    && bestPickaxe.getItem() instanceof PickaxeItem
                    && (ItemUtil.getDigSpeed(bestPickaxe) > ItemUtil.getDigSpeed(currentPickaxe) || !(currentPickaxe.getItem() instanceof PickaxeItem))) {
                if (this.swapItem(slot, bestPickaxe)) {
                    return;
                }
            }
        }

        if (this.switchAxeSetting.getValue()) {
            int slot = (int) (this.axeSlotSetting.getValue().intValue() - 1);
            ItemStack bestAxe = ItemUtil.getBestAxe();
            ItemStack currentAxe = mc.player.getInventory().items.get(slot);
            if (bestAxe != null
                    && bestAxe.getItem() instanceof AxeItem
                    && (ItemUtil.getDigSpeed(bestAxe) > ItemUtil.getDigSpeed(currentAxe) || !(currentAxe.getItem() instanceof AxeItem))) {
                if (this.swapItem(slot, bestAxe)) {
                    return;
                }
            }
        }

        if (this.switchRodSetting.getValue() && !"Fishing Rod".equals(this.offhandItemSetting.getValue())) {
            int slot = (int) (this.rodSlotSetting.getValue().intValue() - 1);
            ItemStack bestRod = ItemUtil.getFishingRodStack();
            ItemStack currentRod = mc.player.getInventory().items.get(slot);
            if (bestRod != null && !(currentRod.getItem() instanceof FishingRodItem)) {
                if (this.swapItem(slot, bestRod)) {
                    return;
                }
            }
        }

        if (this.switchBowSetting.getValue()) {
            int slot = (int) (this.bowSlotSetting.getValue().intValue() - 1);
            ItemStack bestBow = this.getBowToSwap();
            if (bestBow != null) {
                if (this.swapItem(slot, bestBow)) {
                    return;
                }
            }

            if (ItemUtil.countItem(Items.ARROW) > this.maxArrowSizeSetting.getValue().intValue()) {
                ItemStack worstArrow = ItemUtil.getArrowStack();
                if (this.throwItem(worstArrow)) {
                    return;
                }
            }
        }

        if (this.switchEnderPearlSetting.getValue()) {
            if (this.swapItem((int) (this.pearlSlotSetting.getValue().intValue() - 1), Items.ENDER_PEARL)) {
                return;
            }
        }

        if (this.switchWaterBucketSetting.getValue()) {
            if (this.swapItem((int) (this.waterBucketSlotSetting.getValue().intValue() - 1), Items.WATER_BUCKET)) {
                return;
            }
        }

        if (this.switchLavaBucketSetting.getValue()) {
            if (this.swapItem((int) (this.lavaBucketSlotSetting.getValue().intValue() - 1), Items.LAVA_BUCKET)) {
                return;
            }
        }

        if (this.switchFireballSetting.getValue()) {
            if (this.swapItem((int) (this.fireballSlotSetting.getValue().intValue() - 1), Items.FIRE_CHARGE)) {
                return;
            }
        }

        if (this.keepProjectileSetting.getValue()) {
            if (ItemUtil.countItem(Items.EGG) + ItemUtil.countItem(Items.SNOWBALL) > this.maxProjectileSizeSetting.getValue().intValue()) {
                ItemStack worstProjectile = ItemUtil.getWorstProjectile();
                if (this.throwItem(worstProjectile)) {
                    return;
                }
            }

            if (this.switchProjectileSetting.getValue() && !"Projectile".equals(this.offhandItemSetting.getValue())) {
                int projectileSlot = (int) (this.projectileSlotSetting.getValue().intValue() - 1);
                if (ItemUtil.countItem(Items.EGG) > 0) {
                    if (this.swapItem(projectileSlot, Items.EGG)) {
                        return;
                    }
                } else if (ItemUtil.countItem(Items.SNOWBALL) > 0) {
                    if (this.swapItem(projectileSlot, Items.SNOWBALL)) {
                        return;
                    }
                }
            }
        }

        if (this.throwItemsSetting.getValue()) {
            List<Integer> slots = IntStream.range(0, mc.player.getInventory().items.size()).boxed().collect(Collectors.toList());
            Collections.shuffle(slots);

            for (Integer slotId : slots) {
                ItemStack stack = mc.player.getInventory().items.get(slotId);
                if (!stack.isEmpty() && !this.isItemUseful(stack)) {
                    if (this.throwItem(stack)) {
                        return;
                    }
                }
            }
        }
    }

    private ItemStack getBowToSwap() {
        if (!this.switchBowSetting.getValue() || mc.player == null) {
            return null;
        }

        int slot = this.bowSlotSetting.getValue().intValue() - 1;
        ItemStack currentBow = mc.player.getInventory().items.get(slot);
        ItemStack bestBow;
        float bestBowScore;
        float currentBowScore;
        if ("Crossbow".equals(this.bowPrioritySetting.getValue())) {
            bestBow = ItemUtil.getBestCrossbow();
            bestBowScore = ItemUtil.getCrossbowScore(bestBow);
            currentBowScore = ItemUtil.getCrossbowScore(currentBow);
        } else if ("Power Bow".equals(this.bowPrioritySetting.getValue())) {
            bestBow = ItemUtil.getBestBow();
            bestBowScore = ItemUtil.getPowerBowScore(bestBow);
            currentBowScore = ItemUtil.getPowerBowScore(currentBow);
        } else {
            bestBow = ItemUtil.getBestBow();
            bestBowScore = ItemUtil.getBowScore(bestBow);
            currentBowScore = ItemUtil.getBowScore(currentBow);
        }

        if (bestBow == null) {
            bestBow = ItemUtil.getBestCrossbow();
            bestBowScore = ItemUtil.getCrossbowScore(bestBow);
            currentBowScore = ItemUtil.getCrossbowScore(currentBow);
        }

        if (bestBow == null) {
            bestBow = ItemUtil.getBestPowerBow();
            bestBowScore = ItemUtil.getPowerBowScore(bestBow);
            currentBowScore = ItemUtil.getPowerBowScore(currentBow);
        }

        if (bestBow == null) {
            bestBow = ItemUtil.getBestBow();
            bestBowScore = ItemUtil.getBowScore(bestBow);
            currentBowScore = ItemUtil.getBowScore(currentBow);
        }

        if (bestBow != null && bestBowScore > currentBowScore) {
            return bestBow;
        }
        return null;
    }

    public boolean isItemUseful(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (ItemUtil.isWeaponItem(stack)) {
            return true;
        }
        if (stack.getDisplayName().getString().contains("点击使用")) {
            return true;
        }
        if (stack.getItem() == Items.COBWEB) {
            return true;
        }
        if (stack.getItem() instanceof ArmorItem armor) {
            float score = ItemUtil.getArmorScore(stack);
            if (ItemUtil.getEquippedArmorScore(armor.getEquipmentSlot()) >= score) {
                return false;
            }
            return score >= ItemUtil.getBestArmorScore(armor.getEquipmentSlot());
        }
        if (stack.getItem() instanceof SwordItem) {
            return ItemUtil.getBestSword() == stack;
        }
        if (stack.getItem() instanceof PickaxeItem) {
            return ItemUtil.getBestPickaxe() == stack;
        }
        if (stack.getItem() instanceof AxeItem && !ItemUtil.isLegitAxe(stack)) {
            return ItemUtil.getBestAxe() == stack;
        }
        if (stack.getItem() instanceof ShovelItem) {
            return ItemUtil.getBestShovel() == stack;
        }
        if (stack.getItem() instanceof CrossbowItem) {
            return ItemUtil.getBestCrossbow() == stack;
        }
        if (stack.getItem() instanceof BowItem && ItemUtil.isGoodBow(stack)) {
            return ItemUtil.getBestBow() == stack;
        }
        if (stack.getItem() instanceof BowItem && ItemUtil.isGoodBowAlt(stack)) {
            return ItemUtil.getBestBowAlt() == stack;
        }
        if (stack.getItem() instanceof BowItem && ItemUtil.countItem(Items.BOW) > 1) {
            return false;
        }
        if (stack.getItem() == Items.WATER_BUCKET && ItemUtil.countItem(Items.WATER_BUCKET) > getWaterBucketCount()) {
            return false;
        }
        if (stack.getItem() == Items.LAVA_BUCKET && ItemUtil.countItem(Items.LAVA_BUCKET) > getLavaBucketCount()) {
            return false;
        }
        if (stack.getItem() instanceof FishingRodItem && ItemUtil.countItem(Items.FISHING_ROD) > 1) {
            return false;
        }
        if ((stack.getItem() == Items.SNOWBALL || stack.getItem() == Items.EGG) && !shouldKeepProjectile()) {
            return false;
        }
        if (stack.getItem() instanceof ItemNameBlockItem) {
            return false;
        }
        return ItemUtil.isUsableItem(stack);
    }

    private void swapOffHand(int slot) {
        int target = slot < 9 ? slot + 36 : slot;
        this.clickInventory(target, 40, ClickType.SWAP);
        this.inventoryOpen = true;
        actionTimer.reset();
    }

    private boolean shouldPauseForAction() {
        if (Scaffold.INSTANCE != null && Scaffold.INSTANCE.isEnabled()) return true;
        if (KillAura.INSTANCE != null && KillAura.INSTANCE.isEnabled() && KillAura.target != null) return true;
        return isNoSlowActive();
    }

    public boolean isSuppressingSprint() {
        return this.suppressSprint || this.shouldPauseMovingSilentSprint();
    }

    private boolean shouldPauseMovingSilentSprint() {
        return this.silentManageSetting.getValue()
                && !this.inventoryOnlySetting.getValue()
                && (this.movingSilentAction || !this.pendingMovingSilentPackets.isEmpty() || this.sendingSilentInventoryPackets);
    }

    private boolean isNoSlowActive(){
        if (mc.player == null){
            return false;
        }
        if(NoSlow.INSTANCE == null || !NoSlow.INSTANCE.isEnabled()){
            return false;
        }
        if (mc.options.keyUse.isDown()) {
            ItemStack offhandStack = mc.player.getItemInHand(InteractionHand.OFF_HAND);
            ItemStack mainhandStack = mc.player.getItemInHand(InteractionHand.MAIN_HAND);
            if ((!offhandStack.isEmpty() && offhandStack.isEdible()) || (!mainhandStack.isEmpty() && mainhandStack.isEdible()) ) {
                return true;
            }
        }
        return false;
    }

    private boolean canRunMovingSilentManage() {
        return this.silentManageSetting.getValue()
                && !this.inventoryOnlySetting.getValue()
                && mc.player != null
                && mc.getConnection() != null
                && mc.gameMode != null
                && !(Scaffold.INSTANCE != null && Scaffold.INSTANCE.isEnabled())
                && !isNoSlowActive()
                && this.isSafeForSilentManage();
    }

    private boolean shouldSuppressSprint() {
        if (mc.player == null) return false;

        if (isNoSlowActive()) {
            return false;
        }
        if(Scaffold.INSTANCE != null && Scaffold.INSTANCE.isEnabled()){
            return false;
        }
        if (!this.silentManageSetting.getValue() && !(mc.screen instanceof InventoryScreen)) {
            return false;
        }

        if (this.autoArmorSetting.getValue()) {
            for (int i = 0; i < mc.player.getInventory().armor.size(); i++) {
                ItemStack stack = mc.player.getInventory().armor.get(i);
                if (!stack.isEmpty() && stack.getItem() instanceof ArmorItem item) {
                    if (ItemUtil.getBestArmorScore(item.getEquipmentSlot()) > ItemUtil.getArmorScore(stack)) {
                        return true;
                    }
                }
            }

            for (int ix = 0; ix < mc.player.getInventory().items.size(); ix++) {
                ItemStack stack = mc.player.getInventory().items.get(ix);
                if (!stack.isEmpty() && stack.getItem() instanceof ArmorItem item) {
                    float currentItemScore = ItemUtil.getArmorScore(stack);
                    if (ItemUtil.getBestArmorScore(item.getEquipmentSlot()) == currentItemScore
                            && ItemUtil.getEquippedArmorScore(item.getEquipmentSlot()) < currentItemScore) {
                        return true;
                    }
                }
            }
        }

        String offhandPref = this.offhandItemSetting.getValue();
        if (!"None".equals(offhandPref)) {
            ItemStack offHand = mc.player.getInventory().offhand.get(0);
            if ("Golden Apple".equals(offhandPref) && ItemUtil.getSlot(Items.GOLDEN_APPLE) != -1 && offHand.getItem() != Items.GOLDEN_APPLE) return true;
            if ("Projectile".equals(offhandPref) && ItemUtil.getBestProjectile() != null && offHand.getItem() != Items.EGG && offHand.getItem() != Items.SNOWBALL) return true;
            if ("Fishing Rod".equals(offhandPref) && ItemUtil.getSlot(Items.FISHING_ROD) != -1 && offHand.getItem() != Items.FISHING_ROD) return true;
            if ("Block".equals(offhandPref) && ItemUtil.getBestBlock() != null && !BlockUtil.isPlaceable(offHand)) return true;
        }

        if (this.clickOffHand) return true;

        if (this.switchGoldenAppleSetting.getValue() && !"Golden Apple".equals(this.offhandItemSetting.getValue())) {
            int slot = (int) (this.goldenAppleSlotSetting.getValue().intValue() - 1);
            if (mc.player.getInventory().items.get(slot).getItem() != Items.GOLDEN_APPLE && ItemUtil.getSlot(Items.GOLDEN_APPLE) != -1) return true;
        }
        if (this.switchBlockSetting.getValue() && !"Block".equals(this.offhandItemSetting.getValue())) {
            int blockSlot = (int) (this.blockSlotSetting.getValue().intValue() - 1);
            if (!BlockUtil.isPlaceable(mc.player.getInventory().items.get(blockSlot)) && ItemUtil.getBestBlock() != null) return true;
        }
        if (this.switchSwordSetting.getValue()) {
            int slot = (int) (this.swordSlotSetting.getValue().intValue() - 1);
            if (ItemUtil.getBestSword() != null && mc.player.getInventory().items.get(slot) != ItemUtil.getBestSword()) return true;
        }
        if (this.switchPickaxeSetting.getValue()) {
            int slot = (int) (this.pickaxeSlotSetting.getValue().intValue() - 1);
            if (ItemUtil.getBestPickaxe() != null && mc.player.getInventory().items.get(slot) != ItemUtil.getBestPickaxe()) return true;
        }
        if (this.switchAxeSetting.getValue()) {
            int slot = (int) (this.axeSlotSetting.getValue().intValue() - 1);
            if (ItemUtil.getBestAxe() != null && mc.player.getInventory().items.get(slot) != ItemUtil.getBestAxe()) return true;
        }

        if (this.switchBowSetting.getValue()) {
            if (this.getBowToSwap() != null) return true;
            if (ItemUtil.countItem(Items.ARROW) > this.maxArrowSizeSetting.getValue().intValue()) return true;
        }

        if (ItemUtil.countBlocks() > this.maxBlockSizeSetting.getValue().intValue()) return true;

        if (this.keepProjectileSetting.getValue()
                && ItemUtil.countItem(Items.EGG) + ItemUtil.countItem(Items.SNOWBALL) > this.maxProjectileSizeSetting.getValue().intValue()) {
            return true;
        }

        if (this.switchWaterBucketSetting.getValue()) {
            int slot = (int) (this.waterBucketSlotSetting.getValue().intValue() - 1);
            if (mc.player.getInventory().items.get(slot).getItem() != Items.WATER_BUCKET && ItemUtil.getSlot(Items.WATER_BUCKET) != -1) return true;
        }

        if (this.switchLavaBucketSetting.getValue()) {
            int slot = (int) (this.lavaBucketSlotSetting.getValue().intValue() - 1);
            if (mc.player.getInventory().items.get(slot).getItem() != Items.LAVA_BUCKET && ItemUtil.getSlot(Items.LAVA_BUCKET) != -1) return true;
        }

        if (this.switchEnderPearlSetting.getValue()) {
            int slot = (int) (this.pearlSlotSetting.getValue().intValue() - 1);
            if (mc.player.getInventory().items.get(slot).getItem() != Items.ENDER_PEARL && ItemUtil.getSlot(Items.ENDER_PEARL) != -1) return true;
        }

        if (this.switchFireballSetting.getValue()) {
            int slot = (int) (this.fireballSlotSetting.getValue().intValue() - 1);
            if (mc.player.getInventory().items.get(slot).getItem() != Items.FIRE_CHARGE && ItemUtil.getSlot(Items.FIRE_CHARGE) != -1) return true;
        }

        if (this.keepProjectileSetting.getValue() && this.switchProjectileSetting.getValue() && !"Projectile".equals(this.offhandItemSetting.getValue())) {
            int slot = (int) (this.projectileSlotSetting.getValue().intValue() - 1);
            Item current = mc.player.getInventory().items.get(slot).getItem();
            if (current != Items.EGG && current != Items.SNOWBALL && (ItemUtil.countItem(Items.EGG) > 0 || ItemUtil.countItem(Items.SNOWBALL) > 0)) return true;
        }

        if (this.switchRodSetting.getValue() && !"Fishing Rod".equals(this.offhandItemSetting.getValue())) {
            int slot = (int) (this.rodSlotSetting.getValue().intValue() - 1);
            if (mc.player.getInventory().items.get(slot).getItem() != Items.FISHING_ROD && ItemUtil.getSlot(Items.FISHING_ROD) != -1) return true;
        }

        if (this.throwItemsSetting.getValue()) {
            for (ItemStack stack : mc.player.getInventory().items) {
                if (!stack.isEmpty() && !this.isItemUseful(stack)) {
                    return true;
                }
            }
            if (ItemUtil.countItem(Items.LAVA_BUCKET) > getLavaBucketCount()) {
                return true;
            }
        }

        return false;
    }

    private void pauseSprint() {
        mc.options.keySprint.setDown(false);
        if (mc.player.isSprinting()) {
            mc.player.setSprinting(false);
        }
    }

    private boolean throwItem(ItemStack item) {
        int itemSlot = ItemUtil.getSlot(item);
        if (itemSlot != -1) {
            float requiredDelayMs = (float) MathUtil.randomInt(this.minDelaySetting.getValue().intValue(), this.maxDelaySetting.getValue().intValue()) * 50F;
            if (actionTimer.hasPassed(requiredDelayMs)) {
                int target = itemSlot < 9 ? itemSlot + 36 : itemSlot;
                this.clickInventory(target, 1, ClickType.THROW);
                this.inventoryOpen = true;
                actionTimer.reset();
                return true;
            }
        }
        return false;
    }

    private boolean swapItem(int targetSlot, ItemStack bestItem) {
        int bestItemSlot = ItemUtil.getSlot(bestItem);
        if (bestItemSlot != -1) {
            float requiredDelayMs = (float) MathUtil.randomInt(this.minDelaySetting.getValue().intValue(), this.maxDelaySetting.getValue().intValue()) * 50F;
            if (actionTimer.hasPassed(requiredDelayMs)) {
                int source = bestItemSlot < 9 ? bestItemSlot + 36 : bestItemSlot;
                this.clickInventory(source, targetSlot, ClickType.SWAP);
                this.inventoryOpen = true;
                actionTimer.reset();
                return true;
            }
        }
        return false;
    }

    private boolean swapItem(int targetSlot, Item item) {
        ItemStack currentSlot = mc.player.getInventory().items.get(targetSlot);
        int bestItemSlot = ItemUtil.getSlot(item);
        if (bestItemSlot != -1) {
            if (currentSlot.getItem() != item) {
                float requiredDelayMs = (float) MathUtil.randomInt(this.minDelaySetting.getValue().intValue(), this.maxDelaySetting.getValue().intValue()) * 50F;
                if (actionTimer.hasPassed(requiredDelayMs)) {
                    int source = bestItemSlot < 9 ? bestItemSlot + 36 : bestItemSlot;
                    this.clickInventory(source, targetSlot, ClickType.SWAP);
                    this.inventoryOpen = true;
                    actionTimer.reset();
                    return true;
                }
            }
        }
        return false;
    }

    private void clickInventory(int slot, int button, ClickType clickType) {
        if (this.silentManageSetting.getValue()) {
            boolean throwingItem = clickType == ClickType.THROW;

            if (this.canRunMovingSilentManage() && MovementUtil.isInputActive()) {
                if (!this.movingSilentWaiting) {
                    this.movingSilentWaiting = true;
                    this.movingSilentAction = true;
                    this.wasSprinting = this.wasSprinting || mc.player.isSprinting();
                    this.movingSilentDelayTicks = this.wasSprinting ? 4 : 2;
                }
                mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, slot, button, clickType, mc.player);
                return;
            }

            if (throwingItem) {
                this.queueSilentThrow(slot, button);
                return;
            }

            this.beginSilentInventoryClick(false);
        }

        mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, slot, button, clickType, mc.player);
    }

    private boolean handleMovingSilentPacket(PacketEvent event) {
        if (!this.shouldUseMovingSilentQueue()) {
            return false;
        }

        Packet<?> packet = event.getPacket();
        if (packet instanceof ServerboundPlayerCommandPacket commandPacket) {
            if (commandPacket.getAction() == ServerboundPlayerCommandPacket.Action.START_SPRINTING) {
                this.wasSprinting = true;
            } else if (commandPacket.getAction() == ServerboundPlayerCommandPacket.Action.STOP_SPRINTING) {
                this.wasSprinting = false;
            }
        }

        if (this.isPlayerInventoryPacket(packet)) {
            event.setCancelled(true);
            this.pendingMovingSilentPackets.add(packet);
            this.movingSilentAction = true;
            this.movingSilentDelayTicks = Math.max(this.movingSilentDelayTicks, this.wasSprinting || mc.player.isSprinting() ? 4 : 2);
            return true;
        }

        return false;
    }

    private boolean flushMovingSilentPackets() {
        if (!this.silentManageSetting.getValue()
                || this.inventoryOnlySetting.getValue()
                || mc.player == null
                || mc.getConnection() == null
                || mc.gameMode == null) {
            return false;
        }

        if (this.pendingMovingSilentPackets.isEmpty()) {
            this.movingSilentAction = false;
            return false;
        }

        this.pauseSprint();

        if (this.movingSilentDelayTicks > 0) {
            this.movingSilentDelayTicks--;
            return true;
        }

        this.flushMovingSilentPacketsNow();
        return true;
    }

    private void flushMovingSilentPacketsNow() {
        if (mc.getConnection() == null || mc.player == null || this.pendingMovingSilentPackets.isEmpty()) {
            return;
        }

        this.sendingSilentInventoryPackets = true;
        try {
            while (!this.pendingMovingSilentPackets.isEmpty()) {
                mc.getConnection().send(this.pendingMovingSilentPackets.poll());
            }
            mc.getConnection().send(new ServerboundContainerClosePacket(mc.player.inventoryMenu.containerId));
        } finally {
            this.sendingSilentInventoryPackets = false;
            this.movingSilentAction = false;
            this.wasSprinting = false;
            this.movingSilentDelayTicks = 0;
            this.inventoryOpen = false;
        }
    }

    private boolean shouldUseMovingSilentQueue() {
        return this.silentManageSetting.getValue()
                && !this.inventoryOnlySetting.getValue()
                && !this.sendingSilentInventoryPackets
                && mc.player != null
                && mc.getConnection() != null
                && mc.gameMode != null
                && !this.isExternalContainerOpen()
                && this.isSafeForSilentManage();
    }

    private boolean isPlayerInventoryPacket(Packet<?> packet) {
        if (mc.player == null) {
            return false;
        }

        int inventoryContainerId = mc.player.inventoryMenu.containerId;
        if (packet instanceof ServerboundContainerClickPacket clickPacket) {
            return clickPacket.getContainerId() == inventoryContainerId;
        }

        if (packet instanceof ServerboundContainerClosePacket closePacket) {
            return closePacket.getContainerId() == inventoryContainerId;
        }

        return false;
    }

    private boolean isExternalContainerOpen() {
        if (mc.player == null || mc.screen == null) {
            return false;
        }

        if (mc.screen instanceof ContainerScreen containerScreen) {
            String title = containerScreen.getTitle().getString();
            String chest = Component.translatable("container.chest").getString();
            String chestDouble = Component.translatable("container.chestDouble").getString();
            if (title.equals(chest) || title.equals(chestDouble) || title.equals("Chest")) {
                return true;
            }
        }

        AbstractContainerMenu menu = mc.player.containerMenu;
        if (menu instanceof FurnaceMenu || menu instanceof BrewingStandMenu) {
            return true;
        }

        return mc.screen instanceof AbstractContainerScreen container
                && container.getMenu().containerId != mc.player.inventoryMenu.containerId;
    }

    private boolean isSafeForSilentManage() {
        if (mc.player == null || mc.level == null) {
            return false;
        }

        return !mc.player.isInWater()
                && !mc.player.isInLava()
                && !mc.player.isInPowderSnow
                && !mc.player.isVisuallyCrawling()
                && !mc.level.getBlockState(mc.player.blockPosition()).is(Blocks.SLIME_BLOCK)
                && !mc.level.getBlockState(mc.player.blockPosition().below()).is(Blocks.SLIME_BLOCK)
                && !mc.level.getBlockState(mc.player.blockPosition()).is(Blocks.HONEY_BLOCK)
                && !mc.level.getBlockState(mc.player.blockPosition().below()).is(Blocks.HONEY_BLOCK);
    }

    private boolean runPendingSilentThrow() {
        if (this.pendingSilentThrowSlot == -1) {
            return false;
        }

        if (MovementUtil.isInputActive()) {
            this.resetSilentManageState();
            return true;
        }

        if (this.pendingSilentThrowTicks > 0) {
            this.pendingSilentThrowTicks--;
            return true;
        }

        int slot = this.pendingSilentThrowSlot;
        int button = this.pendingSilentThrowButton;
        this.pendingSilentThrowSlot = -1;
        this.pendingSilentThrowButton = 0;
        this.beginSilentInventoryClick(true);
        mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, slot, button, ClickType.THROW, mc.player);
        this.inventoryOpen = true;
        actionTimer.reset();
        return true;
    }

    private void queueSilentThrow(int slot, int button) {
        this.pendingSilentThrowSlot = slot;
        this.pendingSilentThrowButton = button;
        this.pendingSilentThrowTicks = 0;
    }

    private void beginSilentInventoryClick(boolean keepSprintStopped) {
        if (!keepSprintStopped) {
            this.suppressSprint = false;
            this.suppressSprintTicks = 0;
        }

        this.cancelNextInventoryOpenPacket = true;
        this.silentInventoryClickPrimed = false;
        mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.OPEN_INVENTORY));
    }

    private void sendSilentInventoryPackets(ServerboundContainerClickPacket clickPacket) {
        this.sendingSilentInventoryPackets = true;
        try {
            mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.OPEN_INVENTORY));
            mc.getConnection().send(clickPacket);
            mc.getConnection().send(new ServerboundContainerClosePacket(clickPacket.getContainerId()));
            this.inventoryOpen = false;
        } finally {
            this.sendingSilentInventoryPackets = false;
        }
    }

    private void forceSilentClose() {
        if (!this.inventoryOpen || mc.getConnection() == null || mc.player == null) {
            return;
        }

        if (this.silentManageSetting.getValue()) {
            this.cancelNextInventoryOpenPacket = false;
            this.silentInventoryClickPrimed = false;
            this.sendingSilentInventoryPackets = false;
            mc.getConnection().send(new ServerboundContainerClosePacket(mc.player.inventoryMenu.containerId));
        }

        this.inventoryOpen = false;
        this.clickOffHand = false;
    }

    private void resetSilentManageState() {
        this.resetSilentManageState(true);
    }

    private void resetSilentManageState(boolean closeInventory) {
        this.clickOffHand = false;
        this.pendingSilentThrowSlot = -1;
        this.pendingSilentThrowButton = 0;
        this.pendingSilentThrowTicks = 0;
        this.cancelNextInventoryOpenPacket = false;
        this.silentInventoryClickPrimed = false;
        this.sendingSilentInventoryPackets = false;
        this.pendingMovingSilentPackets.clear();
        this.movingSilentWaiting = false;
        this.movingSilentAction = false;
        this.wasSprinting = false;
        this.movingSilentDelayTicks = 0;
        this.suppressSprint = false;
        this.suppressSprintTicks = 0;
        this.prevSuppressSprint = false;
        this.resprintCountdown = 0;
        if (closeInventory && this.inventoryOpen) {
            this.forceSilentClose();
        } else if (!closeInventory) {
            this.inventoryOpen = false;
        }
    }
}