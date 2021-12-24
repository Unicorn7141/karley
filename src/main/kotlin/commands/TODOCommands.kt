package commands

import cache
import classes.TODO
import color
import com.kotlindiscord.kord.extensions.DISCORD_GREEN
import com.kotlindiscord.kord.extensions.DISCORD_WHITE
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.coalescedString
import com.kotlindiscord.kord.extensions.commands.converters.impl.defaultingCoalescingString
import com.kotlindiscord.kord.extensions.commands.converters.impl.int
import com.kotlindiscord.kord.extensions.commands.converters.impl.string
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.chatGroupCommand
import com.kotlindiscord.kord.extensions.pagination.pages.Page
import com.kotlindiscord.kord.extensions.time.TimestampType
import com.kotlindiscord.kord.extensions.time.toDiscord
import dev.kord.core.behavior.reply
import dev.kord.rest.builder.message.create.allowedMentions
import dev.kord.rest.builder.message.create.embed
import kotlinx.datetime.Clock
import update

class TODOCommands : Extension() {
	override val name: String
		get() = "TODO"
	
	/* Arguments */
	class TODOAddArgs : Arguments() {
		val name by string("name", "The name/title of your TODO (wrap with `\" \"` for multiple words)")
		val description by defaultingCoalescingString("description", "Describe your TODO", "Not specified")
	}
	
	class TODOIndexArgs : Arguments() {
		val id by int("ID", "The task's ID which you'd like to remove")
	}
	
	class TODOEditArgs : Arguments() {
		val id by int("ID", "The task's ID which you'd like to edit")
		val value by coalescedString("content", "The new content of the task")
	}
	
