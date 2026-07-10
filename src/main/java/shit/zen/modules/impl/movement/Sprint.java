package shit.zen.modules.impl.movement;

import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.modules.impl.player.InventoryManager;
import shit.zen.modules.impl.combat.WTap; // 确保导入了 WTap
import shit.zen.event.EventTarget;
import shit.zen.event.impl.MotionEvent;

public class Sprint extends Module {

    public Sprint() {
        super("Sprint", Category.MOVEMENT);
        this.setEnabled(true);
    }

    @EventTarget
    public void onMotion(MotionEvent e) {
        if (!e.pre || mc.player == null || mc.options == null) return;

        // 1. 优先级最高：避让冲突逻辑
        // 如果 InventoryManager 正在压制，或者 WTap 正在执行重置(needReset)
        boolean isInventorySuppressing = (InventoryManager.INSTANCE != null && InventoryManager.INSTANCE.isSuppressingSprint());
        boolean isWTapResetting = (WTap.INSTANCE != null && WTap.INSTANCE.isEnabled() && WTap.INSTANCE.isNeedReset());
        boolean isGuiMoving = (GuiMove.INSTANCE != null && GuiMove.INSTANCE.isEnabled());

        if (isInventorySuppressing || isWTapResetting || isGuiMoving) {
            stopSprinting();
            return;
        }

        // 2. 正常逻辑：开启疾跑
        mc.options.keySprint.setDown(true);
        if (mc.options.toggleSprint() != null) {
            mc.options.toggleSprint().set(false);
        }
    }

    @Override
    public void onDisable() {
        stopSprinting();
        super.onDisable();
    }

    private void stopSprinting() {
        if (mc.options != null) {
            mc.options.keySprint.setDown(false);
        }
        if (mc.player != null) {
            mc.player.setSprinting(false);
        }
    }
}