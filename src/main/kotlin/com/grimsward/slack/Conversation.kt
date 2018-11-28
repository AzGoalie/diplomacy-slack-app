package com.grimsward.slack

import io.vertx.core.json.JsonObject

data class Conversation(
    val id: String
) {

    companion object {
        fun fromJsonObject(json: JsonObject): Conversation {
            return Conversation(
                json.getString("id")
            )
        }
    }
}