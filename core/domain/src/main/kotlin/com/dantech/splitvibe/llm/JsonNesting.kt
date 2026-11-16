package com.dantech.splitvibe.llm

/**
 * How deep JSON text nests its arrays and objects, looked at before the text is read. A JSON reader
 * goes into each array and object on the stack, so text nested far deeper than anything the proxy
 * sends, which only a broken proxy could send, would run it out of stack instead of failing to read.
 */
object JsonNesting {

    /** Far deeper than anything the proxy sends nests, and far from what runs a reader out of stack. */
    const val MAX_DEPTH = 64

    /** Whether [text] opens at most [max] arrays and objects inside one another, what its strings hold aside. */
    fun isWithin(text: String, max: Int = MAX_DEPTH): Boolean {
        var depth = 0
        var inString = false
        var escaped = false
        for (char in text) {
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
            } else {
                when (char) {
                    '"' -> inString = true
                    '[', '{' -> if (++depth > max) return false
                    ']', '}' -> depth--
                }
            }
        }
        return true
    }
}
