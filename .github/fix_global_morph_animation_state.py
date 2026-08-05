from pathlib import Path

# Minecraft 1.12.2's EntityMorph copied the wearer's complete render-motion
# state into the inner entity every tick. The initial modern port intentionally
# left that bridge as a P54 seam, which means the dummy's own LimbAnimator can
# keep reporting movement while its wearer is idle. Widen the modern equivalent
# fields so EntityMorph can reproduce the legacy contract without depending on
# any particular entity mod or renderer.
aw_path = Path("src/main/resources/blockbuster.accesswidener")
aw = aw_path.read_text(encoding="utf-8")

aw_block = '''

# EntityMorph animation-state bridge: 1.12.2 mirrored limb swing, hand swing,
# movement inputs and environment state from the wearer to the inner entity.
# These are the 1.21.1 equivalents used by vanilla and modded renderers.
accessible field net/minecraft/entity/LivingEntity handSwinging Z
accessible field net/minecraft/entity/LivingEntity handSwingProgress F
accessible field net/minecraft/entity/LivingEntity handSwingTicks I
accessible field net/minecraft/entity/LivingEntity lastHandSwingProgress F
accessible field net/minecraft/entity/LivingEntity limbAnimator Lnet/minecraft/entity/LimbAnimator;
accessible field net/minecraft/entity/LivingEntity preferredHand Lnet/minecraft/util/Hand;
accessible field net/minecraft/entity/LivingEntity lastLeaningPitch F
accessible field net/minecraft/entity/LivingEntity leaningPitch F
accessible field net/minecraft/entity/LivingEntity fallFlyingTicks I
accessible field net/minecraft/entity/LivingEntity jumping Z
accessible field net/minecraft/entity/LivingEntity sidewaysSpeed F
accessible field net/minecraft/entity/LivingEntity upwardSpeed F
accessible field net/minecraft/entity/LivingEntity forwardSpeed F
accessible field net/minecraft/entity/LimbAnimator prevSpeed F
accessible field net/minecraft/entity/LimbAnimator speed F
accessible field net/minecraft/entity/LimbAnimator pos F
accessible field net/minecraft/entity/Entity touchingWater Z
accessible field net/minecraft/entity/Entity submergedInWater Z
'''

if "# EntityMorph animation-state bridge:" not in aw:
    aw += aw_block

aw_path.write_text(aw, encoding="utf-8")

morph_path = Path("src/main/java/mchorse/metamorph/api/morphs/EntityMorph.java")
morph = morph_path.read_text(encoding="utf-8")

old_seam = '''        /* SEAM(P54): limb-swing / hand-swing render-state mirroring (legacy
         * limbSwing, swingProgress, prevSwingProgress) is refined by the
         * P54 renderer against 1.20.4's LimbAnimator / hand-swing fields. */
'''

new_bridge = '''        /* Legacy's animation bridge, expressed through 1.21.1 state. This is
         * deliberately entity-agnostic: vanilla and modded renderers receive
         * the same movement, pose and environment signals as the wearer. */
        this.entity.limbAnimator.prevSpeed = target.limbAnimator.prevSpeed;
        this.entity.limbAnimator.speed = target.limbAnimator.speed;
        this.entity.limbAnimator.pos = target.limbAnimator.pos;

        this.entity.handSwinging = target.handSwinging;
        this.entity.handSwingProgress = target.handSwingProgress;
        this.entity.lastHandSwingProgress = target.lastHandSwingProgress;
        this.entity.handSwingTicks = target.handSwingTicks;
        this.entity.preferredHand = target.preferredHand;

        this.entity.lastLeaningPitch = target.lastLeaningPitch;
        this.entity.leaningPitch = target.leaningPitch;
        this.entity.fallFlyingTicks = target.fallFlyingTicks;
        this.entity.jumping = target.jumping;

        this.entity.sidewaysSpeed = target.sidewaysSpeed;
        this.entity.upwardSpeed = target.upwardSpeed;
        this.entity.forwardSpeed = target.forwardSpeed;

        this.entity.touchingWater = target.touchingWater;
        this.entity.submergedInWater = target.submergedInWater;
        this.entity.setSwimming(target.isSwimming());
        this.entity.setFlag(Entity.FALL_FLYING_FLAG_INDEX, target.isFallFlying());
        this.entity.setPose(target.getPose());
'''

if old_seam not in morph:
    raise SystemExit("Expected EntityMorph animation seam was not found")

morph = morph.replace(old_seam, new_bridge)
morph_path.write_text(morph, encoding="utf-8")

renderer_path = Path("src/client/java/mchorse/metamorph/client/render/EntityMorphRenderer.java")
renderer = renderer_path.read_text(encoding="utf-8")

old_pose = '''        /* 1.20.4 pulls the model's sneak flag from entity pose state rather than
         * a ModelBiped field, so the legacy isSneak save/restore becomes a pose
         * swap on the dummy. */
        EntityPose pose = dummy.getPose();

        dummy.setPose(entity.isSneaking() ? EntityPose.CROUCHING : EntityPose.STANDING);
'''

new_pose = '''        /* Modern renderers use the full pose, not only a biped sneak bit. Copy
         * it verbatim so swimming, fall-flying, crawling and every other entity
         * pose select their normal animation globally. */
        EntityPose pose = dummy.getPose();

        dummy.setPose(entity.getPose());
'''

if old_pose not in renderer:
    raise SystemExit("Expected binary standing/crouching pose bridge was not found")

renderer = renderer.replace(old_pose, new_pose)
renderer_path.write_text(renderer, encoding="utf-8")

print("Restored global morph animation-state mirroring for Minecraft 1.21.1.")
