package com.tag.routine.data

import java.io.Reader
import java.io.Writer

enum class JsonValueType {
    OBJECT,
    ARRAY,
    STRING,
    NUMBER,
    BOOLEAN,
    NULL,
}

/**
 * Bounded, streaming RFC 8259 JSON reader operating purely on [java.io.Reader].
 *
 * Reads character-by-character through a small fixed buffer without materializing a DOM tree
 * or relying on Android framework stubs (`org.json`, `android.util.JsonReader`), so it runs
 * identically in Android and pure-JVM unit tests and fails safely on huge or corrupt inputs.
 */
class JsonStreamReader(
    private val reader: Reader,
    private val maxChars: Long = BackupCodec.MAX_BACKUP_CHARS,
    private val maxDepth: Int = 32,
    private val maxStringLength: Int = 256_000,
    private val maxCollectionItems: Int = 200_000,
) {
    private val buf = CharArray(8192)
    private var pos = 0
    private var len = 0
    private var charsRead = 0L
    private var depth = 0
    private var startChecked = false

    private fun ensureBuffer(): Boolean {
        if (pos < len) return true
        val n = reader.read(buf)
        if (n <= 0) return false
        charsRead += n
        if (charsRead > maxChars) {
            throw BackupValidationException(
                BackupErrorCode.FILE_TOO_LARGE,
                "Backup file exceeds maximum supported size.",
            )
        }
        pos = 0
        len = n
        return true
    }

    private fun peekRaw(): Int {
        if (!ensureBuffer()) return -1
        return buf[pos].code
    }

    private fun readRaw(): Int {
        if (!ensureBuffer()) return -1
        return buf[pos++].code
    }

    private fun skipWhitespace() {
        if (!startChecked) {
            startChecked = true
            if (peekRaw() == 0xFEFF) {
                readRaw()
            }
        }
        while (true) {
            when (peekRaw()) {
                ' '.code, '\n'.code, '\r'.code, '\t'.code -> readRaw()
                else -> return
            }
        }
    }

    fun isEmptyInput(): Boolean {
        skipWhitespace()
        return peekRaw() == -1
    }

    fun peekType(): JsonValueType {
        skipWhitespace()
        return when (peekRaw()) {
            -1 -> throw BackupValidationException(BackupErrorCode.INVALID_JSON, "Unexpected end of JSON")
            '{'.code -> JsonValueType.OBJECT
            '['.code -> JsonValueType.ARRAY
            '"'.code -> JsonValueType.STRING
            't'.code, 'f'.code -> JsonValueType.BOOLEAN
            'n'.code -> JsonValueType.NULL
            '-'.code, in '0'.code..'9'.code -> JsonValueType.NUMBER
            else -> throw BackupValidationException(BackupErrorCode.INVALID_JSON, "Unexpected JSON token")
        }
    }

    fun readObject(onField: (String) -> Unit) {
        if (peekType() != JsonValueType.OBJECT) {
            throw BackupValidationException(BackupErrorCode.INVALID_FIELD_TYPE, "Expected JSON object")
        }
        readRaw() // '{'
        depth++
        if (depth > maxDepth) {
            throw BackupValidationException(BackupErrorCode.INVALID_FORMAT, "JSON nesting depth exceeded")
        }
        skipWhitespace()
        if (peekRaw() == '}'.code) {
            readRaw()
            depth--
            return
        }
        while (true) {
            skipWhitespace()
            if (peekRaw() != '"'.code) {
                throw BackupValidationException(BackupErrorCode.INVALID_JSON, "Expected object key")
            }
            val key = readString()
            skipWhitespace()
            if (readRaw() != ':'.code) {
                throw BackupValidationException(BackupErrorCode.INVALID_JSON, "Expected ':' after object key")
            }
            onField(key)
            skipWhitespace()
            when (readRaw()) {
                ','.code -> continue
                '}'.code -> {
                    depth--
                    return
                }
                else -> throw BackupValidationException(BackupErrorCode.INVALID_JSON, "Expected ',' or '}' in object")
            }
        }
    }

    fun readArray(onElement: (Int) -> Unit) {
        if (peekType() != JsonValueType.ARRAY) {
            throw BackupValidationException(BackupErrorCode.INVALID_FIELD_TYPE, "Expected JSON array")
        }
        readRaw() // '['
        depth++
        if (depth > maxDepth) {
            throw BackupValidationException(BackupErrorCode.INVALID_FORMAT, "JSON nesting depth exceeded")
        }
        skipWhitespace()
        if (peekRaw() == ']'.code) {
            readRaw()
            depth--
            return
        }
        var index = 0
        while (true) {
            if (index >= maxCollectionItems) {
                throw BackupValidationException(
                    BackupErrorCode.FILE_TOO_LARGE,
                    "Backup array exceeds maximum item count",
                )
            }
            onElement(index++)
            skipWhitespace()
            when (readRaw()) {
                ','.code -> continue
                ']'.code -> {
                    depth--
                    return
                }
                else -> throw BackupValidationException(BackupErrorCode.INVALID_JSON, "Expected ',' or ']' in array")
            }
        }
    }

    fun readString(): String {
        if (peekType() != JsonValueType.STRING) {
            throw BackupValidationException(BackupErrorCode.INVALID_FIELD_TYPE, "Expected string")
        }
        readRaw() // '"'
        val sb = StringBuilder()
        while (true) {
            val c = readRaw()
            if (c < 0) {
                throw BackupValidationException(BackupErrorCode.INVALID_JSON, "Unterminated string")
            }
            if (c == '"'.code) {
                return sb.toString()
            }
            if (c < 0x20) {
                throw BackupValidationException(BackupErrorCode.INVALID_JSON, "Unescaped control character")
            }
            if (c == '\\'.code) {
                when (val esc = readRaw()) {
                    '"'.code -> sb.append('"')
                    '\\'.code -> sb.append('\\')
                    '/'.code -> sb.append('/')
                    'b'.code -> sb.append('\b')
                    'f'.code -> sb.append('\u000C')
                    'n'.code -> sb.append('\n')
                    'r'.code -> sb.append('\r')
                    't'.code -> sb.append('\t')
                    'u'.code -> {
                        var code = 0
                        repeat(4) {
                            val h = readRaw()
                            val digit = when (h) {
                                in '0'.code..'9'.code -> h - '0'.code
                                in 'a'.code..'f'.code -> h - 'a'.code + 10
                                in 'A'.code..'F'.code -> h - 'A'.code + 10
                                else -> throw BackupValidationException(
                                    BackupErrorCode.INVALID_JSON,
                                    "Invalid unicode escape",
                                )
                            }
                            code = (code shl 4) or digit
                        }
                        sb.append(code.toChar())
                    }
                    else -> throw BackupValidationException(
                        BackupErrorCode.INVALID_JSON,
                        "Invalid escape sequence: \\${esc.toChar()}",
                    )
                }
            } else {
                sb.append(c.toChar())
            }
            if (sb.length > maxStringLength) {
                throw BackupValidationException(BackupErrorCode.FILE_TOO_LARGE, "String value exceeds limit")
            }
        }
    }

    fun readStringOrNull(): String? = when (peekType()) {
        JsonValueType.NULL -> {
            readNull()
            null
        }
        JsonValueType.STRING -> readString()
        else -> throw BackupValidationException(BackupErrorCode.INVALID_FIELD_TYPE, "Expected string or null")
    }

    fun readInt(): Int {
        if (peekType() != JsonValueType.NUMBER) {
            throw BackupValidationException(BackupErrorCode.INVALID_FIELD_TYPE, "Expected integer")
        }
        val token = readNumberToken()
        return token.toIntOrNull()
            ?: throw BackupValidationException(BackupErrorCode.INVALID_FIELD_TYPE, "Expected 32-bit integer")
    }

    fun readIntOrNull(): Int? = when (peekType()) {
        JsonValueType.NULL -> {
            readNull()
            null
        }
        JsonValueType.NUMBER -> readInt()
        else -> throw BackupValidationException(BackupErrorCode.INVALID_FIELD_TYPE, "Expected integer or null")
    }

    fun readLong(): Long {
        if (peekType() != JsonValueType.NUMBER) {
            throw BackupValidationException(BackupErrorCode.INVALID_FIELD_TYPE, "Expected integer")
        }
        val token = readNumberToken()
        return token.toLongOrNull()
            ?: throw BackupValidationException(BackupErrorCode.INVALID_FIELD_TYPE, "Expected 64-bit integer")
    }

    fun readLongOrNull(): Long? = when (peekType()) {
        JsonValueType.NULL -> {
            readNull()
            null
        }
        JsonValueType.NUMBER -> readLong()
        else -> throw BackupValidationException(BackupErrorCode.INVALID_FIELD_TYPE, "Expected integer or null")
    }

    fun readBoolean(): Boolean {
        if (peekType() != JsonValueType.BOOLEAN) {
            throw BackupValidationException(BackupErrorCode.INVALID_FIELD_TYPE, "Expected boolean")
        }
        return if (peekRaw() == 't'.code) {
            consumeLiteral("true")
            true
        } else {
            consumeLiteral("false")
            false
        }
    }

    fun readBooleanOrNull(): Boolean? = when (peekType()) {
        JsonValueType.NULL -> {
            readNull()
            null
        }
        JsonValueType.BOOLEAN -> readBoolean()
        else -> throw BackupValidationException(BackupErrorCode.INVALID_FIELD_TYPE, "Expected boolean or null")
    }

    fun readNull() {
        skipWhitespace()
        consumeLiteral("null")
    }

    fun skipValue() {
        when (peekType()) {
            JsonValueType.OBJECT -> readObject { _ -> skipValue() }
            JsonValueType.ARRAY -> readArray { _ -> skipValue() }
            JsonValueType.STRING -> readString()
            JsonValueType.NUMBER -> readNumberToken()
            JsonValueType.BOOLEAN -> readBoolean()
            JsonValueType.NULL -> readNull()
        }
    }

    fun ensureEof() {
        skipWhitespace()
        if (peekRaw() != -1) {
            throw BackupValidationException(BackupErrorCode.INVALID_JSON, "Unexpected trailing content")
        }
    }

    private fun consumeLiteral(expected: String) {
        for (ch in expected) {
            if (readRaw() != ch.code) {
                throw BackupValidationException(BackupErrorCode.INVALID_JSON, "Invalid literal")
            }
        }
    }

    private fun readNumberToken(): String {
        skipWhitespace()
        val sb = StringBuilder()
        if (peekRaw() == '-'.code) {
            sb.append(readRaw().toChar())
        }
        when (val first = peekRaw()) {
            '0'.code -> sb.append(readRaw().toChar())
            in '1'.code..'9'.code -> {
                while (peekRaw() in '0'.code..'9'.code) {
                    sb.append(readRaw().toChar())
                    if (sb.length > 64) {
                        throw BackupValidationException(BackupErrorCode.INVALID_JSON, "Number token too long")
                    }
                }
            }
            else -> throw BackupValidationException(BackupErrorCode.INVALID_JSON, "Invalid number")
        }
        if (peekRaw() == '.'.code) {
            sb.append(readRaw().toChar())
            if (peekRaw() !in '0'.code..'9'.code) {
                throw BackupValidationException(BackupErrorCode.INVALID_JSON, "Invalid fraction in number")
            }
            while (peekRaw() in '0'.code..'9'.code) {
                sb.append(readRaw().toChar())
                if (sb.length > 64) {
                    throw BackupValidationException(BackupErrorCode.INVALID_JSON, "Number token too long")
                }
            }
        }
        if (peekRaw() == 'e'.code || peekRaw() == 'E'.code) {
            sb.append(readRaw().toChar())
            if (peekRaw() == '+'.code || peekRaw() == '-'.code) {
                sb.append(readRaw().toChar())
            }
            if (peekRaw() !in '0'.code..'9'.code) {
                throw BackupValidationException(BackupErrorCode.INVALID_JSON, "Invalid exponent in number")
            }
            while (peekRaw() in '0'.code..'9'.code) {
                sb.append(readRaw().toChar())
                if (sb.length > 64) {
                    throw BackupValidationException(BackupErrorCode.INVALID_JSON, "Number token too long")
                }
            }
        }
        return sb.toString()
    }
}

