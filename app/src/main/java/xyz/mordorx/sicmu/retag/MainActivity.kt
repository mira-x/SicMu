@file:OptIn(ExperimentalMaterial3Api::class)

package xyz.mordorx.sicmu.retag

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.Vibrator
import android.util.DisplayMetrics
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import xyz.mordorx.sicmu.AlbumArtLoader
import xyz.mordorx.sicmu.MusicService
import xyz.mordorx.sicmu.RowSong
import java.util.Optional
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {
    private var musicService: MutableState<MusicService?> = mutableStateOf(null)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as MusicService.MusicBinder
            musicService.value = binder.service
        }

        override fun onServiceDisconnected(name: ComponentName) {
            musicService.value = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Intent(this, MusicService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
        val widthDp = resources.displayMetrics.run { widthPixels / density }.dp
        val heightDp = resources.displayMetrics.run { heightPixels / density }.dp
        enableEdgeToEdge()
        setContent {
            if (musicService.value != null) {
                val decs = remember { MoveArticleDecision.Factory.generateDecisions(musicService.value!!.rows) }

                HorizontalUncontainedCarousel(
                    state = rememberCarouselState(0) { decs.size },
                    itemWidth = widthDp,
                    itemSpacing = 30.dp
                ) { i ->
                    decs[i].display { a ->
                        Log.d("MIRAX", "MoveDecision is positive. Invalidated files:" + a.joinToString(", "))
                    }
/*                    val song = rows[i]
                    val pathElems = song.path.split('/', ' ', '.')
                    val bmp by remember { AlbumArtLoader(applicationContext, song).loadAsync() }
                    var newPath by remember { mutableStateOf(Optional.empty<String>()) }
                    Surface(Modifier
                        .fillMaxSize()
                        .padding(25.dp, 50.dp)) {
                        Column(Modifier.fillMaxSize()) {
                            Image(painter = BitmapPainter(bmp), song.filename, Modifier.height(250.dp))
                            if (newPath.isPresent) {
                                Text("Selected new Path: " + newPath.get())
                            } else {
                                DuolingoSelector(pathElems) { res ->
                                    newPath = Optional.of(res.joinToString("/"))
                                }
                            }
                            Spacer(Modifier.height(25.dp))
                        }
                    }*/
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    var items by remember { mutableStateOf(IntRange(0, 1000).toList()) }
    Column {
        Text(
            text = "Hello $name!, these are the natural numbers:",
            modifier = modifier
        )
        HorizontalUncontainedCarousel(
            state = rememberCarouselState(0) { items.size },
            itemWidth = 100.dp,
            itemSpacing = 15.dp
        ) {
            i -> NumberView(items[i], {
                items = items.filterIndexed { index, _ -> index != i }
            })
        }
    }
}

@Composable
@Preview
fun SelectorPreview() {
    DuolingoSelector("home".split(' ', ',', '.', '!'), { l ->
        l.forEach { s -> Log.d("Selecktor", "Received: " + s) }
    })
}

@Composable
fun DuolingoSelector(options: List<String>, submitCallback: (List<String>) -> Unit) {
    val selected = remember { mutableStateListOf<Int>() }

    val btnMod = Modifier.padding(5.dp, 0.dp)
    val rowMod = Modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = 150.dp)
    val btnShape = RoundedCornerShape(8.dp)
    val vibrator = LocalContext.current.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    val callbackSelect = { i: Int ->
        vibrator.vibrate(10)
        selected.add(i)
    }
    val callbackDeselect = { i: Int ->
        vibrator.vibrate(10)
        selected.remove(i)
    }
    val callbackSubmitInternal = {
        val out = selected.map { index -> options[index] }
        submitCallback(out)
    }

    Column(Modifier
        //.fillMaxSize()
        .padding(25.dp, 15.dp)
        .height(IntrinsicSize.Max)) {
        Spacer(modifier = Modifier.weight(5f))
        // Selected elements
        FlowRow(rowMod,
            verticalArrangement = Arrangement.Center,
            horizontalArrangement = Arrangement.Start) {
            // We add unselected elements invisibly so this FlowRow always takes up the same
            // amount of space and has no layout shifts when (de)selecting options.
            // Also, in order to avoid gaps between selected options, we sort by selectedness.
            options
                .withIndex()
                .sortedBy { !selected.contains(it.index) } // Selected ones at the beginning
                .forEach {
                    val isSelected = selected.contains(it.index)
                    val mod = btnMod.alpha(if (isSelected) 1f else 0f)
                    Button(onClick={callbackDeselect(it.index)},
                        modifier=mod,
                        shape=btnShape,
                        enabled=isSelected) {
                        Text(it.value)
                    }
                }
        }
        HorizontalDivider()
        // Unselected elements
        FlowRow(rowMod,
                verticalArrangement = Arrangement.Center,
                horizontalArrangement = Arrangement.Center) {
            options.forEachIndexed { index, s ->
                val isSelected = selected.contains(index)
                val alpha = if (isSelected) 0f else 1f
                Button(onClick={callbackSelect(index)},
                        modifier=btnMod,
                        shape=btnShape,
                        enabled = !isSelected) {
                    Text(s, Modifier.alpha(alpha))
                }
            }
        }
        HorizontalDivider()
        // Submit Button
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick=callbackSubmitInternal,
                    enabled = selected.isNotEmpty(),
                    modifier = btnMod,
                    shape = btnShape) {
                Text(stringResource(android.R.string.ok))
            }
        }
    }
}

@Composable
fun NumberView(index: Int, deleteCallback: () -> Unit) {
    Log.d("NumberVIew", "Instanciating number view no. $index")
    Surface(modifier = Modifier.background(Color.LightGray), shape = RoundedCornerShape(15.dp)) {
        Column {
            Text("Your lucky number is:", textAlign = TextAlign.Center)
            Spacer(Modifier.weight(.1f))
            Text(index.toString(), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), fontSize = 10.em)
            Spacer(Modifier.weight(.1f))
            Text("Sqrt = " + (sqrt(index.toDouble())).toString(), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.weight(.1f))
            Text("² = " + (index*index).toString(), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.weight(.5f))
            Button(deleteCallback) {
                Text("I know this, delete!")
            }
            Spacer(Modifier.weight(1f))

        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    Greeting("Android", Modifier
        .padding(5.dp)
        .alpha(0.5f)
        .border(.1.dp, Color.Red))
}