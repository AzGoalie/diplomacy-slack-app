package com.grimsward.diplomacy

import com.grimsward.slack.SlackClient
import io.vertx.core.AbstractVerticle
import io.vertx.core.AsyncResult
import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj

class MainVerticle : AbstractVerticle() {
    private lateinit var slackClient: SlackClient
    private val resetMessage = json { obj("text" to "Slack reset for new diplomacy game!") }
    private val userUpdate = json {
        obj(
            "profile" to obj(
                "first_name" to "Anonymous",
                "last_name" to "User",
                "display_name" to "Anonymous"
            )
        )
    }

    override fun start(startFuture: Future<Void>) {
        val slackToken = config().getString("SLACK_TOKEN")
        val hookUrl = config().getString("SLACK_HOOK_URL")

        slackClient = SlackClient(vertx, slackToken)

        val router = Router.router(vertx)
        val handler: (routingContext: RoutingContext) -> Unit = {
            it.response().end()
            resetSlack().setHandler {
                slackClient.sendMessage(hookUrl, resetMessage)
                    .setHandler { result -> printFailures(result, "Sent message") }
            }
        }
        router.get("/slack/reset").handler(handler)
        router.post("/slack/reset").handler(handler)

        vertx.createHttpServer()
            .requestHandler(router::accept)
            .listen(config().getInteger("HTTP.PORT", 8080)) {
                if (it.succeeded()) {
                    startFuture.complete()
                } else {
                    startFuture.fail(it.cause())
                }
            }
    }

    private fun resetSlack(): Future<CompositeFuture> {
        return slackClient.getConversations().compose { conversations ->
            val futures = mutableListOf<Future<*>>()
            conversations.forEach { (id) ->
                futures.add(closeConversation(id))
                futures.add(leaveConversation(id))
                futures.add(deleteConversation(id))
                futures.add(deleteAllMessagesFromConversation(id))
                futures.add(updateAllUserNames())
            }
            CompositeFuture.join(futures)
        }
    }

    private fun closeConversation(id: String) =
        slackClient.closeConversation(id).setHandler { printFailures(it, "Closed conversation $id") }


    private fun leaveConversation(id: String) =
        slackClient.leaveConversation(id).setHandler { printFailures(it, "Left conversation $id") }

    private fun deleteConversation(id: String) =
        slackClient.deleteConversation(id).setHandler { printFailures(it, "Deleted conversation $id") }

    private fun deleteAllMessagesFromConversation(id: String) =
        slackClient.getConversationHistory(id).compose { messages ->
            val futures = messages.map { ts ->
                slackClient.deleteMessage(id, ts)
                    .setHandler { printFailures(it, "Deleted message from $id at $ts") }
            }.toList()
            CompositeFuture.join(futures)
        }

    private fun updateAllUserNames() =
        slackClient.getUsers().compose { users ->
            val futures = users.map { id ->
                slackClient.updateUserProfile(id, userUpdate)
                    .setHandler { printFailures(it, "Failed to update user $id") }
            }.toList()
            CompositeFuture.join(futures)
        }

    private fun <T> printFailures(response: AsyncResult<T>, success: (result: T) -> Unit) {
        if (response.succeeded()) {
            success(response.result())
        } else {
            println(response.cause().message)
        }
    }

    private fun <T> printFailures(response: AsyncResult<T>, successMessage: String) =
        printFailures(response) { println(successMessage) }
}