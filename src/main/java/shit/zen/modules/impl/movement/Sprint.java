package shit.zen.modules.impl.movement;

import shit.zen.event.EventTarget;
import shit.zen.event.impl.RotationEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.modules.impl.player.InventoryManager;
import net.minecraft.client.KeyMapping;

public class Sprint extends Module {

    public Sprint() {
        super("Sprint", Category.MOVEMENT);
        this.setEnabled(true);
    }

    @EventTarget
    public void onRotation(RotationEvent event) {
        if (mc.player == null) return;

        // 正在使用物品栏/其他动作时不处理
        if (GuiMove.INSTANCE.isEnabled() && InventoryManager.isPerformingAction) {
            return;
        }

        boolean canSprint = mc.player.getFoodData().getFoodLevel() > 6
                && !mc.player.isCrouching()
                && !mc.player.isUsingItem()
                && mc.player.onGround() || mc.player.isInWater()
                && (mc.options.keyUp.isDown() || mc.options.keyDown.isDown()
                || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown());

        KeyMapping.set(mc.options.keySprint.getKey(), canSprint);
    }
}