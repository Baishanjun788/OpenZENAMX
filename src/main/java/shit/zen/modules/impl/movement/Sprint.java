package shit.zen.modules.impl.movement;

import shit.zen.event.impl.TickEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.modules.impl.player.InventoryManager;
import shit.zen.event.EventTarget;

public class Sprint extends Module {

    public static Sprint INSTANCE;

    public Sprint() {
        super("Sprint", Category.MOVEMENT);
        INSTANCE = this;
        // 默认开启
        this.setEnabled(true);
    }

    @Override
    public void onDisable() {
        // 🛠️ 修复 1：模块关闭时，将原版的疾跑状态安全交还给游戏本身，防止按键状态死锁
        if (mc.player != null) {
            mc.player.setSprinting(false);
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        // 🛠️ 修复 2：将事件由不稳定的 RotationEvent 迁移至每 Tick 触发的 TickEvent，保证直线行走不熄火
        if (mc.player == null || mc.level == null) return;

        // 兼容原版的背包移动（GuiMove）过滤
        if (GuiMove.INSTANCE != null && GuiMove.INSTANCE.isEnabled() && InventoryManager.isPerformingAction) {
            return;
        }

        /* * 🛠️ 修复 3：加入严密的安全条件判定，防止触发反作弊的恶意发包拦截：
         * 1. mc.player.input.hasForwardImpulse() -> 只有玩家按住了 W 键（往前走）时才跑。
         * 2. !mc.player.isHorizontalColliding() -> 如果一头撞在方块墙上，不触发疾跑。
         * 3. !mc.player.isShiftKeyDown() -> 如果玩家正在按住 Shift 潜行偷人，不触发疾跑。
         * 4. mc.player.getFoodData().getFoodLevel() > 6 -> 饱食度大于 3 颗心，满足原版疾跑体能条件。
         */
        boolean canSprint = mc.player.input.hasForwardImpulse()
                && !mc.player.horizontalCollision
                && !mc.player.isShiftKeyDown()
                && mc.player.getFoodData().getFoodLevel() > 6;

        if (canSprint) {
            // 真正安全且不破坏按键映射的官方设置疾跑方式
            mc.player.setSprinting(true);
        }
    }
}