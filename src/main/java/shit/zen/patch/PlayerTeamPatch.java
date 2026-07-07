package shit.zen.patch;

import asm.patchify.annotation.Overwrite;
import asm.patchify.annotation.Patch;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.scores.PlayerTeam;
import shit.zen.modules.impl.world.BoardReplace;
import shit.zen.utils.misc.ReflectionUtil;

/**
 * 拦截 PlayerTeam 的 playerPrefix / playerSuffix 读取，配合 BoardReplace 模块，
 * 在文本里检测到"布吉岛"就替换成"吉吉吉"。
 *
 * 用 @Overwrite 而不是 @Inject 的原因：这两个方法有返回值，而这个补丁框架里
 * @Inject 只能取消方法执行、不能直接替换返回值（没有 CallbackInfoReturnable 这种东西），
 * 所以只能整体重写方法体，通过 ReflectionUtil 读出原本存的字段值再决定要不要替换。
 */
@Patch(PlayerTeam.class)
public class PlayerTeamPatch {

    @Overwrite(method = "getPlayerPrefix", desc = "()Lnet/minecraft/network/chat/Component;")
    public static Component overwriteGetPlayerPrefix(PlayerTeam team) {
        Component original = (Component) ReflectionUtil.getStaticField(team, "playerPrefix", "net/minecraft/world/scores/PlayerTeam");
        return replaceIfNeeded(original);
    }

    @Overwrite(method = "getPlayerSuffix", desc = "()Lnet/minecraft/network/chat/Component;")
    public static Component overwriteGetPlayerSuffix(PlayerTeam team) {
        Component original = (Component) ReflectionUtil.getStaticField(team, "playerSuffix", "net/minecraft/world/scores/PlayerTeam");
        return replaceIfNeeded(original);
    }

    private static Component replaceIfNeeded(Component original) {
        if (original == null) {
            return original;
        }
        if (BoardReplace.INSTANCE == null || !BoardReplace.INSTANCE.isEnabled()) {
            return original;
        }

        String text = original.getString();
        if (!text.contains(BoardReplace.TARGET_TEXT)) {
            return original;
        }

        String replaced = text.replace(BoardReplace.TARGET_TEXT, BoardReplace.REPLACEMENT_TEXT);
        MutableComponent newComponent = Component.literal(replaced);
        return newComponent.withStyle(original.getStyle());
    }
}
