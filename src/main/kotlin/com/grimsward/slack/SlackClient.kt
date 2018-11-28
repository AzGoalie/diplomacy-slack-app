package com.grimsward.slack

import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.HttpResponse
import io.vertx.ext.web.client.WebClient
import io.vertx.kotlin.ext.web.client.WebClientOptions

class SlackClient(vertx: Vertx, private val token: String) {
    private val client =
        WebClient.create(vertx, WebClientOptions(ssl = true, defaultHost = "slack.com", defaultPort = 443))
    private val hookClient =
        WebClient.create(vertx, WebClientOptions(ssl = true, defaultHost = "hooks.slack.com", defaultPort = 443))

    fun getConversations(): Future<List<Conversation>> = getConversation(null)

    fun leaveConversation(id: String): Future<Void> {
        val future = Future.future<Void>()
        client.post("/api/conversations.leave")
            .addQueryParam("token", token)
            .addQueryParam("channel", id)
            .send { handleResponse(it, future, "Failed to leave conversation $id") }
        return future
    }

    fun deleteConversation(id: String): Future<Void> {
        val future = Future.future<Void>()
        client.post("/api/conversations.delete")
            .addQueryParam("token", token)
            .addQueryParam("channel", id)
            .send { handleResponse(it, future, "Failed to delete conversation $id") }
        return future
    }

    fun closeConversation(id: String): Future<Void> {
        val future = Future.future<Void>()
        client.post("/api/conversations.close")
            .addQueryParam("token", token)
            .addQueryParam("channel", id)
            .send { handleResponse(it, future, "Failed to close conversation $id") }
        return future
    }

    fun getConversationHistory(id: String) = getConversationHistory(id, null)

    fun deleteMessage(id: String, ts: String): Future<Void> {
        val future = Future.future<Void>()
        client.post("/api/chat.delete")
            .addQueryParam("token", token)
            .addQueryParam("channel", id)
            .addQueryParam("ts", ts)
            .send { handleResponse(it, future, "Failed to delete message in $id at $ts") }
        return future
    }

    fun getUsers(): Future<List<String>> = getUsers(null)

    fun updateUserProfile(id: String, profile: JsonObject): Future<Void> {
        val future = Future.future<Void>()
        client.post("/api/users.profile.set")
            .putHeader("Content-Type", "application/json")
            .putHeader("Authorization", "Bearer $token")
            .putHeader("X-Slack-User", id)
            .sendJsonObject(profile) { handleResponse(it, future, "Failed to update user") }
        return future
    }

    fun sendMessage(url: String, body: JsonObject): Future<Void> {
        val future = Future.future<Void>()
        hookClient.post(url)
            .putHeader("Content-Type", "application/json")
            .sendJsonObject(body) {
                if (it.succeeded()) {
                    future.complete()
                } else {
                    future.fail(it.cause())
                }
            }
        return future
    }

    private fun getConversation(cursor: String?): Future<List<Conversation>> {
        val future = Future.future<List<Conversation>>()
        val request = client.get("/api/conversations.list")
            .addQueryParam("token", token)
            .addQueryParam("types", "public_channel,private_channel,im,mpim")
        if (cursor != null) {
            request.addQueryParam("cursor", cursor)
        }

        request.send { response ->
            handleResponse(response, future, "Failed to get list of conversations") { body ->
                val conversations = body.getJsonArray("channels")
                    .map { c -> c as JsonObject }
                    .map { json -> Conversation.fromJsonObject(json) }
                    .toMutableList()
                val responseMetadata = body.getJsonObject("response_metadata")
                if (responseMetadata != null && responseMetadata.getString("next_cursor", "") != "") {
                    getConversation(responseMetadata.getString("next_cursor"))
                        .compose {
                            conversations.addAll(it)
                            future.complete()
                            future
                        }
                }
                future.complete(conversations)
            }
        }

        return future
    }

    private fun getConversationHistory(id: String, cursor: String?): Future<List<String>> {
        val future = Future.future<List<String>>()
        val request = client.post("/api/conversations.history")
            .addQueryParam("token", token)
            .addQueryParam("channel", id)
        if (cursor != null) {
            request.addQueryParam("cursor", cursor)
        }
        request.send { response ->
            handleResponse(response, future, "Failed to get history fro $id") { body ->
                val messageTimestamps = body.getJsonArray("messages")
                    .filter { it != null }
                    .map { message -> message as JsonObject }
                    .map { message -> message.getString("ts") }
                    .toMutableList()
                val responseMetadata = body.getJsonObject("response_metadata")
                if (responseMetadata != null && responseMetadata.getString("next_cursor", "") != "") {
                    getConversationHistory(id, responseMetadata.getString("next_cursor"))
                        .compose {
                            messageTimestamps.addAll(it)
                            future.complete()
                            future
                        }
                }
                future.complete(messageTimestamps)
            }
        }
        return future
    }

    private fun getUsers(cursor: String?): Future<List<String>> {
        val future = Future.future<List<String>>()
        val request = client.post("/api/users.list")
            .addQueryParam("token", token)
        if (cursor != null) {
            request.addQueryParam("cursor", cursor)
        }
        request.send { response ->
            handleResponse(response, future, "Failed to get list of users") { body ->
                val users = body.getJsonArray("members")
                    .map { user -> user as JsonObject }
                    .map { user -> user.getString("id") }
                    .toMutableList()
                val responseMetadata = body.getJsonObject("response_metadata")
                if (responseMetadata != null && responseMetadata.getString("next_cursor", "") != "") {
                    getUsers(responseMetadata.getString("next_cursor"))
                        .compose {
                            users.addAll(it)
                            future.complete()
                            future
                        }
                }
                future.complete(users)
            }
        }
        return future
    }

    private fun <T> handleResponse(
        response: AsyncResult<HttpResponse<Buffer>>,
        future: Future<T>,
        errorMessage: String,
        success: (body: JsonObject) -> Unit = { future.complete() }
    ) {
        if (response.succeeded()) {
            val body = response.result().bodyAsJsonObject()
            if (body.getBoolean("ok")) {
                success(body)
            } else {
                future.fail("Error ${body.getString("error")}: $errorMessage")
            }
        } else {
            future.fail(response.cause())
        }
    }
}