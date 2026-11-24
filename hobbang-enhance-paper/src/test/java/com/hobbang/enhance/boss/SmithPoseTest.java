package com.hobbang.enhance.boss;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SmithPoseTest {

    @Test
    void smithHammerArcNeverPassesThroughHead() {
        BossMotion.Idle idle = new BossMotion.Idle();
        Map<String, Rigid> placed = new HashMap<>();
        BossPoses.Sink sink = (id, rig, scale) -> placed.put(id, rig);

        for (int i = 0; i <= 20; i++) {
            float p = i / 20.0F;
            placed.clear();
            BossMotion.Frame strike = new BossMotion.Frame(p, 0F);
            BossPoses.SmithRig rig = BossPoses.smith(sink, idle, 10F, 0F, 0F, 0F, 0F,
                strike, strike, BossMotion.Frame.REST, BossMotion.Frame.REST, BossMotion.Frame.REST, BossMotion.Frame.REST,
                BossPoses.Leap.NONE, false);
            assertHammerClearOfHead(rig.head(), rig.hammer(), "windup p=" + p);
        }

        for (int i = 0; i <= 20; i++) {
            float r = i / 20.0F;
            float p = 1F - r;
            placed.clear();
            BossMotion.Frame strike = new BossMotion.Frame(p, r);
            BossPoses.SmithRig rig = BossPoses.smith(sink, idle, 10F, 0F, 0F, 0F, 0F,
                strike, strike, BossMotion.Frame.REST, BossMotion.Frame.REST, BossMotion.Frame.REST, BossMotion.Frame.REST,
                BossPoses.Leap.NONE, false);
            assertHammerClearOfHead(rig.head(), rig.hammer(), "release r=" + r);
        }
    }

    private static void assertHammerClearOfHead(Rigid head, Rigid hammer, String phase) {
        Vector3f hp = head.pos();
        Vector3f mp = hammer.pos();
        float dist = hp.distance(mp);
        assertTrue(dist > 1.25F, phase + ": hammer too close to head! dist=" + dist + " head=" + hp + " hammer=" + mp);
    }

    @Test
    void smithOffAssetsExistAndHaveNoLightEmission() throws Exception {
        Path pack = Path.of("..", "resourcepack", "hobbang-pack", "assets", "hobbang");
        if (!Files.exists(pack)) return;
        List<String> parts = List.of(
            "boss/abyssal_smith/torso", "boss/abyssal_smith/chest", "boss/abyssal_smith/head", "boss/abyssal_smith/jaw",
            "boss/abyssal_smith/arm_l", "boss/abyssal_smith/arm_r", "boss/abyssal_smith/fore_l", "boss/abyssal_smith/fore_r",
            "boss/abyssal_smith/hand_l", "boss/abyssal_smith/hand_r", "boss/abyssal_smith/leg_l", "boss/abyssal_smith/leg_r",
            "boss/abyssal_smith/shin_l", "boss/abyssal_smith/shin_r", "boss/abyssal_smith/foot_l", "boss/abyssal_smith/foot_r",
            "boss/smith_hammer/hammer"
        );
        for (String part : parts) {
            Path model = pack.resolve("models/item/" + part + "_off.json");
            Path item = pack.resolve("items/" + part + "_off.json");
            assertTrue(Files.exists(model), "Missing model: " + model);
            assertTrue(Files.exists(item), "Missing item: " + item);
            String json = Files.readString(model);
            assertFalse(json.contains("light_emission"), "light_emission must be stripped in " + model);
        }
    }
}
