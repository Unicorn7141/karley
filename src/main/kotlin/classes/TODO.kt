package classes

import com.kotlindiscord.kord.extensions.time.TimestampType
import com.kotlindiscord.kord.extensions.time.toDiscord
import kotlinx.datetime.Clock

data class TODO(val name: String, var value: String, var isDone: Boolean = false) {
	val createdAt = Clock.System.now().toDiscord(TimestampType.fromFormatSpecifier(null)!!)
	var link: String = ""
}