	override suspend fun setup() {
		chatGroupCommand {
			name = "todo"
			aliases = arrayOf("td", "tasks")
			description = "A simple (yet effective) TODO list"
			
			action {
				val ser = cache[guild!!.id.toString()]!!.also { println(it.TODOList.size) }
				val member = message.getAuthorAsMember()!!
				val TODOList = (ser.TODOList[member.id.toString()] ?: emptyList())
				
				paginator(targetChannel = message.channel) {
					var page: Page
					if (TODOList.isEmpty()) {
						page = Page {
							title = "You have nothing to do"
							description = "Your list is empty \uD83D\uDE31\nYou may wanna consider using the" +
										  " `${ser.prefix}todo add` command to start adding tasks to your list"
							color = DISCORD_WHITE
							thumbnail {
								url = member.memberAvatar?.url ?: member.avatar?.url ?: member.defaultAvatar.url
							}
						}
						
						pages.addPage(page)
					} else {
						val total = TODOList.size
						val pagesCount = (total / 5) + 1
						for (i in 0 until pagesCount) {
							val start = 5 * i
							val end = if (5 * (i + 1) <= total) 5 * (i + 1) else total
							val tasks = TODOList.subList(start, end).also(::println)
							if (tasks.isNotEmpty()) {
								page = Page {
									title = "${member.displayName}'s TODO List"
									description = "Showing items ${start + 1} - $end out of $total"
									color = member.color()
									
									for (t in tasks) {
										val index = (tasks.indexOf(t) + 1) + start
										field("${index}. ${t.name}", true) { t.value }
										field("Created At", true) { t.createdAt }
										field("Finished", true) { if (t.isDone) "Yes" else "No" }
										thumbnail {
											url = member.memberAvatar?.url ?: member.avatar?.url
												  ?: member.defaultAvatar.url
										}
										
									}
								}
								keepEmbed = false
								owner = message.getAuthorAsMember()
								timeoutSeconds = 60
								pages.addPage(page)
							}
						}
					}
					keepEmbed = true
					owner = member
					timeoutSeconds = 60
				}.send()
			}
			
			
			// add
			chatCommand(::TODOAddArgs) {
				name = "add"
				aliasKey = "+"
				description = "Add a new item to your TODO List"
				
				action {
					val ser = cache[guild!!.id.toString()]!!
					val member = message.getAuthorAsMember()!!
					val title = arguments.name
					val content = arguments.description
					
					if (!ser.TODOList.keys.contains(member.id.toString())) {
						ser.TODOList[member.id.toString()] = emptyList()
					}
					val TODOList = ser.TODOList[member.id.toString()]!!.toMutableList()
					TODOList.add(TODO(title, content))
					
					ser.TODOList[member.id.toString()] = TODOList.also(::println)
					cache[ser.id] = ser.also {
						println(it.TODOList[member.id.toString()])
						update(it)
					}
					
					message.reply {
						allowedMentions()
						this.content = "A new TODO Task was added to your list"
						embed {
							this.title = title
							this.description = content
							this.color = DISCORD_GREEN
							this.thumbnail {
								url = member.memberAvatar?.url ?: member.avatar?.url ?: member.defaultAvatar.url
							}
							this.timestamp = Clock.System.now()
						}
					}
				}
			}
			// list
			chatCommand {
				name = "list"
				aliases = arrayOf("ls", "get")
				description = "Get your TODO list"
				
				action {
					val ser = cache[guild!!.id.toString()]!!.also { println(it.TODOList.size) }
					val member = message.getAuthorAsMember()!!
					val TODOList = (ser.TODOList[member.id.toString()] ?: emptyList())
					
					paginator(targetChannel = message.channel) {
						var page: Page
						if (TODOList.isEmpty()) {
							page = Page {
								title = "You have nothing to do"
								description = "Your list is empty \uD83D\uDE31\nYou may wanna consider using the" +
											  " `${ser.prefix}todo add` command to start adding tasks to your list"
								color = DISCORD_WHITE
								thumbnail {
									url = member.memberAvatar?.url ?: member.avatar?.url ?: member.defaultAvatar.url
								}
							}
							
							pages.addPage(page)
						} else {
							val total = TODOList.size
							val pagesCount = (total / 5) + 1
							for (i in 0 until pagesCount) {
								val start = 5 * i
								val end = if (5 * (i + 1) <= total) 5 * (i + 1) else total
								val tasks = TODOList.subList(start, end).also(::println)
								if (tasks.isNotEmpty()) {
									page = Page {
										title = "${member.displayName}'s TODO List"
										description = "Showing items ${start + 1} - $end out of $total"
										color = member.color()
										
										for (t in tasks) {
											val index = (tasks.indexOf(t) + 1) + start
											field("${index}. ${t.name}", true) { t.value }
											field("Created At", true) { t.createdAt }
											field("Finished", true) { if (t.isDone) "Yes" else "No" }
											thumbnail {
												url = member.memberAvatar?.url ?: member.avatar?.url
													  ?: member.defaultAvatar.url
											}
											
										}
									}
									keepEmbed = false
									owner = message.getAuthorAsMember()
									timeoutSeconds = 60
									pages.addPage(page)
								}
							}
						}
						keepEmbed = true
						owner = member
						timeoutSeconds = 60
					}.send()
				}
			}
			// remove
			chatCommand(::TODOIndexArgs) {
				name = "remove"
				aliasKey = "-"
				description = "Remove a task from the list"
				
				action {
					val ser = cache[guild!!.id.toString()]!!
					val member = message.getAuthorAsMember()!!.id.toString()
					val tasks = (ser.TODOList[member] ?: emptyList())
					val index = arguments.id
					message.reply {
						allowedMentions()
						content = if (index - 1 in tasks.indices) {
							val task = tasks[index - 1]
							ser.TODOList[member] = tasks.filter { tasks.indexOf(it) != index - 1 }.also(::println)
							cache[ser.id] = ser.also {
								println(it.TODOList[member]?.size ?: 0)
								update(it)
							}
							"Removed task #${index} (${task.name})"
						} else {
							"There's no task at index $index"
						}
					}
				}
			}
			// edit
			chatCommand(::TODOEditArgs) {
				name = "edit"
				aliasKey = "e"
				description = "Edit an existing task"
				
				action {
					val ser = cache[guild!!.id.toString()]!!
					val member = message.getAuthorAsMember()!!.id.toString()
					val tasks = (ser.TODOList[member] ?: emptyList())
					val id = arguments.id
					val value = arguments.value
					
					message.reply {
						allowedMentions()
						content = if (tasks.isEmpty()) {
							"You don't seem to have any tasks....\nPlease consider using `${ser.prefix}todo add` to add some tasks"
						} else if (id - 1 !in tasks.indices) {
							"There's no task with the id of $id"
						} else {
							tasks[id - 1].value = value
							ser.TODOList[member] = tasks
							cache[ser.id] = ser.also { update(it) }
							"Edited task $id (${tasks[id - 1].name})'s content to $value"
						}
					}
				}
			}
			// finish
			chatCommand(::TODOIndexArgs) {
				name = "finish"
				aliases = arrayOf("v", "~")
				description = "Mark a task as done"
				
				action {
					val ser = cache[guild!!.id.toString()]!!
					val member = message.getAuthorAsMember()!!.id.toString()
					val tasks = (ser.TODOList[member] ?: emptyList())
					val id = arguments.id
					
					message.reply {
						allowedMentions()
						content = if (tasks.isEmpty()) {
							"You don't seem to have any tasks....\nPlease consider using `${ser.prefix}todo add` to add some tasks"
						} else if (id - 1 !in tasks.indices) {
							"There's no task with the id of $id"
						} else {
							tasks[id - 1].isDone = true
							ser.TODOList[member] = tasks
							cache[ser.id] = ser.also { update(it) }
							"Congrats! Task #$id (${tasks[id - 1].name}) is now completed!!"
						}
					}
				}
			}
			// finish and delete
			chatCommand(::TODOIndexArgs) {
				name = "mark and delete"
				aliases = arrayOf("mad", "~!")
				description = "Mark a task as done and delete it"
				
				action {
					val ser = cache[guild!!.id.toString()]!!
					val member = message.getAuthorAsMember()!!.id.toString()
					val tasks = (ser.TODOList[member] ?: emptyList())
					val id = arguments.id
					
					message.reply {
						allowedMentions()
						content = if (tasks.isEmpty()) {
							"You don't seem to have any tasks....\nPlease consider using `${ser.prefix}todo add` to add some tasks"
						} else if (id - 1 !in tasks.indices) {
							"There's no task with the id of $id"
						} else {
							tasks[id - 1].isDone = true
							ser.TODOList[member] = tasks.filter { tasks.indexOf(it) != (id - 1) }
							cache[ser.id] = ser.also { update(it) }
							"Congrats! Task #$id (${tasks[id - 1].name}) is now completed!!\n||And deleted from the list||"
						}
					}
				}
			}
		}
	}
}
