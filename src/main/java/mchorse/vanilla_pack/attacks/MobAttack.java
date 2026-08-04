package mchorse.vanilla_pack.attacks;

import mchorse.metamorph.api.abilities.IAttackAbility;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.EntityMorph;
import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.capabilities.morphing.Morphing;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Mob attack ability (roadmap P49.1, registry id {@code mob}).
 *
 * <p>This ability uses the mob's attack when the player attacks. Both attacks
 * will go through, taking the highest damage between the player and the mob, and
 * triggering any special effects the mob does on attack.</p>
 *
 * <p>API translation: {@code attackEntityAsMob(target)} →
 * {@link LivingEntity#tryAttack(Entity)}.</p>
 *
 * <p>Quirk preserved: the attack is read off the player's <b>current</b> morph
 * rather than off the {@code source}, so a settings-supplied {@code mob} attack
 * on a non-entity morph is a no-op.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/.../vanilla_pack/attacks/MobAttack.java
 */
public class MobAttack implements IAttackAbility
{
    @Override
    public void attack(Entity target, LivingEntity source)
    {
        if (source instanceof PlayerEntity)
        {
            PlayerEntity player = (PlayerEntity) source;
            IMorphing capability = Morphing.get(player);

            if (capability == null)
            {
                return;
            }

            AbstractMorph currentMorph = capability.getCurrentMorph();

            if (currentMorph == null)
            {
                return;
            }

            if (currentMorph instanceof EntityMorph)
            {
                EntityMorph currentEntityMorph = (EntityMorph) currentMorph;
                LivingEntity entity = currentEntityMorph.getEntity(source.getWorld());

                if (entity != null)
                {
                    entity.tryAttack(target);
                }
            }
        }
    }
}
