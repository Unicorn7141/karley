package commands

import cache
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.channel
import com.kotlindiscord.kord.extensions.commands.converters.impl.defaultingBoolean
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalChannel
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalCoalescingString
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.chatGroupCommand
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.MessageChannelBehavior
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.reply
import dev.kord.rest.builder.message.create.allowedMentions
import update

class WelcomeCommands : Extension() {
	override val name: String
		get() = "Welcome"
	
	/* Arguments */
	class WelcomeMessageArgs : Arguments() {
		val message by optionalCoalescingString("message", "The message to display when a new member joins.")
	}
	
	class WelcomeSetArgs : Arguments() {
		val channel by optionalChannel("channel", "The channel to send the welcome message to")
		val embedEnabled by defaultingBoolean("embed-enabled",
											  "Set to true if you wanna send an embed or false for message",
											  false)
		val autoEnable by defaultingBoolean("auto-enable",
											"Whether to auto-enable the welcome messages after assigning the channel",
											true)
		val message by optionalCoalescingString("message", "The message to display when a new member joins.")
	}
	
	override suspend fun setup() {
		chatGroupCommand {
			name = "welcome"
			description = "Create a lovely heartwarming welcome message to your new joining members"
			
			check {
				val author = this.event.message.getAuthorAsMember()!!
				val requiredPerms = listOf(Permission.ManageChannels, Permission.ManageGuild)
				failIfNot { requiredPerms.any { author.getPermissions().contains(it) } }
			}
			// set
			chatCommand(::WelcomeSetArgs) {
				name = "set"
				description =
					"Set the channel, state (enabled/disabled) and message, which will be used for when new members join\n" +
					"Special Message Syntax:\n" +
					"`[member]` -> will result in pinging the member\n" +
					"`[server]` -> the server's name"
				
				action {
					val ser = cache[guild!!.id.toString()]!!
					val welcomeChannel = arguments.channel ?: message.channel.asChannel()
					val welcomeEnabled = arguments.autoEnable
					val welcomeMessage = arguments.message ?: ser.welcomeMessage
					val embedEnabled = if (!ser.welcomeEmbedEnabled) arguments.embedEnabled else ser.welcomeEmbedEnabled
					
					ser.welcomeEnabled = welcomeEnabled
					ser.welcomeMessage = welcomeMessage
					ser.activeChannel = welcomeChannel.id.toString()
					ser.welcomeChannelId = ser.activeChannel
					ser.welcomeEmbedEnabled = embedEnabled
					
					message.reply {
						allowedMentions()
						with(ser) {
							val channel = guild!!.getChannel(Snowflake(welcomeChannelId!!))
							val demoMessage = ser.welcomeMessage
								.replace("[member]", bot.getKoin().get<Kord>().getSelf().mention)
								.replace("[server]", guild!!.asGuild().name)
							
							content = "Welcome Channel: ${channel.mention}\n" +
									  "Welcome Message Demo: ${if (!welcomeEmbedEnabled) demoMessage else "<embed>"}\n" +
									  "Welcome Enabled: $welcomeEnabled" +
									  "Welcome Embed Enabled: $embedEnabled"
							
							if (ser.welcomeEmbedEnabled) {
								embeds.toMutableList().add(welcomeEmbed(message.getAuthorAsMember()!!,
																		getGuild()!!.asGuild())
															   .first()
								)
							}
						}
					}
					
					cache[ser.id] = ser.also { update(ser) }
				}
			}
			// test
			chatCommand {
				name = "test"
				description = "test the welcome command"
				
				action {
					val ser = cache[guild!!.id.toString()]!!
					if (ser.welcomeChannelId != null && ser.welcomeEnabled) {
						val channel = MessageChannelBehavior(Snowflake(ser.welcomeChannelId!!), bot.getKoin().get())
						channel.createMessage {
							if (ser.welcomeEmbedEnabled) {
								embeds.add(ser.welcomeEmbed(message.getAuthorAsMember()!!, guild!!.asGuild()).first())
									.also { println(embeds.size) }
							} else {
								content = ser.welcomeMessage(message.getAuthorAsMember()!!, guild!!.asGuild())
							}
						}
					} else if (ser.welcomeChannelId == null) {
						message.reply {
							allowedMentions()
							content =
								"Please set a channel first by using either `${ser.prefix}channel` or `${ser.prefix}welcome set`"
						}
					} else {
						message.reply {
							allowedMentions()
							content = "Please enable the welcome first by using `${ser.prefix}welcome enable`"
						}
					}
				}
			}
			// reset
			chatCommand {
				name = "reset"
				description = "reset everything to default"
				
				action {
					val ser = cache[guild!!.id.toString()]!!
					ser.welcomeMessage = "Welcome [member] to your new home, [server]"
					ser.welcomeChannelId = null
					ser.welcomeEmbedEnabled = false
					ser.welcomeEnabled = false
					
					message.reply {
						allowedMentions()
						content = "Everything is back to default."
					}
					
					cache[ser.id] = ser.also { update(it) }
				}
			}
			// enable
			chatCommand {
				name = "enable"
				description = "Enable the welcome messages"
				
				action {
					val ser = cache[guild!!.id.toString()]!!
					ser.welcomeEnabled = true
					
					cache[ser.id] = ser.also { update(it) }
				}
			}
			// disable
			chatCommand {
				name = "disable"
				description = "Disable the welcome messages"
				
				action {
					val ser = cache[guild!!.id.toString()]!!
					ser.welcomeEnabled = false
					
					cache[ser.id] = ser.also { update(it) }
				}
			}
			// enable/disable embed
			chatCommand {
				name = "embed"
				description = "Enable/Disable the welcome embed"
				
				action {
					val ser = cache[guild!!.id.toString()]!!
					ser.welcomeEmbedEnabled = !ser.welcomeEmbedEnabled
					
					message.reply {
						allowedMentions()
						content = if (ser.welcomeEmbedEnabled) {
							"Welcome Embeds Enabled"
						} else {
							"Welcome Embeds Disabled"
						}
						cache[ser.id] = ser.also { update(it) }
					}
				}
			}
			// message
			chatCommand(::WelcomeMessageArgs) {
				name = "message"
				description =
					"Set your custom message to greet your newcomers.\nSpecial Syntax:\n`[member]` -> mentions the member\n`[server]` -> the server's name"
				
				action {
					val ser = cache[guild!!.id.toString()]!!
					val msg = arguments.message
					message.reply {
						allowedMentions()
						content = if (msg == null) {
							"Current message: ${ser.welcomeMessage}"
						} else {
							"Setting the message to: \"$msg\"".also { ser.welcomeMessage = msg }
						}
					}
					cache[ser.id] = ser.also { update(it) }
				}
			}
		}
	}
}