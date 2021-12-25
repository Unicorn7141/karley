package commands

import BOT_ID
import cache
import com.kotlindiscord.kord.extensions.DISCORD_BLURPLE
import com.kotlindiscord.kord.extensions.DISCORD_FUCHSIA
import com.kotlindiscord.kord.extensions.DISCORD_GREEN
import com.kotlindiscord.kord.extensions.DISCORD_RED
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.*
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.chatCommand
import com.kotlindiscord.kord.extensions.extensions.chatCommandCheck
import com.kotlindiscord.kord.extensions.utils.dm
import com.kotlindiscord.kord.extensions.utils.getTopRole
import com.kotlindiscord.kord.extensions.utils.hasPermission
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.ban
import dev.kord.core.behavior.channel.createEmbed
import dev.kord.core.behavior.createRole
import dev.kord.core.behavior.reply
import dev.kord.core.behavior.swapRolePositions
import dev.kord.core.entity.PermissionOverwrite
import dev.kord.rest.builder.message.create.allowedMentions
import dev.kord.rest.builder.message.create.embed
import icon
import kotlinx.coroutines.NonCancellable.message
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.Clock
import update

class ModerationCommands : Extension() {
	override val name: String
		get() = "Moderation"
	
	/* Arguments */
	class PrefixArgs : Arguments() {
		val newPrefix by optionalString("prefix", "The new prefix for the guild")
	}
	
	class BanArgs : Arguments() {
		val member by member("member", "The member you'd like to ban")
		val days by defaultingInt("days", "Delete messages sent by this user X days back (max 14)", 0)
		val reason by coalescedString("reason", "The reason for the ban")
	}
	
	class MuteRoleArgs : Arguments() {
		val roleId by optionalRole("role", "The role to give people when using mute")
	}
	
	class MuteArgs : Arguments() {
		val member by member("member", "The member you wanna mute")
		val reason by coalescedString("reason", "The reason for the mute")
	}
	
	class MuteLogArgs : Arguments() {
		val member by optionalMember("member", "The member you'd like to check the mute for")
	}
	
	class UnmuteArgs : Arguments() {
		val member by member("member", "The member you'd like to check the mute for")
	}
	
	class RulesAddArgs : Arguments() {
		val name by string("name", "The rule's name (wrap with `\" \"` for multiple words")
		val description by optionalCoalescingString("description", "Elaboration/explanation about/for this rule")
	}
	
	class RuleSelectArgs : Arguments() {
		val id by optionalInt("id", "The rule's id")
		val name by optionalCoalescingString("name", "The rule's name which you'd like to check")
	}
	
	class ActiveChannelArgs : Arguments() {
		val channel by optionalChannel("channel", "Set/Get the channel for the bot to refer")
	}
	
	class TimeoutArgs : Arguments() {
		val member by member("member", "The member you'd like to timeout")
		val minutes by int("duration in minutes", "The timeout duration in minutes")
	}
	
