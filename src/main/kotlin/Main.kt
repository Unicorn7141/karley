import classes.Server

import com.kotlindiscord.kord.extensions.DISCORD_BLURPLE
import com.kotlindiscord.kord.extensions.DISCORD_RED
import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.checks.failed
import com.kotlindiscord.kord.extensions.checks.memberFor
import com.kotlindiscord.kord.extensions.checks.nullMember
import com.kotlindiscord.kord.extensions.checks.passed
import com.kotlindiscord.kord.extensions.checks.types.Check
import com.kotlindiscord.kord.extensions.utils.runSuspended
import com.kotlindiscord.kord.extensions.utils.scheduling.Scheduler
import com.kotlindiscord.kord.extensions.utils.scheduling.Task
import com.mongodb.client.MongoCollection
import commands.*
import dev.kord.common.Color
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.GuildBehavior
import dev.kord.core.behavior.channel.MessageChannelBehavior
import dev.kord.core.behavior.channel.createEmbed
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.entity.Guild
import dev.kord.core.entity.Member
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.event.guild.GuildCreateEvent
import dev.kord.core.event.guild.GuildDeleteEvent
import dev.kord.core.event.guild.MemberJoinEvent
import dev.kord.core.sorted
import dev.kord.gateway.Intents
import dev.kord.gateway.PrivilegedIntent
import dev.kord.rest.Image
import dev.kord.rest.builder.message.create.allowedMentions
import dev.kord.rest.builder.message.create.embed
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.Clock
import mu.KotlinLogging
import org.litote.kmongo.*
import java.time.LocalDateTime

val TOKEN = System.getenv("TOKEN") ?: error("Add \'TOKEN\' to your environmental variables")
val CONNECT_URI = System.getenv("CONNECT_URI") ?: error("Add \'CONNECT_URI\' to you environmental variables")
const val DEFAULT_PREFIX = "k!"
val client = KMongo.createClient(CONNECT_URI)
val db = client.getDatabase("Discord")
val database = db.getCollection<Server>("Karley")
val cache = mutableMapOf<String, Server>()
lateinit var bot: ExtensibleBot
var serverCount = 0
const val VERSION = "0.3.5"
lateinit var scheduler: Task

@OptIn(PrivilegedIntent::class)
suspend fun main() {
	bot = ExtensibleBot(TOKEN) {
		chatCommands {
			enabled = true
			this.defaultPrefix = DEFAULT_PREFIX
			prefix { defaultPrefix ->
				cache[guildId?.asString]?.prefix ?: defaultPrefix
			}
		}
		
		
		applicationCommands {
			this.enabled = true
			defaultGuild(503652829685088276u)
		}
		
		intents {
			+Intents.all
		}
		
		members {
			fillPresences = true
			all()
		}
		
		extensions {
			help {
				enableBundledExtension = true
				colour {
					if (guildId == null) DISCORD_BLURPLE else message.getAuthorAsMember()?.color()
															  ?: error("Couldn't get member from this message")
				}
				pingInReply = false
				
				deleteInvocationOnPaginatorTimeout = false
				deletePaginatorOnTimeout = false
			}
			add(::WelcomeCommands)
			add(::ModerationCommands)
			add(::TODOCommands)
		}
		
		scheduler = Scheduler().schedule(30, name = "readDB") {
			for ((id, _) in cache) {
				println(cache[id]!!.TODOList.keys.toString() + " | " + id)
				
				cache[id] = database.findOne(Server::id eq id) ?: error("Cannot find guild $id")
			}
			updateStatus()
			restart(scheduler)
		}
	}
	// When bot is launched
	bot.on<ReadyEvent> {
		for (guild in guilds) {
			try {
				val ser = getOrCreate(guild)
				cache[ser.id] = ser
			} catch (e: Exception) {
				println(e.stackTraceToString())
			}
		}
		println(cache.keys)
		serverCount = guilds.size
		updateStatus(bot)
	}
	// When bot is added to a new guild
	bot.on<GuildCreateEvent> {
		runSuspended {
			try {
				val ser = getOrCreate(guild)
				database.write(ser)
				serverCount++
				updateStatus(bot)
			} catch (e: Exception) {
				println(e.stackTraceToString())
			}
		}
	}
	// When bot is removed from a guild
	bot.on<GuildDeleteEvent> {
		val ser = cache[guildId.asString] ?: error("Guild not found")
		database.deleteOne(Server::id eq ser.id)
		cache.remove(ser.id)
		serverCount--
		updateStatus(bot)
	}
	// When member joins the guild
	bot.on<MemberJoinEvent>() {
		val ser = cache[guildId.asString] ?: error("Guild not found in database")
		if (ser.welcomeEnabled && ser.welcomeChannelId != null) {
			val message = ser.welcomeMessage
				.replace("[member]", member.mention)
				.replace("[server]", bot.getKoin().get<Kord>().getGuild(guildId)!!.name)
			
			val channelSnowflake = Snowflake(ser.welcomeChannelId!!)
			
			if (ser.welcomeEmbedEnabled) {
				MessageChannelBehavior(channelSnowflake, bot.getKoin().get()).createEmbed {
					title = "Welcome 🥳"
					thumbnail { url = member.avatar!!.url }
					color = member.color()
					description = message
				}
			} else {
				MessageChannelBehavior(channelSnowflake, bot.getKoin().get()).createMessage {
					content = message
				}
			}
		}
	}
	
	bot.start()
}

