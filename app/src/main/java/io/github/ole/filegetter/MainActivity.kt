package io.github.ole.filegetter

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.MutableLiveData
import io.github.ole.filegetter.getter.Job
import io.github.ole.filegetter.getter.JobExecutor
import io.github.ole.filegetter.ui.theme.FileGetterTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val jobExecutor by lazy { JobExecutor(coroutineScope) }
    private val jobState = MutableLiveData(Job.State.WAITING)
    private val jobListener = object : Job.Listener {
        override fun onJobState(id: String, state: Job.State, message: String) {
            Log.i(TAG, "onJobState[$id]: $state $message")
            jobState.postValue(state)
        }

        override fun onJobProgress(id: String, progress: Long) {
            Log.i(TAG, "onJobProgress[$id]: $progress")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FileGetterTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val currentState = jobState.observeAsState()
                    Greeting(
                        downloading = currentState.value == Job.State.DOWNLOADING,
                        ::download, ::cancel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    private fun download() {
        jobExecutor.execute(
            Job(
                id = "gradle-8.13-bin",
                url = "https://services.gradle.org/distributions/gradle-8.13-bin.zip",
                md5 = "ea50a70340ae49febcc239359925222d",
                cacheDir = cacheDir.absolutePath,
                targetDir = filesDir.absolutePath
            ),
            jobListener
        )
    }

    private fun cancel() {
        jobExecutor.cancel("gradle-8.13-bin")
    }
}

@Composable
fun Greeting(downloading: Boolean, download: () -> Unit, cancel: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Button(
            enabled = !downloading,
            onClick = {
                download()
            }
        ) {
            Text(text = "Download File")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button (
            enabled = downloading,
            onClick = {
                cancel()
            }
        ) {
            Text(text = "Cancel Download")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    FileGetterTheme {
        Greeting(false, {}, {})
    }
}
