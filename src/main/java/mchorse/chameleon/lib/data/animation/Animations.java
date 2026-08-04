package mchorse.chameleon.lib.data.animation;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Every {@link Animation} loaded for one model, keyed by its Bedrock id. Filled
 * from all of the model folder's {@code .animation.json} files (the folder's own
 * file plus everything under {@code animations/}), so ids collide across files
 * on a last-one-wins basis, exactly as legacy.
 *
 * Legacy source: chameleon/.../lib/data/animation/Animations.java
 */
public class Animations
{
    public Map<String, Animation> animations = new HashMap<String, Animation>();

    public Collection<Animation> getAll()
    {
        return this.animations.values();
    }

    public void add(Animation animation)
    {
        this.animations.put(animation.id, animation);
    }

    public Animation get(String id)
    {
        return this.animations.get(id);
    }
}