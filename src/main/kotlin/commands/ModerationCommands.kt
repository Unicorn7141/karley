package commands

import cache
import com.kotlindiscord.kord.extensions.DISCORD_GREEN
import com.kotlindiscord.kord.extensions.DISCORD_RED
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.*
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.chatCommand
import com.kotlindiscord.kord.extensions.extensions.chatCommandCheck
import com.kotlindiscord.kord.extensions.utils.dm
import com.kotlindiscord.kord.extensions.utils.hasPermission
import dev.kord.common.entity.Permission
import dev.kord.common.entity.UserFlag
import dev.kord.core.Kord
import dev.kord.core.behavior.ban
import dev.kord.core.behavior.channel.createEmbed
import dev.kord.core.behavior.reply
import dev.kord.rest.builder.message.create.allowedMentions
import dev.kord.rest.builder.message.create.embed
import icon
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
	
	
	override suspend fun setup() {
		chatCommand(::PrefixArgs) {
			name = "prefix"
			description = "view or set the prefix for the bot in this guild"
			
			action {
				val newPrefix = arguments.newPrefix
				val ser = cache[guild!!.id.asString]!!
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
				val ser = cache[guild!!.id.asString]!!
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
	}
}