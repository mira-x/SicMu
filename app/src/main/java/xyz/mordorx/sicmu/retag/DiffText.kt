/** This is a cheap text diff generator. It was completely authored by Claude (AI) and I (mirax) have no idea what it is doing.
 */
package xyz.mordorx.sicmu.retag

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview
@Composable
fun textDiffPreview() {
    val a = "/home/mira/Musik/The Limeliters - Take My True Love by the Hand.opus"
    val b = "/home/mira/Musik/Limeliters, The - Take My True Love by the Hand.opus"
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "From:",
            fontWeight = FontWeight.Bold
        )
        Text(
            text = buildWordDiff(a, b, showOriginal = true),
            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp)
        )

        Text(
            text = "To:",
            fontWeight = FontWeight.Bold
        )
        Text(
            text = buildWordDiff(a, b, showOriginal = false),
            modifier = Modifier.padding(start = 16.dp, top = 4.dp)
        )
    }
}

fun buildWordDiff(
    original: String,
    modified: String,
    showOriginal: Boolean
) = buildAnnotatedString {
    val originalTokens = tokenize(original)
    val modifiedTokens = tokenize(modified)

    val diff = computeDiff(originalTokens, modifiedTokens)

    if (showOriginal) {
        // Original: Show deleted tokens in red
        diff.forEach { (type, token) ->
            when (type) {
                DiffType.UNCHANGED -> {
                    append(token)
                }
                DiffType.DELETED -> {
                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Bold,
                            color = Color.Red,
                            //background = Color.Red.copy(alpha = 0.15f)
                        )
                    ) {
                        append(token)
                    }
                }
                DiffType.INSERTED -> {
                    // Don't show anything in original
                }
            }
        }
    } else {
        // Modified: Show new tokens in green, missing tokens as red pipe
        var lastWasDeleted = false

        diff.forEach { (type, token) ->
            when (type) {
                DiffType.UNCHANGED -> {
                    append(token)
                    lastWasDeleted = false
                }
                DiffType.DELETED -> {
                    // Only show one hair for consecutive deletions
                    if (!lastWasDeleted) {
                        withStyle(
                            SpanStyle(
                                fontWeight = FontWeight.Bold,
                                color = Color.Red,
                                background = Color.Red.copy(alpha = 0.75f)
                            )
                        ) {
                            // This is the hair character, way thinner than a normal space.
                            // It's used to indicate that there is no new whitespace at this location
                            append("\u200A")
                        }
                    }
                    lastWasDeleted = true
                }
                DiffType.INSERTED -> {
                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32),
                            //background = Color.Green.copy(alpha = 0.15f)
                        )
                    ) {
                        append(token)
                    }
                    lastWasDeleted = false
                }
            }
        }
    }
}

private enum class DiffType {
    UNCHANGED,
    DELETED,
    INSERTED
}

private fun computeDiff(
    original: List<String>,
    modified: List<String>
): List<Pair<DiffType, String>> {
    val result = mutableListOf<Pair<DiffType, String>>()

    val n = original.size
    val m = modified.size
    val max = n + m

    if (max == 0) return emptyList()

    val v = IntArray(2 * max + 1)
    val trace = mutableListOf<IntArray>()

    // Myers diff algorithm - find shortest edit script
    search@ for (d in 0..max) {
        val current = v.clone()
        trace.add(current)

        for (k in -d..d step 2) {
            var x = if (k == -d || (k != d && v[k - 1 + max] < v[k + 1 + max])) {
                v[k + 1 + max]
            } else {
                v[k - 1 + max] + 1
            }

            var y = x - k

            // Extend the path as far as possible with matches
            while (x < n && y < m && original[x] == modified[y]) {
                x++
                y++
            }

            v[k + max] = x

            if (x >= n && y >= m) {
                break@search
            }
        }
    }

    // Backtrack to build the diff
    var x = n
    var y = m

    for (d in trace.size - 1 downTo 0) {
        val v = trace[d]
        val k = x - y

        val prevK = if (k == -d || (k != d && v[k - 1 + max] < v[k + 1 + max])) {
            k + 1
        } else {
            k - 1
        }

        val prevX = v[prevK + max]
        val prevY = prevX - prevK

        // Add all matching tokens
        while (x > prevX && y > prevY) {
            result.add(0, DiffType.UNCHANGED to original[x - 1])
            x--
            y--
        }

        if (d > 0) {
            if (x == prevX) {
                // INSERT operation
                result.add(0, DiffType.INSERTED to modified[y - 1])
                y--
            } else {
                // DELETE operation
                result.add(0, DiffType.DELETED to original[x - 1])
                x--
            }
        }
    }

    return result
}

private fun tokenize(text: String): List<String> {
    val tokens = mutableListOf<String>()
    val currentWord = StringBuilder()

    for (char in text) {
        when {
            char.isLetterOrDigit() -> {
                currentWord.append(char)
            }
            else -> {
                // Save current word if exists
                if (currentWord.isNotEmpty()) {
                    tokens.add(currentWord.toString())
                    currentWord.clear()
                }
                // Add special character/whitespace as separate token
                tokens.add(char.toString())
            }
        }
    }

    // Add last word if exists
    if (currentWord.isNotEmpty()) {
        tokens.add(currentWord.toString())
    }

    return tokens
}