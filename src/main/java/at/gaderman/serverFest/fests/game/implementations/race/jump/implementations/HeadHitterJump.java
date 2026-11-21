package at.gaderman.serverFest.fests.game.implementations.race.jump.implementations;

import at.gaderman.serverFest.fests.game.implementations.race.jump.JumpContext;
import at.gaderman.serverFest.fests.game.implementations.race.jump.JumpPattern;
import org.bukkit.Location;
import org.bukkit.Material;

import java.util.Random;

/// Adds a head hitter above the start of a {@link SimpleJump}
public final class HeadHitterJump extends SimpleJump {

   @Override
   public int difficulty() {
       return 2;
   }

   @Override
   public Location build(Location start, JumpContext ctx) {
       Location end = super.build(start, ctx);
       ctx.set(ctx.offset(end, 0, 3, 0), Material.RED_TERRACOTTA);
       return end;
   }
}