	override suspend fun setup() {
		chatCommand(::PrefixArgs) {
			name = "prefix"
			description = "view or set the prefix for the bot in this guild"
			
			action {
				val newPrefix = arguments.newPrefix
				val ser = cache[guild!!.id.toString()]!!
				val author = message.getAuthorAsMember()!!
				val requiredPerms = buildList<Permission> {
					add(Permission.ManageGuild)
					add(Permission.Administrator)
				}
				message.reply {
					allowedMentions()
					if (newPrefix == null || !requiredPerms.any { author.hasPermission(it) }) {
						content = "My prefix here is `${ser.prefix}` (but you can always ping me :wink:)"
					} else if (requiredPerms.any { author.hasPermission(it) }) {
						embed {
							title = "New Prefix!!!"
							thumbnail { url = guild!!.asGuild().icon() ?: "" }
							color = DISCORD_GREEN
							field("Current Prefix", true) { "`${ser.prefix}`" }
							field("New Prefix", true) { "`${newPrefix.also { ser.prefix = it }}" }
							field("Permanent Prefix", true) { bot.getKoin().get<Kord>().getSelf().mention }
							timestamp = Clock.System.now()
						}
						
						cache[ser.id] = ser.also { update(it) }
					}
				}
			}
		}
		chatCommandCheck {
			val author = event.message.getAuthorAsMember()!!
			val requiredPerms = listOf(Permission.ManageGuild, Permission.BanMembers)
			passIf { requiredPerms.any { author.hasPermission(it) } }
		}
		chatCommand(::BanArgs) {
			name = "ban"
			description = "Be a good moderator and ban someone from the server"
			
			action {
				val ser = cache[guild!!.id.toString()]!!
				val banner = message.getAuthorAsMember()!!
				val banned = arguments.member
				val reason = arguments.reason
				val days = arguments.days
				
				message.delete("I got you bud")
				
				try {
					guild!!.ban(banned.id) {
						this.reason = reason
						this.deleteMessagesDays = days
					}.also {
						banned.dm {
							embed {
								title = "You're Banned"
								description = "I'm sorry to tell you, but you were banned :confused:"
								
								field("Where were you banned from?", true) { guild!!.asGuild().name }
								field("Who banned you?", true) { "Well, I did.\nBut ${banner.mention} told me!" }
								field("Why were you banned?", true) { "Apparently for (and I quote):\n$reason" }
								
								color = DISCORD_RED
								author {
									name = banner.displayName
									icon = banner.memberAvatar?.url ?: banner.avatar?.url ?: banner.defaultAvatar.url
								}
								
								thumbnail { url = guild!!.asGuild().icon() ?: "" }
								timestamp = Clock.System.now()
								footer {
									this.text = "Soryyyyy"
									icon = banned.avatar?.url ?: banned.defaultAvatar.url
								}
							}
						}
						
						message.channel.createEmbed {
							title = "Ban Completed"
							description = "Someone just executed a ban, and it worked"
							
							field("Who's the fucker??", true) { banner.mention }
							field("WHY?!", true) { reason }
						}
					}
				} catch (e: Exception) {
					banner.dm("Ummmm, I cannot ban ${banned.mention}\nGood luck :D")
					println(e.message ?: e.cause)
				}
			}
		}
		chatCommand(::MuteRoleArgs) {
			name = "muterole"
			description = "Add/create a mute role which will be assigned to members when using the mute command"
			
			check {
				val author = event.message.getAuthorAsMember()!!
				val requiredPerms = listOf(Permission.MuteMembers, Permission.ManageGuild)
				passIf(requiredPerms.any { author.hasPermission(it) })
			}
			action {
				val created = arguments.roleId == null
				val ser = cache[guild!!.id.toString()] ?: error("Couldn't find guild ${guild!!.id}")
				val muteRole = arguments.roleId ?: guild!!.getRole(Snowflake(ser.muteRoleId ?: guild!!.createRole {
					name = "muted"
					color = DISCORD_RED
					reason = "You were muted by a mod"
					permissions = Permissions(Permission.ViewChannel, Permission.ReadMessageHistory)
				}.id.toString()))
				guild!!.swapRolePositions {
					val max = guild!!.getMember(bot.getKoin().get<Kord>().selfId).getTopRole()!!
					move(Pair(muteRole.id, guild!!.roles.toList().indexOf(max)))
				}
				ser.muteRoleId = muteRole.id.toString()
				
				message.channel.createEmbed {
					title = if (created) "Muted role created" else "Mute role assigned"
					description =
						if (created) "A new role was created for you as you haven't specified any specific role" else null
					field("Role Name", true) { muteRole.name }
					field("Role ID", true) { muteRole.id.toString() }
					color = muteRole.color
				}
				
				guild!!.channels.toList().forEach { channel ->
					channel.addOverwrite(PermissionOverwrite.forRole(muteRole.id,
																	 denied = Permissions(Permission.All),
																	 allowed = Permissions(Permission.ViewChannel,
																						   Permission.ReadMessageHistory)),
										 "Set as mute role")
				}
				
				cache[ser.id] = ser.also { update(it) }
			}
		}
		chatCommand(::MuteArgs) {
			name = "mute"
			description = "Mute members and prevent them from talking/taking part of anything in your guild"
			
			check {
				val author = event.message.getAuthorAsMember()!!
				val requiredPerms = listOf(Permission.ManageGuild, Permission.MuteMembers)
				passIf(requiredPerms.any { author.hasPermission(it) })
			}
			action {
				val ser = cache[guild!!.id.toString()] ?: error("Cannot find guild ${guild!!.id}")
				if (ser.muteRoleId == null) {
					message.reply {
						content = "Please set a mute role first by using `${ser.prefix}muterule [role]`"
					}
					return@action
				}
				val member = arguments.member
				val reason = arguments.reason
				
				member.addRole(Snowflake(ser.muteRoleId!!), reason = reason)
				ser.muteLog[member.id.toString()] = reason
				
				cache[ser.id] = ser.also { update(it) }
			}
		}
		chatCommand(::MuteLogArgs) {
			name = "mutelog"
			description = "See all the mutes/a mute for a specific member"
			
			action {
				val ser = cache[guild!!.id.toString()] ?: error("Cannot find guild ${guild!!.id}")
				val member = arguments.member
				
				
				paginator(targetChannel = message.channel) {
					if (member == null) {
						if (ser.muteLog.isEmpty()) {
							page {
								title = "Nothing to show"
								description = "There are no muted members here"
								color = DISCORD_GREEN
							}
						} else {
							ser.muteLog.forEach { (mem, reason) ->
								val member_ = guild!!.getMember(Snowflake(mem))
								page {
									title = member_.displayName
									description = "Muted for: $reason"
									color = DISCORD_RED
									thumbnail {
										url = member_.memberAvatar?.url ?: member_.avatar?.url
											  ?: member_.defaultAvatar.url
									}
								}
							}
						}
					} else if (member.id.toString() in ser.muteLog.keys) {
						page {
							title = member.displayName
							description = "Muted for: ${ser.muteLog[member.id.toString()]}"
							color = DISCORD_RED
							thumbnail {
								url = member.memberAvatar?.url ?: member.avatar?.url ?: member.defaultAvatar.url
							}
						}
					} else {
						page {
							title = "Member not found"
							description = "This member is probably not muted :confused:"
							color = DISCORD_FUCHSIA
						}
					}
					
					owner = message.getAuthorAsMember()
					keepEmbed = false
					timeoutSeconds = 120
				}.send()
			}
		}
		chatCommand(::UnmuteArgs) {
			name = "unmute"
			description = "Unmute members and allow them to talk/take part of anything in your guild again"
			
			check {
				val author = event.message.getAuthorAsMember()!!
				val requiredPerms = listOf(Permission.ManageGuild, Permission.MuteMembers)
				passIf(requiredPerms.any { author.hasPermission(it) })
			}
			action {
				val ser = cache[guild!!.id.toString()] ?: error("Cannot find guild ${guild!!.id}")
				val member = arguments.member
				member.removeRole(Snowflake(ser.muteRoleId!!), reason = "Forgiven")
				ser.muteLog.remove(member.id.toString())
				
				cache[ser.id] = ser.also { update(it) }
			}
		}
		chatCommand(::TimeoutArgs) {
			name = "timeout"
			description = "Put a member on a timeout"
			
			check {
				val author = event.message.getAuthorAsMember()!!
				val requiredPerms = listOf(Permission.ManageGuild, Permission.ManageRoles)
				passIf(requiredPerms.any { author.hasPermission(it) })
			}
			action {
				val target = arguments.member
				val duration = arguments.minutes
				
			}
		}
		chatCommand(::ActiveChannelArgs) {
			name = "channel"
			description =
				"Set/Get the active channel for the bot to use " + "(use <@$BOT_ID>whatis active channel for explanation on the term)"
			
			check {
				val author = event.message.getAuthorAsMember()!!
				val requiredPerms = listOf(Permission.ManageGuild, Permission.ManageChannels)
				passIf(requiredPerms.any { author.hasPermission(it) })
			}
			action {
				val channel = arguments.channel
				val ser = cache[guild!!.id.toString()] ?: error("Cannot find guild ${guild!!.id}")
				
				message.channel.createEmbed {
					title = "Active Channel"
					description =
						if (channel == null) "Showing the current active channel" else "Setting the wanted channel as the active channel"
					field("Current Active Channel", true) {
						if (ser.activeChannel == null) "None" else guild!!.getChannel(Snowflake(ser.activeChannel!!)).mention
					}
					if (channel != null && channel.id.toString() != ser.activeChannel) {
						field("New Active Channel", true) { channel.mention }
						ser.activeChannel = channel.id.toString()
					}
					
					color = DISCORD_BLURPLE
				}
				
				cache[ser.id] = ser.also { update(it) }
			}
		}
	}
}