suspend fun updateStatus(extensibleBot: ExtensibleBot = bot) = extensibleBot
	.getKoin()
	.get<Kord>()
	.editPresence {
		if (LocalDateTime.now().minute % 3 == 0)
			watching("over $serverCount servers")
		else if (LocalDateTime.now().minute % 3 == 1 )
			playing("version $VERSION")
		else
			listening("@Karley help")
	}

fun update(ser: Server) = try {
	database.write(ser)
} catch (e: Exception) {
	println(e.message)
}

/**
 * Get a guild from the database or create if it doesn't exist there
 *
 * [guild] -> The guild you'd like to get/add
 */
suspend fun getOrCreate(guild: GuildBehavior): Server {
	return try {
		database.findOne { Server::id.eq(guild.id.asString) } ?: error("Server not found in the database")
	} catch (e: Exception) {
		println(e.message ?: e.stackTraceToString())
		Server(guild.id.asString, DEFAULT_PREFIX)
	}
}

/**
 * Returns the color of a member (defaults to blurple)
 */
suspend fun Member.color() =
	roles.sorted().toList().reversed().firstOrNull { it.color.rgb != 0 }?.color ?: Color(7506394)

/**
 * Either add or update a server
 *
 * [id]  -> The id of the guild you'd like to update
 *
 * [ser] -> The new server
 */
internal fun <TDocument> MongoCollection<TDocument>.write(ser: Server) {
	val serv = database.findOne { Server::id eq ser.id }
	println("${ser.id} | ${serv == null}")
	if (serv == null) {
		database.insertOne(ser)
	} else {
		database.updateOne(Server::id eq ser.id, ser)
	}
	cache[ser.id] = ser
}

/**
 * Check if the user is the bot owner or not
 *
 * @param [memberId] The member's ID
 * @return returns the check result
 */
suspend fun botOwner(memberId: Long): Check<*> = {
	val logger = KotlinLogging.logger("com.kotlindiscord.kord.extensions.checks.botOwner")
	val member = memberFor(event)
	
	if (member == null) {
		logger.nullMember(event)
		pass()
		
	} else {
		val memberObj = member.asMember()
		val result = Snowflake(memberId) == memberObj.id
		
		
		if (result) {
			logger.failed("Member $member is the bot owner")
			
		} else {
			logger.passed()
			pass()
		}
	}
}

/**
 * Restart the scheduler
 *
 * [scheduler] -> The task you'd like to restart
 */
fun restart(task: Task) {
	task.restart()
}

/**
 * Get the guild's icon
 */
fun Guild.icon(format: Image.Format? = null): String? {
	val hash = this.iconHash
	return when (format) {
		null -> if (hash?.startsWith("a_") == true) {
			this.getIconUrl(Image.Format.GIF)
		} else {
			getIconUrl(Image.Format.PNG)
		}
		else -> getIconUrl(format)
	}
}