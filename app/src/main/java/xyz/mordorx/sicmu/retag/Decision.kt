package xyz.mordorx.sicmu.retag

import androidx.compose.foundation.SurfaceScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.channels.Channel
import java.io.File

abstract class Decision {
    open fun isValid(invalidatedFiles: List<File>): Boolean {
        return true
    }

    /**
     * @param okCallback This is called after the user has accepted the decision and changes have been made. It also sends a list of Files (absolute paths) that have been invalidated as cause of the changes.
     * @param hideCallback This is called after okCallback after the user accepts or denies a change. This means that this decision should not be shown again.
     */
    @Composable
    public open fun display(okCallback: (List<File>) -> Unit, hideCallback: () -> Unit) {

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


@Preview
@Composable
fun AcceptDenyButtonsPreview() {
    Column {
        AcceptDenyButtons(denyEnabled = true, acceptEnabled = true)
        AcceptDenyButtons(denyEnabled = false, acceptEnabled = false)
    }
}

@Composable
fun AcceptDenyButtons(
    denyCallback: () -> Unit = {},
    acceptCallback: () -> Unit = {},
    denyEnabled: Boolean = true,
    acceptEnabled: Boolean = true) {

    val btnShape = RoundedCornerShape(8.dp)

    val red = ButtonColors(Color(.85f, 0f, 0f), Color.White, Color(.6f, .5f, .5f), Color.White)
    val green = ButtonColors(Color(0f, .85f, 0f), Color.White, Color(.5f, .6f, .5f), Color.White)
    val bottomBtnMod = Modifier.fillMaxWidth().height(50.dp)

    Row(modifier = Modifier.fillMaxWidth().padding(0.dp, 5.dp), horizontalArrangement = Arrangement.End) {
        Row(Modifier.weight(1f)) {
            Button(onClick = denyCallback,
                enabled = denyEnabled,
                shape = btnShape,
                modifier = bottomBtnMod,
                colors = red) {
                Icon(Icons.Rounded.Close, "Deny")
            }
        }
        Spacer(Modifier.weight(0.05f))
        Row(Modifier.weight(1f)) {
            Button(onClick=acceptCallback,
                enabled = acceptEnabled,
                modifier = bottomBtnMod,
                shape = btnShape,
                colors = green) {
                Icon(imageVector = Icons.Rounded.Check, "Accept")
            }
        }
    }
}