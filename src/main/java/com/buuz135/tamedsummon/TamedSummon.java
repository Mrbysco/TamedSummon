package com.buuz135.tamedsummon;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.Collection;

@Mod("tamedsummon")
public class TamedSummon {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final SimpleCommandExceptionType ERROR_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.summon.failed"));
	private static final SimpleCommandExceptionType ERROR_DUPLICATE_UUID = new SimpleCommandExceptionType(Component.translatable("commands.summon.failed.uuid"));
	private static final SimpleCommandExceptionType INVALID_POSITION = new SimpleCommandExceptionType(Component.translatable("commands.summon.invalidPosition"));

	public TamedSummon() {
		MinecraftForge.EVENT_BUS.register(this);
	}

	@SubscribeEvent
	public void onRegisterCommands(RegisterCommandsEvent event) {
		CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
		CommandBuildContext context = event.getBuildContext();

		final LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("tamedsummon")
				.requires((commandSource) -> commandSource.hasPermission(2))
				.then(Commands.argument("entity", ResourceArgument.resource(context, Registries.ENTITY_TYPE))
						.suggests(SuggestionProviders.SUMMONABLE_ENTITIES)
						.then(Commands.argument("owner", EntityArgument.entities())
								.then(Commands.argument("pos", Vec3Argument.vec3())
										.executes((source) -> {
											return summonEntity(source.getSource(),
													ResourceArgument.getSummonableEntityType(source, "entity"),
													EntityArgument.getEntities(source, "owner"),
													Vec3Argument.getVec3(source, "pos"));
										})
								)
						)
				);

		dispatcher.register(root);
	}

	private static int summonEntity(CommandSourceStack source, Holder.Reference<EntityType<?>> type, Collection<? extends Entity> targets, Vec3 pos) throws CommandSyntaxException {
		BlockPos blockpos = BlockPos.containing(pos);
		if (!Level.isInSpawnableBounds(blockpos)) {
			throw INVALID_POSITION.create();
		} else {
			int amount = 0;

			for (Entity target : targets) {
				amount++;

				CompoundTag compoundtag = new CompoundTag();
				compoundtag.putString("id", type.key().location().toString());
				compoundtag.putUUID("Owner", target.getUUID());
				ServerLevel serverlevel = source.getLevel();
				Entity entity = EntityType.loadEntityRecursive(compoundtag, serverlevel, (entity1) -> {
					entity1.moveTo(pos.x, pos.y, pos.z, entity1.getYRot(), entity1.getXRot());
					return entity1;
				});
				if (entity == null) {
					throw ERROR_FAILED.create();
				}

				if (entity instanceof Mob mob) {
					mob.finalizeSpawn(source.getLevel(), source.getLevel().getCurrentDifficultyAt(entity.blockPosition()), MobSpawnType.COMMAND, (SpawnGroupData) null, (CompoundTag) null);
				}

				if (!serverlevel.tryAddFreshEntityWithPassengers(entity)) {
					throw ERROR_DUPLICATE_UUID.create();
				}

				source.sendSuccess(() -> Component.translatable("commands.summon.success", entity.getDisplayName()), true);
			}

			return amount;
		}
	}

}
