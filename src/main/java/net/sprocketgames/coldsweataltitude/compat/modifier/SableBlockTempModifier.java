package net.sprocketgames.coldsweataltitude.compat.modifier;

import com.momosoftworks.coldsweat.api.registry.BlockTempRegistry;
import com.momosoftworks.coldsweat.api.temperature.block_temp.BlockTemp;
import com.momosoftworks.coldsweat.api.temperature.modifier.TempModifier;
import com.momosoftworks.coldsweat.api.util.Temperature;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.sprocketgames.coldsweataltitude.compat.SableSublevelContext;
import net.sprocketgames.coldsweataltitude.compat.SableSublevelResolver;
import net.sprocketgames.coldsweataltitude.compat.blocktemp.AeronauticsHeatSourceBlockTemp;

import java.util.Collection;
import java.util.function.Function;

public final class SableBlockTempModifier extends TempModifier
{
    @Override
    protected Function<Double, Double> calculate(LivingEntity entity, Temperature.Trait trait)
    {
        if (!(entity instanceof Player player) || trait != Temperature.Trait.WORLD)
        {
            return temp -> temp;
        }

        SableSublevelContext context = SableSublevelResolver.INSTANCE.resolve(player);
        if (context == null)
        {
            return temp -> temp;
        }

        Level level = context.level();
        Vec3 localPosition = context.localPosition();
        BlockPos center = context.localBlockPos();
        int scanRange = AeronauticsHeatSourceBlockTemp.scanRange();
        double total = 0.0D;

        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-scanRange, -scanRange, -scanRange),
            center.offset(scanRange, scanRange, scanRange)))
        {
            BlockState state = level.getBlockState(pos);
            Collection<BlockTemp> blockTemps = BlockTempRegistry.getBlockTempsFor(state);
            if (blockTemps.isEmpty())
            {
                continue;
            }

            double distance = Vec3.atCenterOf(pos).subtract(localPosition).length();
            for (BlockTemp blockTemp : blockTemps)
            {
                if (!blockTemp.isValid(level, pos, state))
                {
                    continue;
                }
                total += blockTemp.getTemperature(level, player, state, pos, distance);
            }
        }

        double finalTotal = total;
        return temp -> temp + finalTotal;
    }
}
