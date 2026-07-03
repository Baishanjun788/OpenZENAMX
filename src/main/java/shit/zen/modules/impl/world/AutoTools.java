package shit.zen.modules.impl.world;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import shit.zen.event.impl.MotionEvent;
import shit.zen.event.impl.UpdateHeldItemEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.settings.impl.BooleanSetting;
import shit.zen.utils.game.ItemUtil;
import shit.zen.event.EventTarget;

public class AutoTools extends Module {
    private final BooleanSetting checkSword = new BooleanSetting("Check Sword", true);
    private final BooleanSetting switchBack = new BooleanSetting("Switch Back", true);
    private final BooleanSetting silent = new BooleanSetting("Silent", true);

    private int previousSlot = -1;
    private boolean isMining = false;

    public AutoTools() {
        super("AutoTools", Category.WORLD);
    }

    @EventTarget
    public void onUpdateHeldItem(UpdateHeldItemEvent event) {
        if (mc.player == null) return;

        // 🛠️ 修复 1：真正的静默（Silent）切换逻辑
        // 如果开启了 Silent，且当前正在挖掘，我们在渲染层保持显示之前的物品
        if (this.silent.getValue() && this.isMining && this.previousSlot != -1) {
            if (event.getHand() == InteractionHand.MAIN_HAND) {
                event.setItemStack(mc.player.getInventory().getItem(this.previousSlot));
            }
        }
    }

    @EventTarget
    public void onMotion(MotionEvent motionEvent) {
        if (mc.player == null || mc.gameMode == null || mc.level == null) {
            return;
        }

        // 🛠️ 修复 2：将所有状态检查统一调整到 Pre 阶段，避免由于包序产生高频切手持的死循环
        if (motionEvent.isPre()) {
            // 判断当前游戏内左键是否正在按住挖掘方块
            if (mc.gameMode.isDestroying()) {
                ItemStack heldStack = mc.player.getMainHandItem();

                // 如果开启了 Check Sword 且手持是剑，则不自动切换工具（防止 PVP 破盾或砍人时工具乱跳）
                if (this.checkSword.getValue() && heldStack.getItem() instanceof SwordItem) {
                    return;
                }

                if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) {
                    BlockHitResult blockHit = (BlockHitResult) mc.hitResult;
                    int bestSlot = this.getBestTool(blockHit.getBlockPos());

                    if (bestSlot != -1 && bestSlot != mc.player.getInventory().selected) {
                        // 如果之前没有记录过旧位置，在这里记录一次
                        if (this.previousSlot == -1) {
                            this.previousSlot = mc.player.getInventory().selected;
                        }

                        this.isMining = true;

                        // 🛠️ 修复 3：实现 Silent 切换与常规切换的分流
                        if (this.silent.getValue()) {
                            // 静默切换：不需要直接修改客户端的 selected。
                            // 直接往服务器发送切换物品槽的数据包，欺骗服务器拿着最佳工具即可
                            mc.getConnection().send(new ServerboundSetCarriedItemPacket(bestSlot));
                        } else {
                            // 常规切换：直接修改客户端指针
                            mc.player.getInventory().selected = bestSlot;
                        }
                    }
                }
            } else {
                // 如果没有处于挖掘状态，且需要切回
                if (this.isMining) {
                    this.isMining = false;
                    if (this.switchBack.getValue() && this.previousSlot != -1) {
                        if (this.silent.getValue()) {
                            // 静默切回
                            mc.getConnection().send(new ServerboundSetCarriedItemPacket(this.previousSlot));
                        } else {
                            // 常规切回
                            mc.player.getInventory().selected = this.previousSlot;
                        }
                        this.previousSlot = -1;
                    }
                }
            }
        }
    }

    private int getBestTool(BlockPos blockPos) {
        BlockState blockState = mc.level.getBlockState(blockPos);
        if (blockState.isAir()) return -1;

        Block block = blockState.getBlock();
        int bestSlot = -1;
        float bestSpeed = 1.0f;

        for (int i = 0; i < 9; ++i) { // 遍历快捷栏
            ItemStack itemStack = mc.player.getInventory().getItem(i);

            // 过滤无效物品（不要把武器当工具，除非挖蜘蛛网）
            if (ItemUtil.isWeaponItem(itemStack) || itemStack.isEmpty()) continue;
            if (itemStack.getItem() instanceof SwordItem && !(block instanceof WebBlock)) continue;

            // 获取该工具对特定方块的基础挖掘速度
            float destroySpeed = itemStack.getItem().getDestroySpeed(itemStack, blockState);

            if (destroySpeed > 1.0f) {
                // 🛠️ 修复 4：彻底移除了原代码对红石矿和钻石矿等经验方块的附魔限制，全面参与效率计算
                int efficiencyLevel = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_EFFICIENCY, itemStack);
                if (efficiencyLevel > 0) {
                    destroySpeed += (float) (efficiencyLevel * efficiencyLevel + 1);
                }

                // 挑选速度最快的那个
                if (destroySpeed > bestSpeed) {
                    bestSpeed = destroySpeed;
                    bestSlot = i;
                }
            }
        }

        return bestSlot;
    }
}