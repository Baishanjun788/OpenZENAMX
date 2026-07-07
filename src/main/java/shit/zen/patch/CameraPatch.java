package shit.zen.patch;

import asm.patchify.annotation.At;
import asm.patchify.annotation.Inject;
import asm.patchify.annotation.Patch;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import shit.zen.ZenClient;
import shit.zen.modules.impl.render.FreeCam;

/**
 * 在游戏原本算完这一帧的相机位置/朝向之后（Camera.setup 跑完），
 * 如果 FreeCam 开着，就把相机坐标强制换成 FreeCam 里维护的那个自由视角坐标。
 * 朝向（yaw/pitch）不动，还是跟着玩家实际的视角走，这样鼠标看方向完全正常，
 * 只有"人在哪"和"镜头在哪"这两件事被拆开了。
 */
@Patch(Camera.class)
public class CameraPatch {

    @Inject(
            method = "setup",
            desc = "(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V",
            at = @At(At.Type.TAIL)
    )
    public static void onSetup(Camera camera, BlockGetter blockGetter, Entity entity, boolean detached, boolean thirdPerson, float partialTick, CallbackInfo callbackInfo) {
        if (!ZenClient.isReady()) {
            return;
        }
        if (FreeCam.INSTANCE == null || !FreeCam.INSTANCE.isEnabled()) {
            return;
        }

        Vec3 freeCamPosition = FreeCam.INSTANCE.getPosition();
        if (freeCamPosition == null) {
            return;
        }

        camera.setPosition(freeCamPosition);
    }
}
