package xyz.mordorx.sicmu.retag

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalContext
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.currentCompositionContext
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import xyz.mordorx.sicmu.AlbumArtLoader
import xyz.mordorx.sicmu.RowSong
import xyz.mordorx.sicmu.Rows
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 *
 */
data class MoveArticleDecision(
    val song: RowSong,
    val originalPath: File,
    val newPath: File
    ) : Decision() {

    companion object Factory {
        private val articles = listOf("The", "Die") // English + German (genderless plural)

        fun generateDecisions(rows: Rows): List<Decision> {
            return rows
                .rowsUnfolded
                .stream()
                .filter { e -> e is RowSong }
                .map { e -> e as RowSong }
                .filter(this::isCandidate)
                .map(this::candidateToDecision)
                .toList()
        }

        /**
         * Only pass songs that are candidates per isCandidate()!
          */
        private fun candidateToDecision(s: RowSong): MoveArticleDecision {
            val oldName = s.filename
            val article = articles.first { a -> oldName.startsWith(a) }
            val artistName = oldName.substringBefore('-').removePrefix(article).trim()
            val songDetails = oldName.substringAfter('-').trim()
            val newName = "$artistName, $article - $songDetails"

            val oldPath = File(s.path)
            val newPath = File(oldPath.parentFile, newName)

            return MoveArticleDecision(s, oldPath, newPath)
        }

        private fun isCandidate(s: RowSong): Boolean {
            val filename = s.filename
            return articles.any { article ->
                // We check if it starts with <article> but does not contain a ", <article>".
                // Why? There's an edge case: The band "The The", whose moved-article version is written as "The, The". If this band wouldn't exist, a simple startsWith() would have been enough.
                // We also check that we have a clear distinction between artist and song name, like "The The - This Is The Day", so we know where the artist's name ends (before the '-')

                return filename.startsWith("$article ") &&
                        !filename.contains(", $article") &&
                        filename.indexOf('-') > filename.indexOf(article)
            }
        }
    }

    @Composable
    private fun ColumnScope.Center(content: @Composable RowScope.() -> Unit) {
        Row(modifier= Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.Center, content=content)
    }

    @Composable
    private fun ColumnScope.End(content: @Composable RowScope.() -> Unit) {
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.End,
            content = content
        )
    }

    @OptIn(InternalComposeApi::class)
    @Composable
    public override fun display(okCallback: (List<File>) -> Unit) {
        val context = LocalContext.current
        val bmp by remember(context, song) { AlbumArtLoader(context, song).loadAsync() }

        DecisionSurface {
            Column() {
                Center {
                    Image(painter= BitmapPainter(bmp), "", Modifier.defaultMinSize(minHeight = 200.dp))
                }

                Center {
                    Text("Move Artist Article", fontSize = 7.em)
                }

                Center {
                    Text("From:")
                    Text(originalPath.toString())
                }

                Center {
                    Text("To:")
                    Text(newPath.toString())

                }

                End {
                    Button({ okCallback(listOf(originalPath, newPath)) }) {
                        Text("Accept")
                    }
                }
            }
        }
    }
}