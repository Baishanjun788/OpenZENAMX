package shit.zen.modules.impl.movement;

import shit.zen.event.EventTarget;
import shit.zen.event.impl.RotationEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.modules.impl.player.InventoryManager;

public class Sprint extends Module {

    public Sprint() {
        super("Sprint", Category.MOVEMENT);
        this.setEnabled(true);
    }

    @EventTarget
    public void onRotation(RotationEvent event) {
        if (mc.player == null) return;
        if (GuiMove.INSTANCE.isEnabled() && InventoryManager.isPerformingAction) {
            mc.options.keySprint.setDown(false);
            return;
        }
        mc.options.keySprint.setDown(true);
    }

    @Override
    public void onDisable() {
        if (mc.player != null && mc.options != null) {
            mc.options.keySprint.setDown(false);
        }
        super.onDisable();
    }
}