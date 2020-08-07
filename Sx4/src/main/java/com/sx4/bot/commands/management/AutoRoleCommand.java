package com.sx4.bot.commands.management;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.jockie.bot.core.command.Context;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.sx4.bot.annotations.command.AuthorPermissions;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.category.Category;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.model.Operators;
import com.sx4.bot.entities.argument.All;
import com.sx4.bot.entities.argument.TimedArgument;
import com.sx4.bot.entities.management.Filter;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.TimeUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

public class AutoRoleCommand extends Sx4Command {

	public AutoRoleCommand() {
		super("auto role");
		
		super.setDescription("Sets roles to be given when a user joins the server");
		super.setAliases("autorole");
		super.setExamples("auto role toggle", "auto role add", "auto role remove");
		super.setCategoryAll(Category.MANAGEMENT);
	}
	
	public void onCommand(Sx4CommandEvent event) {
		event.replyHelp().queue();
	}
	
	@Command(value="toggle", description="Enables/disables auto role in this server")
	@Examples({"auto role toggle"})
	@AuthorPermissions(permissions={Permission.MANAGE_ROLES})
	public void toggle(Sx4CommandEvent event) {
		List<Bson> update = List.of(Operators.set("autoRole.enabled", Operators.cond("$autoRole.enabled", Operators.REMOVE, true)));
		this.database.findAndUpdateGuildById(event.getGuild().getIdLong(), Projections.include("autoRole.enabled"), update).whenComplete((data, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}
			
			event.reply("Auto role is now **" + (data.getEmbedded(List.of("autoRole", "enabled"), false) ? "enabled" : "disabled") + "** " + this.config.getSuccessEmote()).queue();
		});
	}
	
	@Command(value="add", description="Add a role to be given when a user joins")
	@Examples({"auto role add @Role", "auto role add Role", "auto role add 406240455622262784"})
	@AuthorPermissions(permissions={Permission.MANAGE_ROLES})
	public void add(Sx4CommandEvent event, @Argument(value="role", endless=true) Role role) {
		if (role.isManaged()) {
			event.reply("I cannot give a managed role " + this.config.getFailureEmote()).queue();
			return;
		}
		
		if (role.isPublicRole()) {
			event.reply("I cannot give the `@everyone` role " + this.config.getFailureEmote()).queue();
			return;
		}
		
		if (!event.getSelfMember().canInteract(role)) {
			event.reply("I cannot give a role higher or equal than my top role " + this.config.getFailureEmote()).queue();
			return;
		}
		
		if (!event.getMember().canInteract(role)) {
			event.reply("You cannot give a role higher or equal than your top role " + this.config.getFailureEmote()).queue();
			return;
		}
		
		Document data = new Document("id", role.getIdLong());
		
		List<Bson> update = List.of(Operators.set("autoRole.roles", Operators.cond(Operators.or(Operators.extinct("$autoRole.roles"), Operators.eq(Operators.filter("$autoRole.roles", Operators.eq("$$this.id", role.getIdLong())), Collections.EMPTY_LIST)), Operators.cond(Operators.exists("$autoRole.roles"), Operators.concatArrays("$autoRole.roles", List.of(data)), List.of(data)), "$autoRole.roles")));
		this.database.updateGuildById(event.getGuild().getIdLong(), update).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}
			
			if (result.getModifiedCount() == 0) {
				event.reply("That role is already an auto role " + this.config.getFailureEmote()).queue();
				return;
			}
			
			event.reply("The role " + role.getAsMention() + " has been added as an auto role " + this.config.getSuccessEmote()).queue();
		});
	}
	
	@Command(value="remove", description="Remove a role from being given when a user joins")
	@Examples({"auto role remove @Role", "auto role remove Role", "auto role remove all"})
	@AuthorPermissions(permissions={Permission.MANAGE_ROLES})
	public void remove(Sx4CommandEvent event, @Argument(value="role", endless=true) All<Role> allArgument) {
		if (allArgument.isAll()) {
			this.database.updateGuildById(event.getGuild().getIdLong(), Updates.unset("autoRole.roles")).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}
				
				if (result.getModifiedCount() == 0) {
					event.reply("You have no auto roles setup " + this.config.getFailureEmote()).queue();
					return;
				}
				
				event.reply("All auto roles have been removed " + this.config.getSuccessEmote()).queue();
			});
		} else {
			Role role = allArgument.getValue();
			this.database.updateGuildById(event.getGuild().getIdLong(), Updates.pull("autoRole.roles", Filters.eq("id", role.getIdLong()))).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}
				
				if (result.getModifiedCount() == 0) {
					event.reply("That role is not an auto role " + this.config.getFailureEmote()).queue();
					return;
				}
				
				event.reply("The role " + role.getAsMention() + " has been removed from being an auto role " + this.config.getSuccessEmote()).queue();
			});
		}
	}
	
	@Command(value="list", description="Lists all the auto roles setup")
	@Examples({"auto role list"})
	public void list(Sx4CommandEvent event, @Context Guild guild) {
		List<Long> roleIds = this.database.getGuildById(event.getGuild().getIdLong(), Projections.include("autoRole.roles")).getEmbedded(List.of("autoRole", "roles"), Collections.emptyList());
		if (roleIds.isEmpty()) {
			event.reply("You have no auto roles setup " + this.config.getFailureEmote()).queue();
			return;
		}
		
		List<Role> roles = roleIds.stream()
			.map(guild::getRoleById)
			.filter(Objects::nonNull)
			.collect(Collectors.toList());
		
		PagedResult<Role> paged = new PagedResult<>(roles)
			.setAuthor("Auto Roles", null, guild.getIconUrl())
			.setIndexed(false)
			.setDisplayFunction(Role::getAsMention);
		
		paged.execute(event);
	}
	
	public class FilterCommand extends Sx4Command {
		
		public FilterCommand() {
			super("filter");
			
			super.setDescription("Add or remove filters from auto roles");
			super.setExamples("auto role filter add", "auto role filter remove", "auto role filter list");
		}
		
		public void onCommand(Sx4CommandEvent event) {
			event.replyHelp().queue();
		}
		
		@Command(value="add", description="Adds a filter to an auto role")
		@Examples({"auto role filter add @Role BOT", "auto role filter add Role CREATED_LESS_THAN 2d"})
		@AuthorPermissions(permissions={Permission.MANAGE_ROLES})
		public void add(Sx4CommandEvent event, @Argument(value="role") Role role, @Argument(value="filter", endless=true) TimedArgument<Filter> timedArgument) {
			Filter filter = timedArgument.getArgument();
			if (filter.hasDuration() && !timedArgument.hasDuration()) {
				event.reply("That filter requires a time interval to be given with it " + this.config.getFailureEmote()).queue();
				return;
			}
			
			List<Document> roles = this.database.getGuildById(event.getGuild().getIdLong(), Projections.include("autoRole.roles")).getEmbedded(List.of("autoRole", "roles"), Collections.emptyList());
			Document roleData = roles.stream()
				.filter(data -> data.getLong("id") == role.getIdLong())
				.findFirst()
				.orElse(null);
			
			if (roleData == null) {
				event.reply("That role is not an auto role " + this.config.getFailureEmote()).queue();
				return;
			}
			
			List<Document> filters = roleData.getList("filters", Document.class, Collections.emptyList());
			if (filters.stream().anyMatch(data -> data.getString("key").equals(filter.getKey()))) {
				event.reply("That auto role already has that filter or has a contradicting filter " + this.config.getFailureEmote()).queue();
				return;
			}
			
			Document filterData;
			if (filter.hasDuration() && timedArgument.hasDuration()) {
				filterData = filter.asDocument()
					.append("duration", timedArgument.getDuration().toSeconds());
			} else {
				filterData = filter.asDocument();
			}
			
			UpdateOptions options = new UpdateOptions().arrayFilters(List.of(Filters.eq("role.id", role.getIdLong())));
			this.database.updateGuildById(event.getGuild().getIdLong(), Updates.push("autoRole.roles.$[role].filters", filterData), options).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}
				
				event.reply("That auto role now has the filter `" + filter.name() + "` " + (timedArgument.hasDuration() ? "with a duration of " + TimeUtility.getTimeString(timedArgument.getSeconds()) + " " : "") + this.config.getSuccessEmote()).queue();
			});
		}
		
		@Command(value="remove", description="Removes a filter from an auto  role")
		@Examples({"auto role filter remove @Role BOT", "auto role filter remove Role CREATED_LESS_THAN"})
		@AuthorPermissions(permissions={Permission.MANAGE_ROLES})
		public void remove(Sx4CommandEvent event, @Argument(value="role") Role role, @Argument(value="filter") All<Filter> allArgument) {
			boolean all = allArgument.isAll();
			
			UpdateOptions options = new UpdateOptions().arrayFilters(List.of(Filters.eq("role.id", role.getIdLong())));
			Bson update = all ? Updates.unset("autoRole.roles.$[role].filters") : Updates.pull("autoRole.roles.$[role].filters", Filters.eq("key", allArgument.getValue().getKey()));
			this.database.updateGuildById(event.getGuild().getIdLong(), update, options).whenComplete((result, exception) -> {
				if (exception instanceof CompletionException) {
					Throwable cause = exception.getCause();
					if (cause instanceof MongoWriteException && ((MongoWriteException) cause).getCode() == 2) {
						event.reply("That auto role does not have " + (all ? "any" : "that") + " filter" + (all ? "s " : " ") + this.config.getFailureEmote()).queue();
						return;
					}
				}
				
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}
				
				if (result.getModifiedCount() == 0) {
					event.reply("That auto role does not have " + (all ? "any" : "that") + " filter" + (all ? "s " : " ") + this.config.getFailureEmote()).queue();
					return;
				}
				
				event.reply((all ? "All" : "That") + " filter" + (all ? "s have" : " has") + " been removed from that auto role " + this.config.getSuccessEmote()).queue();
			});
		}
		
		@Command(value="list", description="Lists all the filters you can use with descriptions")
		@Examples({"auto role filter list"})
		public void list(Sx4CommandEvent event) {
			StringBuilder description = new StringBuilder();
			
			Arrays.stream(Filter.values())
				.map(filter -> "`" + filter.name() + "` - " + filter.getDescription() + "\n\n")
				.forEach(description::append);
			
			EmbedBuilder embed = new EmbedBuilder()
				.setDescription(description.toString())
				.setAuthor("Filter List", null, event.getGuild().getIconUrl());
			
			event.reply(embed.build()).queue();
		}
		
	}
	
}