package com.dantech.splitvibe

import com.dantech.splitvibe.llm.ClaudeBillAnalyzer
import com.dantech.splitvibe.ocr.OcrLine
import com.dantech.splitvibe.ocr.OcrPoint
import com.dantech.splitvibe.ocr.OcrResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.mockwebserver.MockResponse

/** Recorded real-world data from `src/sharedTest/resources/fixtures`. */
object Fixtures {

    fun text(name: String): String {
        val resource = checkNotNull(javaClass.classLoader?.getResource("fixtures/$name")) { "Missing fixture $name" }
        return resource.readText()
    }

    /** ML Kit output as it was dumped from a device: image size plus each line's text and corners. */
    fun ocr(name: String): OcrResult {
        val root = Json.parseToJsonElement(text(name)).jsonObject
        val lines = root.getValue("lines").jsonArray.map { element ->
            val line = element.jsonObject
            OcrLine(
                text = line.getValue("text").jsonPrimitive.content,
                corners = line.getValue("corners").jsonArray.map { corner ->
                    val (x, y) = corner.jsonArray.map { it.jsonPrimitive.float }
                    OcrPoint(x, y)
                },
            )
        }
        return OcrResult(
            imageWidth = root.getValue("imageWidth").jsonPrimitive.int,
            imageHeight = root.getValue("imageHeight").jsonPrimitive.int,
            lines = lines,
        )
    }

    /** The Messages API response that carries the recorded answer for the sample bill as its text block. */
    fun claudeAnswer(): MockResponse {
        val body = buildJsonObject {
            put("id", "msg_recorded")
            put("type", "message")
            put("role", "assistant")
            put("model", ClaudeBillAnalyzer.DEFAULT_MODEL)
            put(
                "content",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("type", "text")
                            put("text", text("sample_bill_answer.json"))
                        },
                    )
                },
            )
            put("stop_reason", "end_turn")
            putJsonObject("usage") {
                put("input_tokens", 2300)
                put("output_tokens", 800)
            }
        }
        return MockResponse().setHeader("content-type", "application/json").setBody(body.toString())
    }
}