/**
 * Streaming pretty-printed JSON writer operating directly on any [java.io.Writer].
 */
class JsonStreamWriter(
    private val writer: Writer,
    private val indentUnit: String = "  ",
) {
    private data class Scope(val isObject: Boolean, var hasItems: Boolean = false, var expectingValue: Boolean = false)

    private val stack = ArrayDeque<Scope>()

    fun beginObject(): JsonStreamWriter {
        beforeValue()
        writer.write("{")
        stack.addLast(Scope(isObject = true))
        return this
    }

    fun endObject(): JsonStreamWriter {
        val scope = stack.removeLast()
        check(scope.isObject && !scope.expectingValue) { "Unbalanced object in JsonStreamWriter" }
        if (scope.hasItems) {
            writer.write('\n'.code)
            writeIndent()
        }
        writer.write("}")
        return this
    }

    fun beginArray(): JsonStreamWriter {
        beforeValue()
        writer.write("[")
        stack.addLast(Scope(isObject = false))
        return this
    }

    fun endArray(): JsonStreamWriter {
        val scope = stack.removeLast()
        check(!scope.isObject) { "Unbalanced array in JsonStreamWriter" }
        if (scope.hasItems) {
            writer.write('\n'.code)
            writeIndent()
        }
        writer.write("]")
        return this
    }

    fun name(key: String): JsonStreamWriter {
        val scope = stack.last()
        check(scope.isObject && !scope.expectingValue) { "Unexpected name() in JsonStreamWriter" }
        if (scope.hasItems) {
            writer.write(",")
        }
        writer.write('\n'.code)
        writeIndent()
        writeEscapedString(key)
        writer.write(": ")
        scope.hasItems = true
        scope.expectingValue = true
        return this
    }

    fun value(v: String?): JsonStreamWriter {
        beforeValue()
        if (v == null) {
            writer.write("null")
        } else {
            writeEscapedString(v)
        }
        return this
    }

    fun value(v: Int?): JsonStreamWriter {
        beforeValue()
        writer.write(v?.toString() ?: "null")
        return this
    }

    fun value(v: Long?): JsonStreamWriter {
        beforeValue()
        writer.write(v?.toString() ?: "null")
        return this
    }

    fun value(v: Boolean): JsonStreamWriter {
        beforeValue()
        writer.write(if (v) "true" else "false")
        return this
    }

    fun flush() {
        writer.flush()
    }

    private fun beforeValue() {
        val scope = stack.lastOrNull() ?: return
        if (scope.isObject) {
            check(scope.expectingValue) { "Missing property name in object" }
            scope.expectingValue = false
        } else {
            if (scope.hasItems) {
                writer.write(",")
            }
            writer.write('\n'.code)
            writeIndent()
            scope.hasItems = true
        }
    }

    private fun writeIndent() {
        repeat(stack.size) { writer.write(indentUnit) }
    }

    private fun writeEscapedString(value: String) {
        writer.write('"'.code)
        for (ch in value) {
            when (ch) {
                '"' -> writer.write("\\\"")
                '\\' -> writer.write("\\\\")
                '\b' -> writer.write("\\b")
                '\u000C' -> writer.write("\\f")
                '\n' -> writer.write("\\n")
                '\r' -> writer.write("\\r")
                '\t' -> writer.write("\\t")
                else -> {
                    if (ch.code < 0x20) {
                        writer.write("\\u%04x".format(java.util.Locale.ROOT, ch.code))
                    } else {
                        writer.write(ch.code)
                    }
                }
            }
        }
        writer.write('"'.code)
    }
}
