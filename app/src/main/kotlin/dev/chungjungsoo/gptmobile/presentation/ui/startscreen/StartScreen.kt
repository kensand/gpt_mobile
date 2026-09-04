package dev.chungjungsoo.gptmobile.presentation.ui.startscreen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.presentation.common.PrimaryLongButton
import dev.chungjungsoo.gptmobile.presentation.icons.GptMobileStartScreen

@Composable
fun StartScreen(onStartClick: () -> Unit) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                StartScreenLogo()
                Spacer(modifier = Modifier.weight(1f))
                WelcomeText()
                PrimaryLongButton(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    onClick = onStartClick,
                    text = stringResource(R.string.get_started)
                )
            }
        }
    }
}

@Preview
@Composable
fun StartScreenLogo(modifier: Modifier = Modifier) {
    Image(
        imageVector = GptMobileStartScreen,
        contentDescription = stringResource(R.string.gpt_mobile_introduction_logo),
        contentScale = ContentScale.Fit,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp)
            .heightIn(max = 360.dp)
    )
}

@Preview
@Composable
fun WelcomeText(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            modifier = Modifier.semantics { heading() },
            text = stringResource(R.string.welcome_title),
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            modifier = Modifier.padding(top = 8.dp),
            text = stringResource(R.string.welcome_description),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
