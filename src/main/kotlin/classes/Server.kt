package classes

import DEFAULT_PREFIX
import color
import com.kotlindiscord.kord.extensions.time.TimestampType
import com.kotlindiscord.kord.extensions.time.toDiscord
import com.kotlindiscord.kord.extensions.utils.createdAt
import dev.kord.core.entity.Guild
import dev.kord.core.entity.Member
import dev.kord.core.entity.channel.Channel
import dev.kord.rest.builder.message.EmbedBuilder
import kotlinx.datetime.Clock
import java.time.format.DateTimeFormatter


data class Server(val id: String, var prefix: String = DEFAULT_PREFIX) {
	var welcomeChannelId: String? = null
	var welcomeEnabled = false
	var welcomeMessage = "Welcome [member] to your new home, [server]"
	var welcomeEmbedEnabled = false
	val muteLog = mutableMapOf<String, String>()
	var activeChannel: String? = null
	val rules = mutableMapOf<Int, Rule>()
	val rulesMessage: String? = null;
	
	var muteRoleId: String? = null
	
	val TODOList = mutableMapOf<String, List<TODO>>()
	
	fun welcomeMessage(target: Member, guild: Guild) = welcomeMessage
		.replace("[member]", target.mention)
		.replace("[server]", guild.name)
	
	suspend fun welcomeEmbed(target: Member, guild: Guild): MutableList<EmbedBuilder> {
		val embeds = mutableListOf<EmbedBuilder>()
		val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
		embeds.add(
			EmbedBuilder().also {
				it.color = target.color()
				it.title = "Welcome ${target.displayName}"
				it.description = welcomeMessage
					.replace("[member]", target.mention)
					.replace("[server]", guild.name)
				it.field("Created At", true) {
					target.createdAt.toDiscord(TimestampType.fromFormatSpecifier(null)!!)

				}
				it.field("Exists For", true) {
					
					"Approximately ${target.createdAt.toDiscord(TimestampType.fromFormatSpecifier("R")!!)}"
				}
				
				it.thumbnail { url = target.avatar!!.url }
				it.timestamp = Clock.System.now()
			}
		)
		
		return embeds
	}
}

private fun <K, V> Map<K, V>.swap(k: K, k1: K): Map<K, V> {
	val temp = this.toMutableMap()
	temp[k] = temp[k1]!!.also { temp[k1] = temp[k]!! }
	
	return temp.toMap()
}
