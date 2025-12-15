package xyz.mordorx.sicmu.retag

import androidx.compose.foundation.SurfaceScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.channels.Channel
import java.io.File

abstract class Decision {
    open fun isValid(invalidatedFiles: List<File>): Boolean {
        return true
    }

    /**
     * @param okCallback This is called after the user has accepted the decision and changes have been made. It also sends a list of Files (absolute paths) that have been invalidated as cause of the changes.
     */
    @Composable
    public open fun display(okCallback: (List<File>) -> Unit) {

    }

    @Composable
    protected fun DecisionSurface(content: @Composable () -> Unit) {
        val shape = RoundedCornerShape(15.dp)
        Surface(modifier = Modifier.fillMaxWidth().padding(25.dp, 75.dp), shape = shape) {
            Surface(modifier = Modifier.fillMaxSize().padding(15.dp), shape = shape) {
                content()
            }
        }
    }
}
