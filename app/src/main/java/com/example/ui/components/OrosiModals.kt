package com.example.ui.components

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.local.AppSettingsEntity
import com.example.data.local.BankAccountEntity
import com.example.data.local.CommitmentEntity
import com.example.data.local.TransactionEntity
import com.example.domain.CashflowHealthStatus
import com.example.domain.CashflowTxSnapshot
import com.example.domain.JalaliCalendarUtils
import com.example.domain.OrosiCashflowAnalyzer
import com.example.domain.OrosiRegressionSuite
import com.example.domain.PersianNumberUtils
import com.example.domain.RegressionSuiteReport
import com.example.domain.UntaggedSmsCandidate
import com.example.domain.VoiceSmsMatchResult
import com.example.ui.VoiceUiState
import com.example.ui.theme.AlabasterSilk
import com.example.ui.theme.NishapurTurquoise
import com.example.ui.theme.NishapurTurquoiseBright
import com.example.ui.theme.NishapurTurquoiseDeep
import com.example.ui.theme.NishapurTurquoiseSoft
import com.example.ui.theme.OrosiHairline
import com.example.ui.theme.OrosiOnSurfaceVariant
import com.example.ui.theme.OrosiSurfaceContainer
import com.example.ui.theme.OrosiSurfaceHigh
import com.example.ui.theme.OrosiSurfaceLow
import com.example.ui.theme.PersianLapis
import com.example.ui.theme.PersianLapisLight
import com.example.ui.theme.RoyalAmethyst
import com.example.ui.theme.RoyalAmethystSoft
import com.example.ui.theme.RubyRose
import com.example.ui.theme.RubyRoseSoft
import com.example.ui.theme.SaffronGold
import com.example.ui.theme.SaffronGoldBright
import com.example.ui.theme.SaffronGoldLight
import kotlin.math.sin

private fun formatAmountTextFieldValue(newValue: TextFieldValue): TextFieldValue {
    val formatted = PersianNumberUtils.formatLiveAmountInput(newValue.text)
    return TextFieldValue(
        text = formatted,
        selection = TextRange(formatted.length)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceOverlayBottomSheet(
    voiceState: VoiceUiState,
    onSpeechChange: (String) -> Unit,
    onManualAmountChange: (String) -> Unit = {},
    onPurposeChange: (String) -> Unit = {},
    onTransactionTypeChange: (Boolean) -> Unit = {},
    onUpdateMultiItem: (index: Int, newTitle: String, newAmountStr: String) -> Unit = { _, _, _ -> },
    onToggleMultiItemType: (index: Int) -> Unit = {},
    onSetMultiItemType: (index: Int, isIncome: Boolean) -> Unit = { idx, _ -> onToggleMultiItemType(idx) },
    onToggleMultiItemScale: (index: Int) -> Unit = {},
    onToggleSingleScale: () -> Unit = {},
    onSetSingleMatchedBankTx: (Long?) -> Unit = {},
    onSetMultiItemMatchedBankTx: (index: Int, txId: Long?) -> Unit = { _, _ -> },
    onToggleSingleDestination: () -> Unit = {},
    onSetSingleCommitmentCategory: (String) -> Unit = {},
    onToggleMultiItemDestination: (index: Int) -> Unit = {},
    onSetMultiItemCommitmentCategory: (index: Int, category: String) -> Unit = { _, _ -> },
    onSimulateVoiceRecord: () -> Unit,
    onConfirmVoiceEntry: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isListening by remember { mutableStateOf(false) }
    var voiceStatusHint by remember { mutableStateOf("برای شروع یا ضبط مجدد صحبت کنید") }
    var hasAutoLaunchedSystemDialogOnError by remember { mutableStateOf(false) }

    val currentOnSpeechChange by rememberUpdatedState(onSpeechChange)
    val currentOnSimulateVoiceRecord by rememberUpdatedState(onSimulateVoiceRecord)
    val currentRecognizedText by rememberUpdatedState(voiceState.recognizedText)

    // System Voice Popup Dialog Launcher (works 100% reliably even when a Screen Recorder holds the microphone!)
    val systemVoiceDialogLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListening = false
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val bestSpoken = matches?.firstOrNull()?.trim()
            if (!bestSpoken.isNullOrBlank()) {
                currentOnSpeechChange(bestSpoken)
                voiceStatusHint = "متن صوتی دریافت شد ✓"
            }
        }
    }

    fun launchSystemVoicePopup(fallbackToSimIfUnavailable: Boolean = false) {
        try {
            val popupIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "fa-IR")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(
                    RecognizerIntent.EXTRA_PROMPT,
                    "جمله تراکنش را بگویید (مثلاً: ۷۰ تومن نانوایی)"
                )
            }
            systemVoiceDialogLauncher.launch(popupIntent)
        } catch (_: Exception) {
            if (fallbackToSimIfUnavailable) {
                currentOnSimulateVoiceRecord()
            }
        }
    }

    // Retain SpeechRecognizer with remember & destroy in DisposableEffect.onDispose
    val speechRecognizer = remember(context) {
        try {
            SpeechRecognizer.createSpeechRecognizer(context)
        } catch (_: Exception) {
            null
        }
    }

    fun buildSpeechRecognizerIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "fa-IR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

    fun startOrRestartRecognition(triggerFallbackIfUnavailable: Boolean = false) {
        if (speechRecognizer != null) {
            try {
                if (isListening) {
                    try {
                        speechRecognizer.stopListening()
                        speechRecognizer.cancel()
                    } catch (_: Exception) {
                    }
                    isListening = false
                }
                isListening = true
                voiceStatusHint = "در حال شنیدن صدای شما... (مثلاً بگویید: ۷۰ تومن نانوایی)"
                speechRecognizer.startListening(buildSpeechRecognizerIntent())
            } catch (_: Exception) {
                isListening = false
                if (triggerFallbackIfUnavailable) {
                    launchSystemVoicePopup(fallbackToSimIfUnavailable = true)
                }
            }
        } else if (triggerFallbackIfUnavailable) {
            launchSystemVoicePopup(fallbackToSimIfUnavailable = true)
        }
    }

    DisposableEffect(speechRecognizer) {
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                voiceStatusHint = "در حال شنیدن صدای شما... صحبت کنید"
            }

            override fun onBeginningOfSpeech() {
                isListening = true
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                isListening = false
                voiceStatusHint = "ضبط پایان یافت • برای ضبط مجدد روی موج ضربه بزنید"
            }

            override fun onError(error: Int) {
                isListening = false
                voiceStatusHint = "برای ضبط مجدد روی موج یا دکمه دیالوگ صوتی سیستم بزنید"
                // Critical fallback: if background SpeechRecognizer fails (e.g. ERROR_AUDIO = 3 when Screen Recorder holds mic,
                // ERROR_CLIENT = 5, ERROR_NO_MATCH = 7, ERROR_INSUFFICIENT_PERMISSIONS = 9), launch native system voice dialog
                if (!hasAutoLaunchedSystemDialogOnError &&
                    currentRecognizedText.isBlank() &&
                    (error == SpeechRecognizer.ERROR_AUDIO ||
                        error == SpeechRecognizer.ERROR_CLIENT ||
                        error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                        error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ||
                        error == SpeechRecognizer.ERROR_NO_MATCH)
                ) {
                    hasAutoLaunchedSystemDialogOnError = true
                    launchSystemVoicePopup(fallbackToSimIfUnavailable = false)
                }
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                val firstSpoken = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                if (!firstSpoken.isNullOrBlank()) {
                    currentOnSpeechChange(firstSpoken)
                    voiceStatusHint = "متن صوتی دریافت شد ✓"
                } else {
                    voiceStatusHint = "صدایی تشخیص داده نشد • دوباره امتحان کنید"
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                if (!partial.isNullOrBlank()) {
                    currentOnSpeechChange(partial)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
        speechRecognizer?.setRecognitionListener(listener)

        onDispose {
            isListening = false
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            } catch (_: Exception) {
            }
        }
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        launchSystemVoicePopup(fallbackToSimIfUnavailable = false)
    }

    // Immediately and automatically launch the system Google Voice Dialog (ACTION_RECOGNIZE_SPEECH) on open
    LaunchedEffect(Unit) {
        launchSystemVoicePopup(fallbackToSimIfUnavailable = false)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "voiceOverlayWave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = OrosiSurfaceLow,
        contentColor = AlabasterSilk
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .testTag("voice_overlay_sheet"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val targetTx = voiceState.targetUntaggedTransaction
            if (targetTx != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(OrosiSurfaceContainer)
                        .border(1.dp, PersianLapisLight.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "تراکنش بانک ${targetTx.shortBankName}",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = NishapurTurquoiseSoft,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OrosiAmountText(
                            amountToman = targetTx.totalAmountToman,
                            color = AlabasterSilk,
                            numberStyle = MaterialTheme.typography.titleSmall,
                            numberFontWeight = FontWeight.ExtraBold,
                            unitStyle = MaterialTheme.typography.labelSmall,
                            unitColor = OrosiOnSurfaceVariant,
                            isIncome = targetTx.type == "INCOME"
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            val multiItems = voiceState.parsedMultiItems
            val hasMultiItems = multiItems.size > 1
            val isMultiNewMode = targetTx == null && hasMultiItems

            // 1. Sleek Unified Voice Capture Header Card (wave + inline mic pill, zero redundant buttons)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                NishapurTurquoiseDeep,
                                PersianLapis
                            )
                        )
                    )
                    .border(
                        1.dp,
                        NishapurTurquoiseBright.copy(alpha = 0.50f),
                        RoundedCornerShape(22.dp)
                    )
                    .clickable {
                        launchSystemVoicePopup(fallbackToSimIfUnavailable = true)
                    }
                    .padding(vertical = 14.dp, horizontal = 16.dp)
                    .testTag("voice_record_button"),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                ) {
                    val barCount = 26
                    val totalWidth = size.width
                    val spacing = totalWidth / (barCount * 1.8f)
                    val barWidth = spacing * 0.85f
                    val centerY = size.height / 2f

                    for (i in 0 until barCount) {
                        val norm = i.toFloat() / barCount.toFloat()
                        val envelope = sin(norm * Math.PI).toFloat()
                        val dynamicWave = if (isListening || voiceState.isRecordingSimulated) {
                            0.38f + 0.62f * kotlin.math.abs(sin(phase * 1.4f + i * 0.45f))
                        } else {
                            0.25f + 0.35f * kotlin.math.abs(sin(phase + i * 0.45f))
                        }
                        val barHeight = (size.height * 0.88f * envelope * dynamicWave).coerceAtLeast(10f)
                        val x = (totalWidth - (barCount * (barWidth + spacing))) / 2f + i * (barWidth + spacing)

                        drawRoundRect(
                            color = if (i % 2 == 0) NishapurTurquoiseBright else NishapurTurquoiseSoft.copy(alpha = 0.78f),
                            topLeft = Offset(x, centerY - barHeight / 2f),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isListening) "در حال شنیدن صدای شما..." else voiceStatusHint,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        color = NishapurTurquoiseSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(NishapurTurquoise)
                            .border(1.dp, NishapurTurquoiseBright, RoundedCornerShape(50))
                            .clickable { launchSystemVoicePopup(fallbackToSimIfUnavailable = true) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("system_voice_dialog_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "شروع مجدد ضبط صوتی",
                            tint = AlabasterSilk,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "ضبط مجدد",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = AlabasterSilk,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 1.5 Transaction Direction Selector (tactile Material 3 segmented toggle for single-item new entry)
            if (targetTx == null && !hasMultiItems) {
                val isSingleComm = voiceState.commitmentKind != null
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(OrosiSurfaceContainer)
                        .border(1.dp, PersianLapisLight.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                        .padding(5.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isExpenseSelected = !voiceState.isIncome
                    val isIncomeSelected = voiceState.isIncome

                    Surface(
                        onClick = { onTransactionTypeChange(false) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isExpenseSelected) RubyRose.copy(alpha = 0.34f) else OrosiSurfaceLow,
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (isExpenseSelected) 1.5.dp else 1.dp,
                            color = if (isExpenseSelected) RubyRoseSoft else OrosiHairline
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .testTag("voice_tx_type_expense")
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ArrowUpward,
                                contentDescription = null,
                                tint = if (isExpenseSelected) RubyRoseSoft else OrosiOnSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isSingleComm) "بدهی / برنامه خرید" else "پرداختی",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (isExpenseSelected) FontWeight.ExtraBold else FontWeight.Medium
                                ),
                                color = if (isExpenseSelected) AlabasterSilk else OrosiOnSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Surface(
                        onClick = { onTransactionTypeChange(true) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isIncomeSelected) NishapurTurquoiseDeep else OrosiSurfaceLow,
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (isIncomeSelected) 1.5.dp else 1.dp,
                            color = if (isIncomeSelected) NishapurTurquoiseBright else OrosiHairline
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .testTag("voice_tx_type_income")
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ArrowDownward,
                                contentDescription = null,
                                tint = if (isIncomeSelected) NishapurTurquoiseBright else OrosiOnSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isSingleComm) "طلب / واریز آینده" else "دریافتی",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (isIncomeSelected) FontWeight.ExtraBold else FontWeight.Medium
                                ),
                                color = if (isIncomeSelected) AlabasterSilk else OrosiOnSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            // 2. Single Clean Sentence/Description Field at the top
            OutlinedTextField(
                value = voiceState.recognizedText,
                onValueChange = onSpeechChange,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = AlabasterSilk
                ),
                label = {
                    Text(
                        when {
                            targetTx != null && targetTx.type == "INCOME" ->
                                "این واریزی از کجا اومد؟ (مثلاً: حقوق، تسویه علی)"
                            targetTx != null ->
                                "بابتِ این تراکنش بانکی (مثلاً: پفک، شارژ ایرانسل)"
                            else ->
                                "جمله یا توضیح تراکنش (مثلاً: ۷۰ تومن نانوایی)"
                        }
                    )
                },
                placeholder = {
                    Text(
                        when {
                            targetTx != null && targetTx.type == "INCOME" ->
                                "مثلاً: حقوق، واریز کارفرما، برگشت پول..."
                            targetTx != null ->
                                "مثلاً: پفک، شارژ ایرانسل، نانوایی..."
                            else ->
                                "مثلاً: ۷۰ تومن نانوایی یا ۵۰ تومن طلب دارم..."
                        }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("voice_live_text_input"),
                singleLine = false,
                maxLines = 3,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NishapurTurquoiseBright,
                    unfocusedBorderColor = PersianLapisLight.copy(alpha = 0.35f),
                    focusedContainerColor = OrosiSurfaceContainer,
                    unfocusedContainerColor = OrosiSurfaceContainer.copy(alpha = 0.72f)
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Redesigned Auto-Extracted Summary Section (Tactile Cards + Unmistakable Buttons + Zero Overlap)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (hasMultiItems) {
                    val commMultiCount = multiItems.count { it.isCommitment }
                    val txMultiCount = multiItems.size - commMultiCount
                    val multiHeaderText = when {
                        targetTx != null ->
                            "${PersianNumberUtils.toPersianDigits(multiItems.size.toString())} مورد از مبلغ بانک تفکیک شد"
                        commMultiCount == multiItems.size ->
                            "${PersianNumberUtils.toPersianDigits(multiItems.size.toString())} مورد آماده ثبت در تعهدات"
                        commMultiCount > 0 ->
                            "${PersianNumberUtils.toPersianDigits(txMultiCount.toString())} تراکنش و ${PersianNumberUtils.toPersianDigits(commMultiCount.toString())} تعهد تشخیص داده شد"
                        else ->
                            "${PersianNumberUtils.toPersianDigits(multiItems.size.toString())} تراکنش مجزا تشخیص داده شد"
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = multiHeaderText,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = NishapurTurquoiseSoft,
                            modifier = Modifier.testTag("recognized_speech_text")
                        )
                        Text(
                            text = "قابل ویرایش",
                            style = MaterialTheme.typography.labelSmall,
                            color = OrosiOnSurfaceVariant
                        )
                    }

                    multiItems.forEachIndexed { idx, item ->
                        var itemAmountField by remember(idx, item.amountToman) {
                            val formatted = PersianNumberUtils.formatLiveAmountInput(item.amountToman.toString())
                            mutableStateOf(
                                TextFieldValue(
                                    text = formatted,
                                    selection = TextRange(formatted.length)
                                )
                            )
                        }

                        val isCommItem = targetTx == null && item.isCommitment
                        val commDateShort = formatVoiceCommitmentDateShort(
                            dueDateHint = item.dueDateHint,
                            explicitJalaliDate = item.explicitJalaliDate,
                            eventDayOffset = item.eventDayOffset
                        )
                        val destBadgeText = when {
                            isCommItem && commDateShort.isNotBlank() ->
                                "تعهد • ${item.commitmentSubLabel.ifBlank { if (item.isIncome) "طلب" else "بدهی" }} • $commDateShort"
                            isCommItem && item.commitmentSubLabel.isNotBlank() ->
                                "تعهد • ${item.commitmentSubLabel}"
                            isCommItem -> "ثبت در تعهدات"
                            item.explicitJalaliDate.isNotBlank() ->
                                "تراکنش • ${JalaliCalendarUtils.stripYearFromJalaliDate(item.explicitJalaliDate)}"
                            item.eventDayOffset == -1 -> "تراکنش • دیروز"
                            item.eventDayOffset == -2 -> "تراکنش • پریروز"
                            item.eventDayOffset < -2 ->
                                "تراکنش • ${PersianNumberUtils.toPersianDigits((-item.eventDayOffset).toString())} روز پیش"
                            else -> "تراکنش • امروز"
                        }
                        val cardContainerBg = if (isCommItem) {
                            Color(0xFF231639) // Unmistakable deep Royal Amethyst fill for commitments
                        } else {
                            OrosiSurfaceContainer // Calm neutral slate fill for regular transactions
                        }

                        // Borderless container: bright borders/glow are reserved strictly for clickable buttons!
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(cardContainerBg)
                                .padding(14.dp)
                                .testTag("multi_voice_item_$idx"),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Row 1: Index badge + Destination/Date badge on right, clean BiDi-safe OrosiAmountText on left
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isCommItem) RoyalAmethyst.copy(alpha = 0.65f)
                                                else if (item.isIncome) NishapurTurquoiseDeep
                                                else RubyRose.copy(alpha = 0.35f)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = PersianNumberUtils.toPersianDigits((idx + 1).toString()),
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                            color = AlabasterSilk
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (isCommItem) RoyalAmethyst.copy(alpha = 0.34f)
                                                else PersianLapis.copy(alpha = 0.55f)
                                            )
                                            .padding(horizontal = 9.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isCommItem) Icons.Default.Schedule else Icons.Default.EventNote,
                                            contentDescription = null,
                                            tint = if (isCommItem) RoyalAmethystSoft else PersianLapisLight,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = destBadgeText,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = if (isCommItem) AlabasterSilk else AlabasterSilk,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                OrosiAmountText(
                                    amountToman = item.amountToman,
                                    color = if (item.isIncome) NishapurTurquoiseSoft else RubyRoseSoft,
                                    numberStyle = MaterialTheme.typography.titleSmall,
                                    numberFontWeight = FontWeight.ExtraBold,
                                    unitStyle = MaterialTheme.typography.labelSmall,
                                    unitColor = (if (item.isIncome) NishapurTurquoiseSoft else RubyRoseSoft).copy(alpha = 0.85f),
                                    isIncome = item.isIncome && !isCommItem
                                )
                            }

                            // Row 2: Tactile 2-Way Direction Buttons (borderless track; bright borders strictly on buttons)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black.copy(alpha = 0.24f))
                                    .padding(4.dp)
                                    .testTag("multi_voice_item_type_$idx"),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    onClick = { if (targetTx == null) onSetMultiItemType(idx, false) },
                                    enabled = targetTx == null,
                                    shape = RoundedCornerShape(9.dp),
                                    color = if (!item.isIncome) RubyRose.copy(alpha = 0.38f) else OrosiSurfaceHigh.copy(alpha = 0.55f),
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = if (!item.isIncome) 1.3.dp else 0.9.dp,
                                        color = if (!item.isIncome) RubyRoseSoft else PersianLapisLight.copy(alpha = 0.24f)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 38.dp)
                                        .testTag("multi_voice_item_expense_$idx")
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(vertical = 7.dp, horizontal = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.ArrowUpward,
                                            contentDescription = null,
                                            tint = if (!item.isIncome) RubyRoseSoft else OrosiOnSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = if (isCommItem) "بدهی" else "پرداختی",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = if (!item.isIncome) FontWeight.ExtraBold else FontWeight.SemiBold
                                            ),
                                            color = if (!item.isIncome) AlabasterSilk else OrosiOnSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                }

                                Surface(
                                    onClick = { if (targetTx == null) onSetMultiItemType(idx, true) },
                                    enabled = targetTx == null,
                                    shape = RoundedCornerShape(9.dp),
                                    color = if (item.isIncome) NishapurTurquoiseDeep else OrosiSurfaceHigh.copy(alpha = 0.55f),
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = if (item.isIncome) 1.3.dp else 0.9.dp,
                                        color = if (item.isIncome) NishapurTurquoiseBright else PersianLapisLight.copy(alpha = 0.24f)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 38.dp)
                                        .testTag("multi_voice_item_income_$idx")
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(vertical = 7.dp, horizontal = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.ArrowDownward,
                                            contentDescription = null,
                                            tint = if (item.isIncome) NishapurTurquoiseBright else OrosiOnSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = if (isCommItem) "طلب" else "دریافتی",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = if (item.isIncome) FontWeight.ExtraBold else FontWeight.SemiBold
                                            ),
                                            color = if (item.isIncome) AlabasterSilk else OrosiOnSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            // Row 3: High-contrast Editable Title + Amount Inputs
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                OutlinedTextField(
                                    value = item.purposeCategory,
                                    onValueChange = { newTitle ->
                                        onUpdateMultiItem(idx, newTitle, itemAmountField.text)
                                    },
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = AlabasterSilk
                                    ),
                                    label = {
                                        Text(
                                            text = "عنوان",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1.15f)
                                        .testTag("multi_voice_item_title_$idx"),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = NishapurTurquoiseBright,
                                        unfocusedBorderColor = PersianLapisLight.copy(alpha = 0.32f),
                                        focusedContainerColor = OrosiSurfaceContainer,
                                        unfocusedContainerColor = OrosiSurfaceLow
                                    )
                                )

                                OutlinedTextField(
                                    value = itemAmountField,
                                    onValueChange = { newVal ->
                                        val formattedVal = formatAmountTextFieldValue(newVal)
                                        itemAmountField = formattedVal
                                        onUpdateMultiItem(idx, item.purposeCategory, formattedVal.text)
                                    },
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = AlabasterSilk
                                    ),
                                    label = {
                                        Text(
                                            text = "مبلغ (تومان)",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("multi_voice_item_amount_$idx"),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = SaffronGoldBright,
                                        unfocusedBorderColor = PersianLapisLight.copy(alpha = 0.32f),
                                        focusedContainerColor = OrosiSurfaceContainer,
                                        unfocusedContainerColor = OrosiSurfaceLow
                                    )
                                )
                            }

                            // Row 4: Elevated Tactile Saffron Scale Toggle Button (unmistakably interactive!)
                            if (targetTx == null &&
                                (item.totalAmountToman in 1_000L..999_000L ||
                                    (item.totalAmountToman >= 1_000_000L && item.totalAmountToman % 1_000L == 0L))
                            ) {
                                val isThousandScale = item.totalAmountToman in 1_000L..999_000L
                                Surface(
                                    onClick = { onToggleMultiItemScale(idx) },
                                    shape = RoundedCornerShape(12.dp),
                                    color = SaffronGold.copy(alpha = 0.20f),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.2.dp,
                                        SaffronGoldBright.copy(alpha = 0.72f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 42.dp)
                                        .testTag("multi_voice_item_scale_$idx")
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SwapVert,
                                            contentDescription = null,
                                            tint = SaffronGoldLight,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = PersianNumberUtils.formatScaleToggleLabel(isThousandScale),
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                            color = SaffronGoldLight,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            // Row 5: Interactive Destination Switch (تراکنش عادی ↔ تعهدات + انتخاب دسته)
                            if (targetTx == null) {
                                VoiceDestinationControlSection(
                                    isCommitment = isCommItem,
                                    isIncome = item.isIncome,
                                    commitmentCategory = item.commitmentCategory,
                                    dueDateHint = item.dueDateHint,
                                    testTagPrefix = "multi_$idx",
                                    onToggleDestination = { onToggleMultiItemDestination(idx) },
                                    onSelectCommitmentCategory = { cat ->
                                        onSetMultiItemCommitmentCategory(idx, cat)
                                    }
                                )
                            }

                            // Row 6: Smart Bank SMS Auto-Match / 1-Tap Link Strip (non-commitment items only)
                            if (targetTx == null && !isCommItem) {
                                VoiceBankSmsMatchStrip(
                                    matchResult = voiceState.multiAutoMatches.getOrNull(idx),
                                    activeMatchedTxId = voiceState.multiMatchedBankTxIds.getOrNull(idx),
                                    testTagSuffix = "multi_$idx",
                                    onSetMatchedTxId = { newId ->
                                        onSetMultiItemMatchedBankTx(idx, newId)
                                    }
                                )
                            }
                        }
                    }

                    // Clean Borderless Non-Overlapping Footer Summary Card
                    val hasMixedMulti = multiItems.any { it.isIncome } && multiItems.any { !it.isIncome }
                    val multiIncomeSum = multiItems.filter { it.isIncome }.sumOf { it.totalAmountToman }
                    val multiExpenseSum = multiItems.filter { !it.isIncome }.sumOf { it.totalAmountToman }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(OrosiSurfaceContainer)
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                            .testTag("voice_manual_amount_input"),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (targetTx != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "عنوان ترکیبی ثبت‌شده:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = OrosiOnSurfaceVariant
                                )
                                Text(
                                    text = voiceState.purposeCategory,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                    color = NishapurTurquoiseSoft,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else if (hasMixedMulti) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(OrosiSurfaceContainer)
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(NishapurTurquoiseBright)
                                    )
                                    Text(
                                        text = "دریافتی",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = NishapurTurquoiseSoft,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    OrosiAmountText(
                                        amountToman = multiIncomeSum,
                                        color = AlabasterSilk,
                                        numberStyle = MaterialTheme.typography.labelLarge,
                                        numberFontWeight = FontWeight.ExtraBold,
                                        unitStyle = MaterialTheme.typography.labelSmall,
                                        unitColor = NishapurTurquoiseSoft.copy(alpha = 0.85f),
                                        isIncome = true,
                                        showUnit = false
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(18.dp)
                                        .background(OrosiHairline)
                                )

                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(RubyRoseSoft)
                                    )
                                    Text(
                                        text = "پرداختی",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = RubyRoseSoft,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    OrosiAmountText(
                                        amountToman = multiExpenseSum,
                                        color = AlabasterSilk,
                                        numberStyle = MaterialTheme.typography.labelLarge,
                                        numberFontWeight = FontWeight.ExtraBold,
                                        unitStyle = MaterialTheme.typography.labelSmall,
                                        unitColor = RubyRoseSoft.copy(alpha = 0.85f),
                                        showUnit = false
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "جمع کل مبالغ:",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = OrosiOnSurfaceVariant
                                )
                                OrosiAmountText(
                                    amountToman = if (multiItems.all { it.isIncome }) multiIncomeSum else voiceState.totalAmountToman,
                                    color = if (multiItems.all { it.isIncome }) NishapurTurquoiseSoft else RubyRoseSoft,
                                    numberStyle = MaterialTheme.typography.titleMedium,
                                    numberFontWeight = FontWeight.ExtraBold,
                                    unitStyle = MaterialTheme.typography.labelSmall,
                                    unitColor = OrosiOnSurfaceVariant,
                                    isIncome = multiItems.all { it.isIncome }
                                )
                            }
                        }
                    }
                } else {
                    val isSingleCommitmentCard = targetTx == null && voiceState.commitmentKind != null
                    val singleCardBg = if (isSingleCommitmentCard) {
                        Color(0xFF231639) // Distinct filled Royal Amethyst surface for commitments
                    } else {
                        OrosiSurfaceContainer // Calm neutral borderless surface for transactions
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(singleCardBg)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (targetTx == null) {
                            val isSingleCommitment = voiceState.commitmentKind != null
                            val singleCommDateShort = formatVoiceCommitmentDateShort(
                                dueDateHint = voiceState.dueDateHint,
                                explicitJalaliDate = voiceState.explicitJalaliDate,
                                eventDayOffset = voiceState.eventDayOffset
                            )
                            val singleDestLabel = when {
                                isSingleCommitment && singleCommDateShort.isNotBlank() ->
                                    "تعهد • ${voiceState.commitmentSubLabel.ifBlank { if (voiceState.isIncome) "طلب" else "بدهی" }} • $singleCommDateShort"
                                isSingleCommitment && voiceState.commitmentSubLabel.isNotBlank() ->
                                    "ثبت در تعهدات • ${voiceState.commitmentSubLabel}"
                                isSingleCommitment -> "ثبت در بخش تعهدات (بدهی و طلب)"
                                voiceState.explicitJalaliDate.isNotBlank() ->
                                    "تراکنش • ${JalaliCalendarUtils.stripYearFromJalaliDate(voiceState.explicitJalaliDate)}"
                                voiceState.eventDayOffset == -1 -> "تراکنش • تاریخ: دیروز"
                                voiceState.eventDayOffset == -2 -> "تراکنش • تاریخ: پریروز"
                                voiceState.eventDayOffset < -2 ->
                                    "تراکنش • ${PersianNumberUtils.toPersianDigits((-voiceState.eventDayOffset).toString())} روز پیش"
                                else -> "مشخصات تراکنش (قابل ویرایش)"
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("recognized_speech_text"),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = singleDestLabel,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (isSingleCommitment) RoyalAmethystSoft else NishapurTurquoiseSoft,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (voiceState.isIncome) NishapurTurquoiseDeep.copy(alpha = 0.72f) else RubyRose.copy(alpha = 0.22f))
                                        .padding(horizontal = 9.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = when {
                                            isSingleCommitment && voiceState.isIncome -> "تعهد: طلب"
                                            isSingleCommitment -> "تعهد: بدهی"
                                            voiceState.isIncome -> "دریافتی"
                                            else -> "پرداختی"
                                        },
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                        color = if (voiceState.isIncome) NishapurTurquoiseSoft else RubyRoseSoft
                                    )
                                }
                            }

                            var singleAmountField by remember(voiceState.manualAmountText) {
                                val formatted = PersianNumberUtils.formatLiveAmountInput(voiceState.manualAmountText)
                                mutableStateOf(
                                    TextFieldValue(
                                        text = formatted,
                                        selection = TextRange(formatted.length)
                                    )
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                OutlinedTextField(
                                    value = voiceState.purposeCategory,
                                    onValueChange = onPurposeChange,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = AlabasterSilk
                                    ),
                                    label = {
                                        Text(
                                            text = "عنوان / بابت",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    placeholder = {
                                        Text(
                                            text = if (voiceState.isIncome) "مثلاً: حقوق، هدیه..." else "مثلاً: نانوایی، پفک...",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1.15f)
                                        .testTag("voice_editable_title_input"),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = NishapurTurquoiseBright,
                                        unfocusedBorderColor = PersianLapisLight.copy(alpha = 0.32f),
                                        focusedContainerColor = OrosiSurfaceContainer,
                                        unfocusedContainerColor = OrosiSurfaceLow
                                    )
                                )

                                OutlinedTextField(
                                    value = singleAmountField,
                                    onValueChange = { newVal ->
                                        val formattedVal = formatAmountTextFieldValue(newVal)
                                        singleAmountField = formattedVal
                                        onManualAmountChange(formattedVal.text)
                                    },
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = AlabasterSilk
                                    ),
                                    label = {
                                        Text(
                                            text = "مبلغ (تومان)",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("voice_manual_amount_input"),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = SaffronGoldBright,
                                        unfocusedBorderColor = PersianLapisLight.copy(alpha = 0.32f),
                                        focusedContainerColor = OrosiSurfaceContainer,
                                        unfocusedContainerColor = OrosiSurfaceLow
                                    )
                                )
                            }

                            if (voiceState.totalAmountToman in 1_000L..999_000L ||
                                (voiceState.totalAmountToman >= 1_000_000L && voiceState.totalAmountToman % 1_000L == 0L)
                            ) {
                                val isThousandScale = voiceState.totalAmountToman in 1_000L..999_000L
                                Surface(
                                    onClick = { onToggleSingleScale() },
                                    shape = RoundedCornerShape(12.dp),
                                    color = SaffronGold.copy(alpha = 0.20f),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.2.dp,
                                        SaffronGoldBright.copy(alpha = 0.72f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 42.dp)
                                        .testTag("voice_single_scale_toggle")
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SwapVert,
                                            contentDescription = null,
                                            tint = SaffronGoldLight,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = PersianNumberUtils.formatScaleToggleLabel(isThousandScale),
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                            color = SaffronGoldLight,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            VoiceDestinationControlSection(
                                isCommitment = isSingleCommitment,
                                isIncome = voiceState.isIncome,
                                commitmentCategory = voiceState.commitmentCategory,
                                dueDateHint = voiceState.dueDateHint,
                                testTagPrefix = "single",
                                onToggleDestination = onToggleSingleDestination,
                                onSelectCommitmentCategory = onSetSingleCommitmentCategory
                            )

                            if (!isSingleCommitment) {
                                VoiceBankSmsMatchStrip(
                                    matchResult = voiceState.singleAutoMatch,
                                    activeMatchedTxId = voiceState.singleMatchedBankTxId,
                                    testTagSuffix = "single",
                                    onSetMatchedTxId = onSetSingleMatchedBankTx
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("recognized_speech_text"),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "عنوان / بابت:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OrosiOnSurfaceVariant
                                )
                                Text(
                                    text = voiceState.purposeCategory.ifBlank { "در انتظار ورود جمله..." },
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                    color = if (voiceState.purposeCategory.isNotBlank()) NishapurTurquoiseSoft else OrosiOnSurfaceVariant
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("voice_manual_amount_input"),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "مبلغ قفل‌شده بانک:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OrosiOnSurfaceVariant
                                )
                                OrosiAmountText(
                                    amountToman = voiceState.totalAmountToman,
                                    color = SaffronGoldLight,
                                    numberStyle = MaterialTheme.typography.titleSmall,
                                    numberFontWeight = FontWeight.ExtraBold,
                                    unitStyle = MaterialTheme.typography.labelSmall,
                                    unitColor = SaffronGoldLight.copy(alpha = 0.85f),
                                    isIncome = targetTx.type == "INCOME"
                                )
                            }
                        }
                    }
                }
            }

            val hasBankMismatch = targetTx != null && voiceState.hasBankAmountMismatch
            if (hasBankMismatch) {
                val spokenFmt = PersianNumberUtils.formatToman(voiceState.conflictingSpokenAmountToman, showUnit = true)
                val bankFmt = PersianNumberUtils.formatToman(targetTx!!.totalAmountToman, showUnit = true)
                val diffFmt = PersianNumberUtils.formatToman(
                    kotlin.math.abs(voiceState.conflictingSpokenAmountToman - targetTx.totalAmountToman),
                    showUnit = true
                )
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(RubyRose.copy(alpha = 0.22f))
                        .border(1.dp, RubyRoseSoft, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .testTag("voice_bank_amount_mismatch_warning")
                ) {
                    Text(
                        text = "⚠️ عدم تطابق مبلغ با پیامک بانک: مبلغ گفته‌شده ($spokenFmt) با مبلغ پیامک بانک ($bankFmt) به اندازه $diffFmt اختلاف دارد. لطفاً مبلغ را اصلاح کنید تا ثبت فعال شود.",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = RubyRoseSoft
                    )
                }
            }

            if (targetTx == null && voiceState.hasSummaryTotalMismatch && voiceState.statedSummaryHeaderToman > 0L) {
                val statedFmt = PersianNumberUtils.formatToman(voiceState.statedSummaryHeaderToman, showUnit = true)
                val itemsSumFmt = PersianNumberUtils.formatToman(voiceState.totalAmountToman, showUnit = true)
                val diffFmt = PersianNumberUtils.formatToman(
                    kotlin.math.abs(voiceState.totalAmountToman - voiceState.statedSummaryHeaderToman),
                    showUnit = true
                )
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SaffronGold.copy(alpha = 0.18f))
                        .border(1.dp, SaffronGoldBright, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .testTag("voice_summary_total_mismatch_warning")
                ) {
                    Text(
                        text = "⚠️ هشدار مغایرت سرجمع: در ابتدای جمله سرجمع را $statedFmt گفتید، اما جمع ریز موارد تفکیک‌شده $itemsSumFmt است ($diffFmt اختلاف). در صورت نیاز مبالغ بالا را ویرایش کنید.",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = SaffronGoldLight
                    )
                }
            }

            val canConfirm = !hasBankMismatch &&
                (targetTx != null || voiceState.totalAmountToman > 0L) &&
                (voiceState.purposeCategory.isNotBlank() || (targetTx == null && voiceState.isIncome && voiceState.totalAmountToman > 0L))
            if (!canConfirm && !hasBankMismatch) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (targetTx == null) {
                        "جمله تراکنش را همراه با مبلغ بگویید یا بنویسید (مثلاً: ۷۰ تومن نانوایی)"
                    } else {
                        "بگویید یا بنویسید این تراکنش بابت چه چیزی بود"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = SaffronGoldLight,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            val confirmButtonLabel = if (isMultiNewMode) {
                val commCount = multiItems.count { it.isCommitment }
                val linkedSmsCount = multiItems.indices.count { i ->
                    !multiItems[i].isCommitment && voiceState.multiMatchedBankTxIds.getOrNull(i) != null
                }
                val cashCount = (multiItems.size - commCount - linkedSmsCount).coerceAtLeast(0)
                when {
                    linkedSmsCount > 0 && cashCount == 0 && commCount == 0 ->
                        "تأیید و تکمیل ${PersianNumberUtils.toPersianDigits(linkedSmsCount.toString())} پیامک بانکی"
                    linkedSmsCount > 0 -> {
                        val parts = buildList {
                            add("${PersianNumberUtils.toPersianDigits(linkedSmsCount.toString())} پیامک")
                            if (cashCount > 0) add("${PersianNumberUtils.toPersianDigits(cashCount.toString())} نقدی")
                            if (commCount > 0) add("${PersianNumberUtils.toPersianDigits(commCount.toString())} تعهد")
                        }
                        "تأیید و ثبت (${parts.joinToString(" و ")})"
                    }
                    commCount == multiItems.size ->
                        "تأیید و ثبت (${PersianNumberUtils.toPersianDigits(commCount.toString())} تعهد)"
                    commCount > 0 ->
                        "تأیید و ثبت (${PersianNumberUtils.toPersianDigits(cashCount.toString())} تراکنش و ${PersianNumberUtils.toPersianDigits(commCount.toString())} تعهد)"
                    else ->
                        "تأیید و ثبت (${PersianNumberUtils.toPersianDigits(multiItems.size.toString())} تراکنش)"
                }
            } else if (targetTx == null && voiceState.commitmentKind != null) {
                "تأیید و ثبت در تعهدات"
            } else if (targetTx == null && voiceState.singleMatchedBankTxId != null) {
                val matchedBank = when (voiceState.singleMatchedBankTxId) {
                    voiceState.singleAutoMatch?.matchedCandidate?.id -> voiceState.singleAutoMatch.matchedCandidate.bankName
                    voiceState.singleAutoMatch?.suggestedCandidate?.id -> voiceState.singleAutoMatch.suggestedCandidate.bankName
                    else -> ""
                }
                if (matchedBank.isNotBlank()) "تأیید و تکمیل پیامک $matchedBank" else "تأیید و تکمیل پیامک بانک"
            } else {
                "تأیید و ثبت"
            }

            // 3. Confirm Button: "تأیید و ثبت" or "تأیید و ثبت (۲ تراکنش)"
            Button(
                onClick = onConfirmVoiceEntry,
                enabled = canConfirm,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NishapurTurquoise,
                    contentColor = AlabasterSilk,
                    disabledContainerColor = OrosiSurfaceContainer,
                    disabledContentColor = OrosiOnSurfaceVariant
                ),
                contentPadding = PaddingValues(vertical = 15.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (canConfirm) NishapurTurquoiseBright else OrosiHairline,
                        RoundedCornerShape(18.dp)
                    )
                    .testTag("confirm_voice_entry_button")
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = confirmButtonLabel,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
                )
            }
        }
    }
}

@Composable
private fun VoiceBankSmsMatchStrip(
    matchResult: VoiceSmsMatchResult?,
    activeMatchedTxId: Long?,
    testTagSuffix: String,
    onSetMatchedTxId: (Long?) -> Unit
) {
    val autoCandidate = matchResult?.matchedCandidate
    val suggestedCandidate = matchResult?.suggestedCandidate
    val linkedCandidate = when {
        activeMatchedTxId == null -> null
        autoCandidate?.id == activeMatchedTxId -> autoCandidate
        suggestedCandidate?.id == activeMatchedTxId -> suggestedCandidate
        else -> null
    }
    val todayDate = remember { JalaliCalendarUtils.fromTimestamp(System.currentTimeMillis()).dateWithYear }

    fun formatCandidateWhen(cand: UntaggedSmsCandidate): String {
        return if (cand.jalaliDate.isNotBlank() && cand.jalaliDate != todayDate) {
            "${JalaliCalendarUtils.stripYearFromJalaliDate(cand.jalaliDate)} • ${cand.timeStr}"
        } else {
            cand.timeStr
        }
    }

    if (linkedCandidate != null) {
        val isExact = matchResult?.isExactAutoMatch == true && autoCandidate?.id == linkedCandidate.id
        val isApproxAuto = matchResult?.isApproximateAutoMatch == true && autoCandidate?.id == linkedCandidate.id
        val whenStr = formatCandidateWhen(linkedCandidate)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(NishapurTurquoiseDeep.copy(alpha = 0.45f))
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .testTag("voice_sms_match_linked_$testTagSuffix"),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Row 1: Full-width unwrapped status text (ZERO ellipsis/truncation)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Sms,
                    contentDescription = null,
                    tint = NishapurTurquoiseBright,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(16.dp)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = if (isApproxAuto) {
                            "تطبیق تقریبی با پیامک ${linkedCandidate.bankName} • $whenStr"
                        } else {
                            "تطبیق با پیامک ${linkedCandidate.bankName} • $whenStr"
                        },
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            lineHeight = 18.sp
                        ),
                        color = NishapurTurquoiseSoft
                    )
                    if (!isExact) {
                        Text(
                            text = "مبلغ دقیق بانک (${PersianNumberUtils.formatToman(linkedCandidate.amountToman, showUnit = true)}) حفظ می‌شود",
                            style = MaterialTheme.typography.labelSmall.copy(lineHeight = 17.sp),
                            color = AlabasterSilk.copy(alpha = 0.88f)
                        )
                    }
                }
            }

            // Row 2: Dedicated action bar (never squeezes text above)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Surface(
                    onClick = { onSetMatchedTxId(null) },
                    shape = RoundedCornerShape(10.dp),
                    color = OrosiSurfaceHigh,
                    border = androidx.compose.foundation.BorderStroke(
                        1.1.dp,
                        PersianLapisLight.copy(alpha = 0.50f)
                    ),
                    modifier = Modifier
                        .heightIn(min = 34.dp)
                        .testTag("voice_unlink_sms_$testTagSuffix")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "ثبت نقدی جدا",
                            tint = OrosiOnSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "ثبت نقدی جدا",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = AlabasterSilk,
                            maxLines = 1
                        )
                    }
                }
            }
        }
        return
    }

    val relinkOrSuggest = autoCandidate ?: suggestedCandidate ?: return
    val isRelink = autoCandidate != null
    val whenStr = formatCandidateWhen(relinkOrSuggest)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(OrosiSurfaceLow.copy(alpha = 0.85f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = if (isRelink) {
                "ثبت به عنوان تراکنش نقدی جدید"
            } else {
                "ثبت به عنوان نقدی جدید • پیامک بانکی اخیر پیدا شد:"
            },
            style = MaterialTheme.typography.labelSmall.copy(lineHeight = 17.sp),
            color = OrosiOnSurfaceVariant
        )
        Surface(
            onClick = { onSetMatchedTxId(relinkOrSuggest.id) },
            shape = RoundedCornerShape(10.dp),
            color = NishapurTurquoiseDeep.copy(alpha = 0.72f),
            border = androidx.compose.foundation.BorderStroke(
                1.2.dp,
                NishapurTurquoiseBright.copy(alpha = 0.75f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .testTag("voice_link_suggested_sms_$testTagSuffix")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Sms,
                    contentDescription = null,
                    tint = NishapurTurquoiseBright,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isRelink) {
                        "اتصال مجدد به پیامک ${relinkOrSuggest.bankName} (${PersianNumberUtils.formatToman(relinkOrSuggest.amountToman)} • $whenStr)"
                    } else {
                        "اتصال به پیامک ${relinkOrSuggest.bankName} (${PersianNumberUtils.formatToman(relinkOrSuggest.amountToman)} • $whenStr)"
                    },
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        lineHeight = 18.sp
                    ),
                    color = NishapurTurquoiseSoft
                )
            }
        }
    }
}

private fun formatVoiceCommitmentDateShort(
    dueDateHint: String,
    explicitJalaliDate: String,
    eventDayOffset: Int
): String {
    val cleanHint = dueDateHint.trim()
    val resolvedDate = when {
        explicitJalaliDate.isNotBlank() -> JalaliCalendarUtils.stripYearFromJalaliDate(explicitJalaliDate)
        cleanHint.isNotBlank() -> JalaliCalendarUtils.stripYearFromJalaliDate(
            JalaliCalendarUtils.resolveRelativeJalaliDate(cleanHint)
        )
        eventDayOffset != 0 -> JalaliCalendarUtils.stripYearFromJalaliDate(
            JalaliCalendarUtils.timestampToJalali(System.currentTimeMillis() + eventDayOffset * 86_400_000L).dateWithYear
        )
        else -> ""
    }
    return when {
        cleanHint.isNotBlank() && resolvedDate.isNotBlank() && !resolvedDate.startsWith(cleanHint) ->
            "$cleanHint ($resolvedDate)"
        cleanHint.isNotBlank() -> cleanHint
        resolvedDate.isNotBlank() -> resolvedDate
        else -> ""
    }
}

private data class CashflowBadgeVisuals(
    val label: String,
    val bg: Color,
    val dotColor: Color,
    val textColor: Color
)

@Composable
private fun VoiceDestinationControlSection(
    isCommitment: Boolean,
    isIncome: Boolean,
    commitmentCategory: String,
    dueDateHint: String,
    testTagPrefix: String,
    onToggleDestination: () -> Unit,
    onSelectCommitmentCategory: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!isCommitment) {
            // Clickable button ONLY gets a luminous border
            Surface(
                onClick = onToggleDestination,
                shape = RoundedCornerShape(12.dp),
                color = RoyalAmethyst.copy(alpha = 0.24f),
                border = androidx.compose.foundation.BorderStroke(
                    1.2.dp,
                    RoyalAmethystSoft.copy(alpha = 0.78f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 42.dp)
                    .testTag("voice_dest_toggle_$testTagPrefix")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = RoyalAmethystSoft,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ثبت در بخش تعهدات (قسط، چک یا بدهی)",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = AlabasterSilk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else {
            val destSummary = if (dueDateHint.isNotBlank()) {
                "مقصد: بخش تعهدات ✓ • $dueDateHint"
            } else {
                "مقصد: بخش تعهدات ✓"
            }
            // Borderless status strip; ONLY the action button on the left has a bright interactive border
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.24f))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = RoyalAmethystSoft,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = destSummary,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = AlabasterSilk.copy(alpha = 0.90f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    onClick = onToggleDestination,
                    shape = RoundedCornerShape(10.dp),
                    color = PersianLapis.copy(alpha = 0.85f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.2.dp,
                        PersianLapisLight.copy(alpha = 0.78f)
                    ),
                    modifier = Modifier
                        .heightIn(min = 34.dp)
                        .testTag("voice_dest_toggle_$testTagPrefix")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "تغییر به تراکنش",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = AlabasterSilk,
                            maxLines = 1
                        )
                    }
                }
            }

            if (!isIncome) {
                val categoryOptions = listOf(
                    "LOAN_INSTALLMENT" to "وام و قسط",
                    "SAYYAD_ISSUED" to "چک صیادی",
                    "DONG" to "بدهی و طلب"
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.24f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categoryOptions.forEach { (catKey, label) ->
                        val selected = commitmentCategory == catKey
                        Surface(
                            onClick = { onSelectCommitmentCategory(catKey) },
                            shape = RoundedCornerShape(9.dp),
                            color = if (selected) RoyalAmethyst.copy(alpha = 0.62f) else OrosiSurfaceHigh.copy(alpha = 0.45f),
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (selected) 1.3.dp else 0.9.dp,
                                color = if (selected) RoyalAmethystSoft else PersianLapisLight.copy(alpha = 0.24f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 36.dp)
                                .testTag("voice_comm_cat_${testTagPrefix}_$catKey")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold
                                    ),
                                    color = if (selected) AlabasterSilk else OrosiOnSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeroMathBreakdownBottomSheet(
    settings: AppSettingsEntity,
    transactions: List<TransactionEntity>,
    reservedCommitmentsToman: Long,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isMoreDetailsExpanded by remember { mutableStateOf(false) }

    val txSnapshots = remember(transactions) {
        transactions.map { tx ->
            CashflowTxSnapshot(
                id = tx.id,
                type = tx.type,
                totalAmountToman = tx.totalAmountToman,
                userShareToman = tx.userShareToman,
                bankName = tx.shortBankName,
                timestamp = tx.timestamp
            )
        }
    }

    val initialOffset = remember(txSnapshots, settings.paydayStartDay) {
        OrosiCashflowAnalyzer.resolveInitialPaydayCycleOffset(
            transactions = txSnapshots,
            paydayStartDay = settings.paydayStartDay
        )
    }
    var cycleOffsetMonths by remember(initialOffset) { mutableIntStateOf(initialOffset) }
    val canGoNextCycle = cycleOffsetMonths < 0

    val summary = remember(txSnapshots, settings.paydayStartDay, reservedCommitmentsToman, cycleOffsetMonths) {
        OrosiCashflowAnalyzer.analyzeCurrentPaydayCycle(
            transactions = txSnapshots,
            paydayStartDay = settings.paydayStartDay,
            reservedCommitmentsToman = reservedCommitmentsToman,
            cycleOffsetMonths = cycleOffsetMonths
        )
    }

    val badgeVisuals = when (summary.healthStatus) {
        CashflowHealthStatus.POSITIVE -> CashflowBadgeVisuals(
            label = "تراز مثبت و متعادل ✓",
            bg = NishapurTurquoiseDeep.copy(alpha = 0.65f),
            dotColor = NishapurTurquoiseBright,
            textColor = NishapurTurquoiseSoft
        )
        CashflowHealthStatus.BALANCED -> CashflowBadgeVisuals(
            label = "تراز سربه‌سر",
            bg = SaffronGold.copy(alpha = 0.18f),
            dotColor = SaffronGoldBright,
            textColor = SaffronGoldLight
        )
        CashflowHealthStatus.DEFICIT -> CashflowBadgeVisuals(
            label = "کسری تراز ماه",
            bg = RubyRose.copy(alpha = 0.20f),
            dotColor = RubyRoseSoft,
            textColor = RubyRoseSoft
        )
    }

    val netColor = when (summary.healthStatus) {
        CashflowHealthStatus.POSITIVE -> NishapurTurquoiseSoft
        CashflowHealthStatus.BALANCED -> SaffronGoldLight
        CashflowHealthStatus.DEFICIT -> RubyRoseSoft
    }

    val moreDetailsSubtitle = if (summary.excludedSelfTransferPairs > 0) {
        "قسط و چک، موجودی کل و ${PersianNumberUtils.toPersianDigits(summary.excludedSelfTransferPairs.toString())} انتقال خودی"
    } else {
        "قسط و چک و موجودی کل حساب‌ها"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = OrosiSurfaceLow,
        contentColor = AlabasterSilk
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 32.dp)
                .testTag("hero_math_sheet"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Serene Header + Borderless Tonal Status Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ترازنامه ماه حقوقی",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = AlabasterSilk,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(badgeVisuals.bg)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("hero_math_status_badge")
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(badgeVisuals.dotColor)
                    )
                    Text(
                        text = badgeVisuals.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = badgeVisuals.textColor,
                        maxLines = 1
                    )
                }
            }

            // 1B. Calm Payday Cycle Switcher (< Prev Cycle | Label | Next Cycle >) matching TransactionsScreen
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(OrosiSurfaceContainer)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(OrosiSurfaceHigh)
                    ) {
                        IconButton(
                            onClick = { cycleOffsetMonths = (cycleOffsetMonths - 1).coerceAtLeast(-24) },
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("hero_math_prev_cycle_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "ماه حقوقی قبل",
                                tint = AlabasterSilk,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EventNote,
                            contentDescription = null,
                            tint = NishapurTurquoiseSoft,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = summary.cycleLabel,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = AlabasterSilk,
                            modifier = Modifier.testTag("hero_math_cycle_label")
                        )
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                if (canGoNextCycle) OrosiSurfaceHigh else OrosiSurfaceHigh.copy(alpha = 0.35f)
                            )
                    ) {
                        IconButton(
                            onClick = { if (canGoNextCycle) cycleOffsetMonths += 1 },
                            enabled = canGoNextCycle,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("hero_math_next_cycle_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronLeft,
                                contentDescription = "ماه حقوقی بعد",
                                tint = if (canGoNextCycle) AlabasterSilk else OrosiOnSurfaceVariant.copy(alpha = 0.30f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                if (initialOffset == -1 && cycleOffsetMonths == -1) {
                    Text(
                        text = "در ماه جاری هنوز تراکنشی ثبت نشده؛ تراز ماه قبل نمایش داده شده است.",
                        style = MaterialTheme.typography.labelSmall,
                        color = OrosiOnSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("hero_math_auto_prev_hint")
                    )
                }
            }

            // 2. Calm Minimalist Primary View: Inflow & Outflow (2-Column Borderless KPI Cards)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(NishapurTurquoiseDeep.copy(alpha = 0.52f))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(NishapurTurquoiseBright.copy(alpha = 0.20f))
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ArrowDownward,
                                contentDescription = null,
                                tint = NishapurTurquoiseBright,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            text = "کل ورودی ماه",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = NishapurTurquoiseSoft,
                            maxLines = 1
                        )
                    }
                    OrosiAmountText(
                        amountToman = summary.totalIncomeToman,
                        color = AlabasterSilk,
                        numberStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                        numberFontWeight = FontWeight.ExtraBold,
                        unitStyle = MaterialTheme.typography.labelSmall,
                        unitColor = NishapurTurquoiseSoft,
                        isIncome = true,
                        stackedUnit = true
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(RubyRose.copy(alpha = 0.18f))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(RubyRoseSoft.copy(alpha = 0.18f))
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ArrowUpward,
                                contentDescription = null,
                                tint = RubyRoseSoft,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            text = "کل خرج مصرفی",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = RubyRoseSoft,
                            maxLines = 1
                        )
                    }
                    OrosiAmountText(
                        amountToman = summary.totalExpenseToman,
                        color = AlabasterSilk,
                        numberStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                        numberFontWeight = FontWeight.ExtraBold,
                        unitStyle = MaterialTheme.typography.labelSmall,
                        unitColor = RubyRoseSoft,
                        stackedUnit = true
                    )
                }
            }

            // 3. Primary Net Cashflow Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(OrosiSurfaceContainer)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "تراز خالص ماه حقوقی",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = AlabasterSilk,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                OrosiAmountText(
                    amountToman = kotlin.math.abs(summary.netBalanceToman),
                    color = netColor,
                    numberStyle = MaterialTheme.typography.titleMedium,
                    numberFontWeight = FontWeight.ExtraBold,
                    unitStyle = MaterialTheme.typography.labelSmall,
                    unitColor = netColor.copy(alpha = 0.85f),
                    isIncome = summary.netBalanceToman > 0L,
                    prefixText = if (summary.netBalanceToman < 0L) "کسری" else null
                )
            }

            // 4. Clean Daily Safe Budget Hero Strip (No duplicate balance clutter)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(PersianLapis)
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "سهم خرجِ روزانه پیشنهادی",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = PersianLapisLight,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                OrosiAmountText(
                    amountToman = PersianNumberUtils.roundDownToCleanThousands(settings.dailySafeBudgetToman),
                    color = AlabasterSilk,
                    numberStyle = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                    numberFontWeight = FontWeight.Black,
                    unitStyle = MaterialTheme.typography.labelMedium,
                    unitColor = NishapurTurquoiseSoft
                )
            }

            // 5. Calm Collapsible "جزئیات بیشتر" Section (Instant crisp toggle without sheet anchor stutter)
            Surface(
                onClick = { isMoreDetailsExpanded = !isMoreDetailsExpanded },
                shape = RoundedCornerShape(16.dp),
                color = OrosiSurfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .testTag("hero_math_more_details_toggle")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (isMoreDetailsExpanded) "بستن جزئیات بیشتر" else "جزئیات بیشتر",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = AlabasterSilk,
                            maxLines = 1
                        )
                        Text(
                            text = moreDetailsSubtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = OrosiOnSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = if (isMoreDetailsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isMoreDetailsExpanded) "بستن جزئیات بیشتر" else "نمایش جزئیات بیشتر",
                        tint = NishapurTurquoiseSoft,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            if (isMoreDetailsExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(OrosiSurfaceContainer)
                        .padding(16.dp)
                        .testTag("hero_math_more_details_content"),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MonthlyReportAmountRow(
                        label = "قسط و چک‌های پیش‌رو (جدا از تراز)",
                        amountToman = summary.reservedCommitmentsToman,
                        valueColor = RoyalAmethystSoft
                    )

                    MonthlyReportAmountRow(
                        label = "موجودی کل حساب‌ها",
                        amountToman = settings.monthlyBalanceToman,
                        valueColor = NishapurTurquoiseSoft
                    )

                    if (summary.excludedSelfTransferPairs > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(OrosiHairline)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("self_transfer_expand_row"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "انتقال بین‌حسابی خودی (کسرشده)",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = AlabasterSilk,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${PersianNumberUtils.toPersianDigits(summary.excludedSelfTransferPairs.toString())} جفت هم‌مبلغ از ورودی و خرج کسر شد",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OrosiOnSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            OrosiAmountText(
                                amountToman = summary.excludedSelfTransferTotalToman,
                                color = OrosiOnSurfaceVariant,
                                numberStyle = MaterialTheme.typography.labelLarge,
                                numberFontWeight = FontWeight.Bold,
                                unitStyle = MaterialTheme.typography.labelSmall,
                                unitColor = OrosiOnSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(OrosiSurfaceLow)
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("self_transfer_details_list"),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            summary.excludedSelfTransferDetails.forEachIndexed { idx, pair ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = "از ${pair.fromBankName} ← به ${pair.toBankName}",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = AlabasterSilk,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = pair.dateTimeLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = OrosiOnSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    OrosiAmountText(
                                        amountToman = pair.amountToman,
                                        color = NishapurTurquoiseSoft,
                                        numberStyle = MaterialTheme.typography.labelLarge,
                                        numberFontWeight = FontWeight.Bold,
                                        unitStyle = MaterialTheme.typography.labelSmall,
                                        unitColor = NishapurTurquoiseSoft.copy(alpha = 0.8f)
                                    )
                                }
                                if (idx < summary.excludedSelfTransferDetails.lastIndex) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(OrosiHairline)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthlyReportAmountRow(
    label: String,
    amountToman: Long,
    valueColor: Color,
    isIncome: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = OrosiOnSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(10.dp))
        OrosiAmountText(
            amountToman = amountToman,
            color = valueColor,
            numberStyle = MaterialTheme.typography.titleSmall,
            numberFontWeight = FontWeight.Bold,
            unitStyle = MaterialTheme.typography.labelSmall,
            unitColor = valueColor.copy(alpha = 0.85f),
            isIncome = isIncome
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommitmentActionBottomSheet(
    commitment: CommitmentEntity,
    onSaveCommitment: (
        title: String,
        counterparty: String,
        amountToman: Long,
        dueDateStr: String,
        isSettled: Boolean
    ) -> Unit,
    onDeleteCommitment: () -> Unit,
    onDismiss: () -> Unit
) {
    var titleStr by remember(commitment) { mutableStateOf(commitment.title) }
    var counterpartyStr by remember(commitment) { mutableStateOf(commitment.counterparty) }
    var amountField by remember(commitment) {
        val formatted = PersianNumberUtils.formatToman(commitment.amountToman)
        mutableStateOf(TextFieldValue(text = formatted, selection = TextRange(formatted.length)))
    }
    var dueDateStr by remember(commitment) { mutableStateOf(commitment.dueDateStr) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = OrosiSurfaceLow,
        contentColor = AlabasterSilk
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .testTag("commitment_action_sheet")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "مدیریت و تسویه تعهد مالی",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = AlabasterSilk
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "بستن",
                        tint = OrosiOnSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = {
                    val parsedAmount = PersianNumberUtils.parseAmountInputToLong(amountField.text)
                        ?: commitment.amountToman
                    onSaveCommitment(
                        titleStr,
                        counterpartyStr,
                        parsedAmount,
                        dueDateStr,
                        !commitment.isSettled
                    )
                },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (commitment.isSettled) PersianLapis else NishapurTurquoise,
                    contentColor = AlabasterSilk
                ),
                contentPadding = PaddingValues(vertical = 16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NishapurTurquoiseBright, RoundedCornerShape(16.dp))
                    .testTag("mark_commitment_settled_button")
            ) {
                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (commitment.isSettled) "بازگردانی به وضعیت فعال" else "پاس شد / تسویه شد ✓",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = titleStr,
                onValueChange = { titleStr = it },
                label = { Text("عنوان چک، قسط یا دُنگ", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = amountField,
                onValueChange = { amountField = formatAmountTextFieldValue(it) },
                label = { Text("مبلغ (تومان)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = dueDateStr,
                onValueChange = { dueDateStr = PersianNumberUtils.toPersianDigits(it) },
                label = { Text("تاریخ سررسید", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = counterpartyStr,
                onValueChange = { counterpartyStr = it },
                label = { Text("طرف حساب / بانک", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDeleteCommitment,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("delete_commitment_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = null,
                        tint = RubyRoseSoft
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "حذف",
                        color = RubyRoseSoft,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Button(
                    onClick = {
                        val parsedAmount = PersianNumberUtils.parseAmountInputToLong(amountField.text)
                            ?: commitment.amountToman
                        onSaveCommitment(
                            titleStr,
                            counterpartyStr,
                            parsedAmount,
                            dueDateStr,
                            commitment.isSettled
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RoyalAmethyst,
                        contentColor = AlabasterSilk
                    ),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    modifier = Modifier
                        .weight(1.6f)
                        .testTag("save_commitment_edits_button")
                ) {
                    Text(
                        text = "ذخیره ویرایش",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

/**
 * 4. Jibeman-Style Transaction Details Modal (`TransactionDetailAndEditBottomSheet`):
 * - Top 4 badges: Direction (`دریافتی` / `پرداختی`), Bank (`بلو`, `سپه`, etc.), Jalali Date (`۲۲ آبان ۱۴۰۵`), and Time (`۱۲:۲۱`) + a top-left Trash icon to delete the transaction.
 * - `مبلغ (تومان)` AND `مانده حساب پس از تراکنش (تومان)`.
 * - `توضیحات / بابت` field with a direct Microphone button right inside the field + collapsible "مشاهده متن اصلی پیامک بانک".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailAndEditBottomSheet(
    transaction: TransactionEntity,
    onQuickVoiceRetag: (TransactionEntity) -> Unit,
    onDeleteTransaction: (TransactionEntity) -> Unit,
    onSaveEdits: (
        amountToman: Long,
        balanceAfterToman: Long?,
        weekdayDate: String,
        timeStr: String,
        cardInfo: String,
        purpose: String,
        storeName: String,
        specialTagMode: String
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val jalaliInfo = remember(transaction.timestamp) {
        JalaliCalendarUtils.fromTimestamp(transaction.timestamp)
    }

    var amountField by remember(transaction) {
        val formatted = PersianNumberUtils.formatToman(
            transaction.userShareToman.takeIf { it > 0L } ?: transaction.totalAmountToman
        )
        mutableStateOf(
            TextFieldValue(
                text = formatted,
                selection = TextRange(formatted.length)
            )
        )
    }
    var balanceAfterField by remember(transaction) {
        val formatted = transaction.balanceAfterToman?.let { PersianNumberUtils.formatToman(it) } ?: ""
        mutableStateOf(
            TextFieldValue(
                text = formatted,
                selection = TextRange(formatted.length)
            )
        )
    }
    var purposeStr by remember(transaction) {
        val initialPurpose = if (transaction.isUntagged ||
            transaction.purpose == "نیازمند تکمیل صوتی" ||
            transaction.purpose == "برداشت کارتی / خرید" ||
            transaction.purpose == "واریز به حساب"
        ) {
            ""
        } else {
            transaction.purpose
        }
        mutableStateOf(initialPurpose)
    }
    var storeStr by remember(transaction) {
        mutableStateOf(
            if (transaction.isUntagged || transaction.storeName == "نامشخص") {
                transaction.shortBankName
            } else {
                transaction.storeName
            }
        )
    }
    var specialTagMode by remember(transaction) {
        mutableStateOf("NORMAL")
    }
    var showRawSms by remember { mutableStateOf(false) }
    var confirmDeleteClicked by remember(transaction.id) { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isIncome = transaction.type == "INCOME"
    val maxModalHeight = (LocalConfiguration.current.screenHeightDp * 0.75f).dp

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = OrosiSurfaceLow,
        contentColor = AlabasterSilk
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxModalHeight)
                .then(
                    if (showRawSms) Modifier.verticalScroll(rememberScrollState()) else Modifier
                )
                .padding(horizontal = 18.dp)
                .padding(bottom = 20.dp)
                .testTag("transaction_detail_sheet")
        ) {
            // Compact Header Row: Title (Right in RTL) + 2-Step Trash Icon & Close Icon (Left in RTL)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "جزئیات تراکنش",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = AlabasterSilk
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!confirmDeleteClicked) {
                        IconButton(
                            onClick = { confirmDeleteClicked = true },
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(RubyRose.copy(alpha = 0.16f))
                                .testTag("delete_transaction_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "حذف تراکنش",
                                tint = RubyRoseSoft,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(RubyRose)
                                .border(1.dp, RubyRoseSoft, RoundedCornerShape(50))
                                .clickable { onDeleteTransaction(transaction) }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                .testTag("delete_transaction_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "تأیید حذف",
                                tint = AlabasterSilk,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "تأیید حذف؟",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = AlabasterSilk
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "بستن",
                            tint = OrosiOnSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Compact Single-Row Hero Summary Header: Bank Logo + Name/Date/Time on one side, Direction Badge + Live Amount on the other
            val livePreviewAmount = PersianNumberUtils.parseAmountInputToLong(amountField.text)
                ?: transaction.totalAmountToman
            val formattedHeroAmount = if (isIncome) {
                PersianNumberUtils.formatIncomeToman(livePreviewAmount, showUnit = true)
            } else {
                PersianNumberUtils.formatToman(livePreviewAmount, showUnit = true)
            }
            val bankBadgeLabel = if (transaction.shortBankName == "نقدی / دستی") {
                "نقدی / دستی"
            } else {
                "بانک ${transaction.shortBankName}"
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                PersianLapis,
                                OrosiSurfaceHigh
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = if (isIncome) NishapurTurquoiseBright.copy(alpha = 0.55f) else PersianLapisLight.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    BankLogoAvatar(
                        bankName = transaction.shortBankName,
                        size = 38.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = bankBadgeLabel,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = AlabasterSilk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            JibemanDetailBadge(
                                text = if (isIncome) "دریافتی" else "پرداختی",
                                bgColor = if (isIncome) NishapurTurquoiseDeep else RubyRose.copy(alpha = 0.28f),
                                borderColor = if (isIncome) NishapurTurquoiseBright else RubyRoseSoft,
                                textColor = if (isIncome) NishapurTurquoiseSoft else RubyRoseSoft
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${jalaliInfo.dateWithYear} • ساعت ${transaction.timeStr}",
                            style = MaterialTheme.typography.labelSmall,
                            color = OrosiOnSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = formattedHeroAmount,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = if (isIncome) NishapurTurquoiseSoft else RubyRoseSoft,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Compact 2-Column Form Container (Amount & Balance After side-by-side + Purpose & Raw SMS)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(OrosiSurfaceContainer)
                    .border(1.dp, OrosiHairline, RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Row 1 (Two Columns): 1) مبلغ تراکنش (تومان)  |  2) مانده پس از تراکنش (تومان)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = amountField,
                        onValueChange = { amountField = formatAmountTextFieldValue(it) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = AlabasterSilk
                        ),
                        label = {
                            Text(
                                text = "مبلغ (تومان)",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("detail_amount_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = OrosiSurfaceLow,
                            unfocusedContainerColor = OrosiSurfaceLow,
                            focusedBorderColor = NishapurTurquoiseBright,
                            unfocusedBorderColor = OrosiHairline
                        )
                    )

                    OutlinedTextField(
                        value = balanceAfterField,
                        onValueChange = { balanceAfterField = formatAmountTextFieldValue(it) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = AlabasterSilk
                        ),
                        label = {
                            Text(
                                text = "مانده حساب (تومان)",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        placeholder = {
                            Text(
                                text = "مثلاً: ۹۸۴,۴۳۳",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("detail_balance_after_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = OrosiSurfaceLow,
                            unfocusedContainerColor = OrosiSurfaceLow,
                            focusedBorderColor = NishapurTurquoiseBright,
                            unfocusedBorderColor = OrosiHairline
                        )
                    )
                }

                // Row 2: توضیحات / بابت field with direct Microphone button inside
                OutlinedTextField(
                    value = purposeStr,
                    onValueChange = { purposeStr = it },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = AlabasterSilk
                    ),
                    label = {
                        Text(
                            text = "توضیحات / بابت (متنی یا صوتی)",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    placeholder = {
                        Text(
                            text = "مثلاً: پفک، شارژ ایرانسل، نانوایی...",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { onQuickVoiceRetag(transaction) },
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .clip(CircleShape)
                                .background(NishapurTurquoise)
                                .size(32.dp)
                                .testTag("sheet_voice_retag_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "ثبت صوتی بابت",
                                tint = AlabasterSilk,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("detail_purpose_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = OrosiSurfaceLow,
                        unfocusedContainerColor = OrosiSurfaceLow,
                        focusedBorderColor = NishapurTurquoiseBright,
                        unfocusedBorderColor = OrosiHairline
                    )
                )

                // Collapsible "مشاهده متن اصلی پیامک بانک" ONLY if rawSmsBody is present
                if (transaction.rawSmsBody.isNotBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(OrosiSurfaceLow)
                            .border(1.dp, PersianLapisLight.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showRawSms = !showRawSms }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag("toggle_raw_sms_button"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Sms,
                                    contentDescription = null,
                                    tint = PersianLapisLight,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "مشاهده متن اصلی پیامک بانک",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = PersianLapisLight
                                )
                            }
                            Icon(
                                imageVector = if (showRawSms) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = OrosiOnSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        AnimatedVisibility(visible = showRawSms) {
                            Text(
                                text = transaction.rawSmsBody,
                                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                                color = AlabasterSilk,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                                    .padding(bottom = 10.dp)
                                    .testTag("raw_sms_body_text")
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Compact Section 3: 3-Segment Pill Selector for Transaction Mode
            val modeOptions = listOf(
                "NORMAL" to "خرج عادی",
                "INSTALLMENT" to "وام و قسط",
                "DONG" to "بدهی و دُنگ"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(OrosiSurfaceContainer)
                    .border(1.dp, OrosiHairline, RoundedCornerShape(14.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                modeOptions.forEach { (modeKey, modeLabel) ->
                    val isSelected = specialTagMode == modeKey
                    val activeColor = when (modeKey) {
                        "INSTALLMENT" -> RoyalAmethyst
                        "DONG" -> NishapurTurquoise
                        else -> PersianLapis
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) activeColor else Color.Transparent)
                            .border(
                                width = if (isSelected) 1.dp else 0.dp,
                                color = if (isSelected) Color.White.copy(alpha = 0.45f) else Color.Transparent,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { specialTagMode = modeKey }
                            .padding(vertical = 8.dp, horizontal = 6.dp)
                            .testTag("tx_mode_toggle_$modeKey"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = modeLabel,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                            ),
                            color = if (isSelected) AlabasterSilk else OrosiOnSurfaceVariant,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    val parsedAmount = PersianNumberUtils.parseAmountInputToLong(amountField.text)
                        ?: transaction.totalAmountToman
                    val parsedBalanceAfter = if (balanceAfterField.text.isBlank()) {
                        null
                    } else {
                        PersianNumberUtils.parseAmountInputToLong(balanceAfterField.text)
                            ?: transaction.balanceAfterToman
                    }
                    onSaveEdits(
                        parsedAmount,
                        parsedBalanceAfter,
                        transaction.weekdayDate,
                        transaction.timeStr,
                        transaction.cardInfo,
                        purposeStr.trim(),
                        storeStr.trim().ifEmpty { transaction.shortBankName },
                        specialTagMode
                    )
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NishapurTurquoise,
                    contentColor = AlabasterSilk
                ),
                contentPadding = PaddingValues(vertical = 13.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NishapurTurquoiseBright, RoundedCornerShape(14.dp))
                    .testTag("save_transaction_edits_button")
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "ذخیره تغییرات",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold)
                )
            }
        }
    }
}

@Composable
private fun JibemanDetailBadge(
    text: String,
    bgColor: Color,
    borderColor: Color,
    textColor: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = textColor,
            maxLines = 1,
            softWrap = false
        )
    }
}

private data class InboxSmsPickerRow(
    val id: Long,
    val senderAddress: String,
    val body: String,
    val dateStr: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBankAccountBottomSheet(
    onAddAccount: (
        bankName: String,
        balanceToman: Long,
        isTankhah: Boolean,
        cardLast4: String,
        customSenderAddresses: List<String>
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) } // 0 = کارت بانکی, 1 = منبع نقدی
    var selectedBankName by remember { mutableStateOf("بلو") }
    var bankSearchQuery by remember { mutableStateOf("") }
    var accountCustomTitle by remember { mutableStateOf("") }
    var balanceField by remember { mutableStateOf(TextFieldValue("")) }
    var cardLast4 by remember { mutableStateOf("") }
    var isTankhah by remember { mutableStateOf(false) }
    var attachedSenders by remember { mutableStateOf(listOf<String>()) }
    var showSmsSenderPickerDialog by remember { mutableStateOf(false) }
    var recentInboxMessages by remember { mutableStateOf<List<InboxSmsPickerRow>>(emptyList()) }

    val defaultSendersForSelectedBank = remember(selectedBankName) {
        com.example.domain.JibemanSmsEngine.BANK_PROFILES
            .firstOrNull { it.nameFa == selectedBankName || it.nameFa.replace("\u200C", " ") == selectedBankName.replace("\u200C", " ") }
            ?.senderIds
            ?.take(3)
            .orEmpty()
    }

    val filteredBanks = remember(bankSearchQuery) {
        val q = bankSearchQuery.trim()
        if (q.isEmpty()) {
            JibemanAssetLogoCache.ALL_38_IRANIAN_BANKS
        } else {
            JibemanAssetLogoCache.ALL_38_IRANIAN_BANKS.filter { (fa, key) ->
                fa.contains(q) || key.contains(q, ignoreCase = true)
            }
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = OrosiSurfaceLow,
        contentColor = AlabasterSilk
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .testTag("add_bank_account_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AccountBalance,
                        contentDescription = null,
                        tint = NishapurTurquoiseBright
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ایجاد حساب",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = AlabasterSilk
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "بستن",
                        tint = OrosiOnSurfaceVariant
                    )
                }
            }

            // 2-Tab Selector at top: "کارت بانکی" | "منبع نقدی"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(OrosiSurfaceContainer)
                    .border(1.dp, OrosiHairline, RoundedCornerShape(14.dp))
                    .padding(4.dp)
                    .testTag("add_account_tab_bar"),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val tabs = listOf(0 to "کارت بانکی", 1 to "منبع نقدی")
                tabs.forEach { (idx, title) ->
                    val isSelected = selectedTab == idx
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) NishapurTurquoise else Color.Transparent)
                            .clickable { selectedTab = idx }
                            .padding(vertical = 10.dp)
                            .testTag("add_account_tab_$idx")
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                fontSize = 13.5.sp
                            ),
                            color = if (isSelected) AlabasterSilk else OrosiOnSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }

            if (selectedTab == 0) {
                // Tab 1: کارت بانکی
                Text(
                    text = "انتخاب بانک (۳۸ بانک و مؤسسه رسمی)",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = NishapurTurquoiseSoft
                )

                OutlinedTextField(
                    value = bankSearchQuery,
                    onValueChange = { bankSearchQuery = it },
                    label = {
                        Text(
                            text = "جستجوی نام بانک (مثلاً: بلو، سپه، ملت، پاسارگاد...)",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bank_search_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NishapurTurquoiseBright,
                        unfocusedBorderColor = OrosiHairline
                    )
                )

                // Horizontal scrollable selector of all 38 Iranian banks with official 192x192 PNG logos
                // Cards are 106.dp wide with 2-line centered text so NO bank name is ever truncated with "..."
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("iranian_banks_selector_row"),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = filteredBanks,
                        key = { it.first }
                    ) { (bankFa, _) ->
                        val isSelected = selectedBankName == bankFa
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .width(106.dp)
                                .height(98.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (isSelected) PersianLapis else OrosiSurfaceContainer
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) NishapurTurquoiseBright else OrosiHairline,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable { selectedBankName = bankFa }
                                .padding(vertical = 8.dp, horizontal = 6.dp)
                                .testTag("select_bank_$bankFa")
                        ) {
                            BankLogoAvatar(
                                bankName = bankFa,
                                size = 44.dp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = bankFa,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp
                                ),
                                color = if (isSelected) AlabasterSilk else OrosiOnSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 2
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = accountCustomTitle,
                    onValueChange = { accountCustomTitle = it },
                    label = {
                        Text(
                            text = "نام حساب (اختیاری — مثلاً: حساب شخصی من)",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    placeholder = { Text("بانک $selectedBankName") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_account_name_input")
                )

                OutlinedTextField(
                    value = balanceField,
                    onValueChange = { balanceField = formatAmountTextFieldValue(it) },
                    label = {
                        Text(
                            text = "مانده فعلی (تومان)",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    placeholder = { Text("مثلاً: ۴,۳۴۲,۱۳۹") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_account_balance_input")
                )

                // Jibeman-style SMS sender section: "سرشماره‌های پیامکی" + "+ افزودن سرشماره از پیامک‌ها"
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(OrosiSurfaceContainer)
                        .border(1.dp, OrosiHairline, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "سرشماره‌های پیامکی ($selectedBankName)",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            ),
                            color = AlabasterSilk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        val allDisplayedSenders = (defaultSendersForSelectedBank + attachedSenders).distinct()
                        if (allDisplayedSenders.isNotEmpty()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                allDisplayedSenders.take(2).forEach { sender ->
                                    Text(
                                        text = PersianNumberUtils.formatLtrSender(sender),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            textDirection = TextDirection.Ltr,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = NishapurTurquoiseSoft,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    if (attachedSenders.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "سرشماره‌های انتخاب‌شده:",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = SaffronGoldLight
                            )
                            Text(
                                text = PersianNumberUtils.formatLtrSenderList(attachedSenders, "  "),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    textDirection = TextDirection.Ltr
                                ),
                                color = SaffronGoldLight
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            val list = mutableListOf<InboxSmsPickerRow>()
                            try {
                                val cursor = context.contentResolver.query(
                                    android.provider.Telephony.Sms.Inbox.CONTENT_URI,
                                    arrayOf(
                                        android.provider.Telephony.Sms._ID,
                                        android.provider.Telephony.Sms.ADDRESS,
                                        android.provider.Telephony.Sms.BODY,
                                        android.provider.Telephony.Sms.DATE
                                    ),
                                    null,
                                    null,
                                    "${android.provider.Telephony.Sms.DATE} DESC"
                                )
                                cursor?.use { c ->
                                    val idIdx = c.getColumnIndex(android.provider.Telephony.Sms._ID)
                                    val addrIdx = c.getColumnIndex(android.provider.Telephony.Sms.ADDRESS)
                                    val bodyIdx = c.getColumnIndex(android.provider.Telephony.Sms.BODY)
                                    val dateIdx = c.getColumnIndex(android.provider.Telephony.Sms.DATE)
                                    var count = 0
                                    while (c.moveToNext() && count < 45) {
                                        val addr = if (addrIdx >= 0) c.getString(addrIdx).orEmpty().trim() else ""
                                        val body = if (bodyIdx >= 0) c.getString(bodyIdx).orEmpty().trim() else ""
                                        val ts = if (dateIdx >= 0) c.getLong(dateIdx) else System.currentTimeMillis()
                                        if (addr.isNotBlank() && body.isNotBlank()) {
                                            val j = com.example.domain.JalaliCalendarUtils.timestampToJalali(ts)
                                            list.add(
                                                InboxSmsPickerRow(
                                                    id = if (idIdx >= 0) c.getLong(idIdx) else count.toLong(),
                                                    senderAddress = addr,
                                                    body = body,
                                                    dateStr = "${j.dateWithYear} • ${j.timeStr}"
                                                )
                                            )
                                            count++
                                        }
                                    }
                                }
                            } catch (_: Exception) {
                            }
                            recentInboxMessages = list
                            showSmsSenderPickerDialog = true
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_sender_from_sms_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = NishapurTurquoiseBright,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "+ افزودن سرشماره از پیامک‌ها",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = NishapurTurquoiseSoft,
                            maxLines = 1
                        )
                    }
                }
            } else {
                // Tab 2: منبع نقدی
                OutlinedTextField(
                    value = accountCustomTitle,
                    onValueChange = { accountCustomTitle = it },
                    label = {
                        Text(
                            text = "نام حساب (مثلاً: کیف پول نقدی، گاوصندوق)",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    placeholder = { Text("کیف پول نقدی") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_cash_account_name_input")
                )

                OutlinedTextField(
                    value = balanceField,
                    onValueChange = { balanceField = formatAmountTextFieldValue(it) },
                    label = {
                        Text(
                            text = "مانده فعلی (تومان)",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    placeholder = { Text("مثلاً: ۲,۵۰۰,۰۰۰") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_cash_account_balance_input")
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(OrosiSurfaceContainer)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "حساب تنخواه / شرکت",
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
                    color = AlabasterSilk,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = isTankhah,
                    onCheckedChange = { isTankhah = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AlabasterSilk,
                        checkedTrackColor = NishapurTurquoise
                    )
                )
            }

            Button(
                onClick = {
                    val finalBankName = if (selectedTab == 0) {
                        selectedBankName.ifBlank { accountCustomTitle.trim().ifEmpty { "بلو" } }
                    } else {
                        accountCustomTitle.trim().ifEmpty { "کیف پول نقدی" }
                    }
                    val parsedBalance = PersianNumberUtils.parseAmountInputToLong(balanceField.text) ?: 0L
                    onAddAccount(finalBankName, parsedBalance, isTankhah, cardLast4, attachedSenders)
                },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NishapurTurquoise,
                    contentColor = AlabasterSilk
                ),
                contentPadding = PaddingValues(vertical = 16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NishapurTurquoiseBright, RoundedCornerShape(16.dp))
                    .testTag("submit_new_account_button")
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ذخیره حساب",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }

    if (showSmsSenderPickerDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showSmsSenderPickerDialog = false },
            containerColor = OrosiSurfaceLow,
            titleContentColor = AlabasterSilk,
            textContentColor = AlabasterSilk,
            title = {
                Text(
                    text = "انتخاب سرشماره از پیامک‌های اخیر",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                if (recentInboxMessages.isEmpty()) {
                    Text(
                        text = "پیامکی در صندوق ورودی یافت نشد یا دسترسی پیامک هنوز تأیید نشده است.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OrosiOnSurfaceVariant
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(360.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        recentInboxMessages.forEach { smsItem ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(OrosiSurfaceContainer)
                                    .border(1.dp, OrosiHairline, RoundedCornerShape(14.dp))
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "سرشماره:",
                                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                                color = NishapurTurquoiseSoft,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = PersianNumberUtils.formatLtrSender(smsItem.senderAddress),
                                                style = MaterialTheme.typography.labelLarge.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    textDirection = TextDirection.Ltr
                                                ),
                                                color = NishapurTurquoiseSoft,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            text = smsItem.dateStr,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = OrosiOnSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(NishapurTurquoise)
                                            .clickable {
                                                if (smsItem.senderAddress !in attachedSenders) {
                                                    attachedSenders = attachedSenders + smsItem.senderAddress
                                                }
                                                showSmsSenderPickerDialog = false
                                            }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "انتخاب",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = AlabasterSilk
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = smsItem.body.replace("\n", " "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AlabasterSilk,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                OutlinedButton(onClick = { showSmsSenderPickerDialog = false }) {
                    Text("بستن", color = AlabasterSilk)
                }
            }
        )
    }
}

private fun queryRecentInboxSmsRows(context: android.content.Context): List<InboxSmsPickerRow> {
    val list = mutableListOf<InboxSmsPickerRow>()
    try {
        val cursor = context.contentResolver.query(
            android.provider.Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(
                android.provider.Telephony.Sms._ID,
                android.provider.Telephony.Sms.ADDRESS,
                android.provider.Telephony.Sms.BODY,
                android.provider.Telephony.Sms.DATE
            ),
            null,
            null,
            "${android.provider.Telephony.Sms.DATE} DESC"
        )
        cursor?.use { c ->
            val idIdx = c.getColumnIndex(android.provider.Telephony.Sms._ID)
            val addrIdx = c.getColumnIndex(android.provider.Telephony.Sms.ADDRESS)
            val bodyIdx = c.getColumnIndex(android.provider.Telephony.Sms.BODY)
            val dateIdx = c.getColumnIndex(android.provider.Telephony.Sms.DATE)
            var count = 0
            while (c.moveToNext() && count < 60) {
                val addr = if (addrIdx >= 0) c.getString(addrIdx).orEmpty().trim() else ""
                val body = if (bodyIdx >= 0) c.getString(bodyIdx).orEmpty().trim() else ""
                val ts = if (dateIdx >= 0) c.getLong(dateIdx) else System.currentTimeMillis()
                if (addr.isNotBlank() && body.isNotBlank()) {
                    val j = JalaliCalendarUtils.timestampToJalali(ts)
                    list.add(
                        InboxSmsPickerRow(
                            id = if (idIdx >= 0) c.getLong(idIdx) else count.toLong(),
                            senderAddress = addr,
                            body = body,
                            dateStr = "${j.dateWithYear} • ${j.timeStr}"
                        )
                    )
                    count++
                }
            }
        }
    } catch (_: Exception) {
    }
    return list
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditBankAccountBottomSheet(
    account: BankAccountEntity,
    existingCustomSenders: List<String>,
    isRescanning: Boolean,
    onSaveEdits: (
        originalBankName: String,
        newBankName: String,
        balanceToman: Long,
        isTankhah: Boolean,
        addedSenders: List<String>,
        removedSenders: List<String>
    ) -> Unit,
    onRescanBankFromSms: (bankName: String) -> Unit,
    onDeleteAccount: (bankName: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var editedBankName by remember(account.bankName) { mutableStateOf(account.bankName) }
    var balanceField by remember(account.bankName, account.balanceToman) {
        val formatted = PersianNumberUtils.formatLiveAmountInput(account.balanceToman.toString())
        mutableStateOf(TextFieldValue(text = formatted, selection = TextRange(formatted.length)))
    }
    var isTankhah by remember(account.bankName, account.isTankhah) { mutableStateOf(account.isTankhah) }
    var currentCustomSenders by remember(account.bankName, existingCustomSenders) {
        mutableStateOf(existingCustomSenders)
    }
    var removedSenders by remember(account.bankName) { mutableStateOf(listOf<String>()) }
    var showSmsPickerDialog by remember { mutableStateOf(false) }
    var recentInboxMessages by remember { mutableStateOf<List<InboxSmsPickerRow>>(emptyList()) }
    var showDeleteConfirm by remember(account.bankName) { mutableStateOf(false) }

    val defaultSenders = remember(editedBankName) {
        com.example.domain.JibemanSmsEngine.BANK_PROFILES
            .firstOrNull {
                it.nameFa == editedBankName.trim() ||
                    it.nameFa.replace("\u200C", " ") == editedBankName.trim().replace("\u200C", " ")
            }
            ?.senderIds
            ?.take(4)
            .orEmpty()
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = OrosiSurfaceLow,
        contentColor = AlabasterSilk
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .testTag("edit_bank_account_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    BankLogoAvatar(bankName = account.bankName, size = 40.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "مدیریت حساب ${account.bankName}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = AlabasterSilk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (account.isManual) "منبع دستی • قابل اتصال به سرشماره پیامکی" else "همگام‌سازی خودکار از پیامک بانک",
                            style = MaterialTheme.typography.labelSmall,
                            color = OrosiOnSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "بستن",
                        tint = OrosiOnSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = editedBankName,
                onValueChange = { editedBankName = it },
                label = { Text("نام حساب / بانک", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_account_name_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NishapurTurquoiseBright,
                    unfocusedBorderColor = OrosiHairline
                )
            )

            OutlinedTextField(
                value = balanceField,
                onValueChange = { balanceField = formatAmountTextFieldValue(it) },
                label = { Text("مانده فعلی (تومان)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_account_balance_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SaffronGoldLight,
                    unfocusedBorderColor = OrosiHairline
                )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(OrosiSurfaceContainer)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "حساب تنخواه / شرکت",
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
                    color = AlabasterSilk
                )
                Switch(
                    checked = isTankhah,
                    onCheckedChange = { isTankhah = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AlabasterSilk,
                        checkedTrackColor = NishapurTurquoise
                    ),
                    modifier = Modifier.testTag("edit_account_tankhah_switch")
                )
            }

            // Linked SMS senders + Add from SMS + Instant Single-Bank Rescan
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(OrosiSurfaceContainer)
                    .border(1.dp, OrosiHairline, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "سرشماره‌های پیامکی متصل",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = AlabasterSilk
                )

                if (defaultSenders.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        defaultSenders.forEach { sender ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(PersianLapis)
                                    .border(1.dp, PersianLapisLight.copy(alpha = 0.4f), RoundedCornerShape(50))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = PersianNumberUtils.formatLtrSender(sender),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        textDirection = TextDirection.Ltr,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = NishapurTurquoiseSoft
                                )
                            }
                        }
                    }
                }

                if (currentCustomSenders.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        currentCustomSenders.forEach { sender ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(SaffronGold.copy(alpha = 0.2f))
                                    .border(1.dp, SaffronGoldBright.copy(alpha = 0.6f), RoundedCornerShape(50))
                                    .clickable {
                                        currentCustomSenders = currentCustomSenders - sender
                                        if (sender !in removedSenders) {
                                            removedSenders = removedSenders + sender
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = PersianNumberUtils.formatLtrSender(sender),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        textDirection = TextDirection.Ltr,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = SaffronGoldLight
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "حذف سرشماره",
                                    tint = SaffronGoldLight,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        recentInboxMessages = queryRecentInboxSmsRows(context)
                        showSmsPickerDialog = true
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_account_add_sender_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = NishapurTurquoiseBright,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "+ افزودن سرشماره از پیامک‌ها",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = NishapurTurquoiseSoft
                    )
                }

                OutlinedButton(
                    onClick = { onRescanBankFromSms(account.bankName) },
                    enabled = !isRescanning,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rescan_single_bank_button")
                ) {
                    if (isRescanning) {
                        CircularProgressIndicator(
                            color = NishapurTurquoiseBright,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            tint = NishapurTurquoiseBright,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isRescanning) "در حال جستجوی پیامک‌های بانک..." else "بازخوانی موجودی این بانک از پیامک‌ها",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = AlabasterSilk
                    )
                }
            }

            Button(
                onClick = {
                    val parsedBalance = PersianNumberUtils.parseAmountInputToLong(balanceField.text) ?: 0L
                    val added = currentCustomSenders.filter { it !in existingCustomSenders }
                    onSaveEdits(
                        account.bankName,
                        editedBankName.trim().ifEmpty { account.bankName },
                        parsedBalance,
                        isTankhah,
                        added,
                        removedSenders
                    )
                },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NishapurTurquoise,
                    contentColor = AlabasterSilk
                ),
                contentPadding = PaddingValues(vertical = 15.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NishapurTurquoiseBright, RoundedCornerShape(16.dp))
                    .testTag("save_edited_account_button")
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ذخیره تغییرات",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            if (!showDeleteConfirm) {
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("delete_account_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = null,
                        tint = RubyRoseSoft,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "حذف این حساب",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = RubyRoseSoft
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(RubyRose.copy(alpha = 0.22f))
                        .border(1.dp, RubyRoseSoft, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "حساب «${account.bankName}» از فهرست حساب‌ها حذف شود؟ (تراکنش‌های قبلی شما حفظ می‌شوند)",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = AlabasterSilk
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onDeleteAccount(account.bankName) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RubyRose,
                                contentColor = AlabasterSilk
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("confirm_delete_account_button")
                        ) {
                            Text(
                                text = "حذف قطعی",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold)
                            )
                        }
                        OutlinedButton(
                            onClick = { showDeleteConfirm = false },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("انصراف", color = AlabasterSilk)
                        }
                    }
                }
            }
        }
    }

    if (showSmsPickerDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showSmsPickerDialog = false },
            containerColor = OrosiSurfaceLow,
            titleContentColor = AlabasterSilk,
            textContentColor = AlabasterSilk,
            title = {
                Text(
                    text = "انتخاب سرشماره از پیامک‌های اخیر",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                if (recentInboxMessages.isEmpty()) {
                    Text(
                        text = "پیامکی در صندوق ورودی یافت نشد یا دسترسی پیامک هنوز تأیید نشده است.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OrosiOnSurfaceVariant
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(360.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        recentInboxMessages.forEach { smsItem ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(OrosiSurfaceContainer)
                                    .border(1.dp, OrosiHairline, RoundedCornerShape(14.dp))
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = PersianNumberUtils.formatLtrSender(smsItem.senderAddress),
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                fontWeight = FontWeight.Bold,
                                                textDirection = TextDirection.Ltr
                                            ),
                                            color = NishapurTurquoiseSoft,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = smsItem.dateStr,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = OrosiOnSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(NishapurTurquoise)
                                            .clickable {
                                                if (smsItem.senderAddress !in currentCustomSenders) {
                                                    currentCustomSenders = currentCustomSenders + smsItem.senderAddress
                                                }
                                                removedSenders = removedSenders - smsItem.senderAddress
                                                showSmsPickerDialog = false
                                            }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "انتخاب",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = AlabasterSilk
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = smsItem.body.replace("\n", " "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AlabasterSilk,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                OutlinedButton(onClick = { showSmsPickerDialog = false }) {
                    Text("بستن", color = AlabasterSilk)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsSimulatorBottomSheet(
    onCleanPurgeAndRescanRealSms: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = OrosiSurfaceLow,
        contentColor = AlabasterSilk
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 30.dp)
                .testTag("sms_simulator_sheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = NishapurTurquoiseBright,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "اتصال خودکار به پیامک همه بانک‌ها: فعال ✓",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = AlabasterSilk
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "بستن",
                        tint = OrosiOnSurfaceVariant
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(OrosiSurfaceContainer)
                    .border(1.dp, NishapurTurquoise.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "✓ اسکن کامل صندوق پیامک و شناسایی تمام بانک‌های شما",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = NishapurTurquoiseSoft
                )
                Text(
                    text = "پشتیبانی خودکار از تمام بانک‌های ایران: بلوبانک، سپه، پاسارگاد، ملت، ویپاد، ملی، صادرات، تجارت، سامان، رسالت، مسکن، رفاه، آینده، پارسیان، اقتصاد نوین، مهر ایران، کشاورزی، سینا، شهر، گردشگری و آبانک.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OrosiOnSurfaceVariant
                )
                Text(
                    text = "• استخراج آخرین مانده هر بانک حتی از پیامک‌های ماه‌های گذشته و نادیده گرفتن ۱۰۰٪ پیامک‌های تبلیغاتی (دیجی‌کالا، اسنپ، تپسی)",
                    style = MaterialTheme.typography.bodySmall,
                    color = SaffronGoldLight
                )
            }

            Button(
                onClick = onCleanPurgeAndRescanRealSms,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PersianLapis,
                    contentColor = AlabasterSilk
                ),
                contentPadding = PaddingValues(vertical = 16.dp, horizontal = 16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NishapurTurquoiseBright, RoundedCornerShape(16.dp))
                    .testTag("clean_rescan_real_sms_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = null,
                    tint = NishapurTurquoiseBright
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "پاکسازی و بازخوانی تمیز پیامک‌های واقعی",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaydayAndBackupBottomSheet(
    settings: AppSettingsEntity,
    backupJsonText: String,
    onSaveSettings: (Int, String, Boolean, Long, Long) -> Unit,
    onSetTankhahModeEnabled: (Boolean) -> Unit = {},
    onSelectFontFamily: (String) -> Unit = {},
    onImportBackup: (String) -> Unit,
    onResetDefault: () -> Unit,
    onDismiss: () -> Unit
) {
    val liveCurrentCycleLabel = remember(settings.paydayStartDay) {
        JalaliCalendarUtils.currentPaydayCycleRange(settings.paydayStartDay).label
    }
    var cycleLabel by remember(settings, liveCurrentCycleLabel) {
        mutableStateOf(liveCurrentCycleLabel)
    }
    var paydayDayStr by remember(settings) {
        mutableStateOf(PersianNumberUtils.toPersianDigits(settings.paydayStartDay.toString()))
    }
    var autoDetectSalary by remember(settings) { mutableStateOf(settings.autoDetectSalary) }
    var dailySafeField by remember(settings) {
        val formatted = PersianNumberUtils.formatToman(settings.dailySafeBudgetToman)
        mutableStateOf(TextFieldValue(text = formatted, selection = TextRange(formatted.length)))
    }
    var editableBackupJson by remember(backupJsonText) { mutableStateOf(backupJsonText) }
    var showBackupBox by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = OrosiSurfaceLow,
        contentColor = AlabasterSilk
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .testTag("payday_backup_sheet")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "تنظیمات ماه حقوقی و پشتیبان آفلاین",
                    style = MaterialTheme.typography.titleLarge,
                    color = AlabasterSilk
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "بستن",
                        tint = OrosiOnSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Live 5-Font Family Switcher (IRANSansX Default + Dana + IRANYekanX + Peyda + Vazirmatn)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(OrosiSurfaceContainer)
                    .border(1.dp, OrosiHairline, RoundedCornerShape(16.dp))
                    .padding(14.dp)
                    .testTag("font_family_selector_card"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "فونت برنامه (اعداد فارسی)",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = AlabasterSilk
                    )
                    val activeOption = com.example.ui.theme.OrosiFontOption.fromKey(settings.selectedFontFamily)
                    Text(
                        text = "فعال: ${activeOption.persianTitle} ✓",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = NishapurTurquoiseSoft
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    com.example.ui.theme.OrosiFontOption.entries.forEach { option ->
                        val isSelected = com.example.ui.theme.OrosiFontOption.fromKey(settings.selectedFontFamily) == option
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) NishapurTurquoise else OrosiSurfaceHigh
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) NishapurTurquoiseBright else OrosiHairline,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { onSelectFontFamily(option.key) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag("select_font_${option.key}")
                        ) {
                            Text(
                                text = option.persianTitle,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontFamily = option.fontFamily,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                    fontSize = 12.5.sp
                                ),
                                color = if (isSelected) AlabasterSilk else OrosiOnSurfaceVariant,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = cycleLabel,
                onValueChange = { cycleLabel = it },
                label = { Text("بازه ماه حقوقی", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NishapurTurquoiseBright,
                    unfocusedBorderColor = OrosiHairline
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Rule 1: Side-by-Side Inputs with single-line labels and Alignment.Bottom
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = paydayDayStr,
                    onValueChange = { paydayDayStr = PersianNumberUtils.toPersianDigits(it) },
                    label = {
                        Text(
                            text = "روز واریز حقوق",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NishapurTurquoiseBright,
                        unfocusedBorderColor = OrosiHairline
                    )
                )

                OutlinedTextField(
                    value = dailySafeField,
                    onValueChange = { dailySafeField = formatAmountTextFieldValue(it) },
                    label = {
                        Text(
                            text = "بودجه روزانه (تومان)",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1.35f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NishapurTurquoiseBright,
                        unfocusedBorderColor = OrosiHairline
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(OrosiSurfaceContainer)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "تفکیک حساب تنخواه",
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
                    color = AlabasterSilk,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = settings.tankhahModeEnabled,
                    onCheckedChange = onSetTankhahModeEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AlabasterSilk,
                        checkedTrackColor = NishapurTurquoise
                    ),
                    modifier = Modifier.testTag("settings_enable_tankhah_mode_switch")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(OrosiSurfaceContainer)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "تشخیص خودکار حقوق",
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
                    color = AlabasterSilk,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = autoDetectSalary,
                    onCheckedChange = { autoDetectSalary = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AlabasterSilk,
                        checkedTrackColor = NishapurTurquoise
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = {
                    val day = PersianNumberUtils.toEnglishDigits(paydayDayStr).toIntOrNull() ?: 1
                    val dailySafe = PersianNumberUtils.parseAmountInputToLong(dailySafeField.text) ?: 680_000L
                    onSaveSettings(day, cycleLabel, autoDetectSalary, settings.goldPrice18kToman, dailySafe)
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PersianLapis,
                    contentColor = AlabasterSilk
                ),
                contentPadding = PaddingValues(vertical = 14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, PersianLapisLight, RoundedCornerShape(14.dp))
                    .testTag("save_payday_settings_button")
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ذخیره تنظیمات",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showBackupBox = !showBackupBox },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("toggle_backup_json_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SaveAlt,
                        contentDescription = null,
                        tint = NishapurTurquoiseBright,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (showBackupBox) "بستن پشتیبان" else "خروجی / بازیابی بک‌آپ",
                        color = AlabasterSilk,
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                OutlinedButton(
                    onClick = onResetDefault,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Restore,
                        contentDescription = null,
                        tint = SaffronGoldBright,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "بازنشانی پیش‌فرض",
                        color = SaffronGoldLight,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            if (showBackupBox) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = editableBackupJson,
                    onValueChange = { editableBackupJson = it },
                    label = { Text("پشتیبان JSON دیتابیس محلی") },
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { onImportBackup(editableBackupJson) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NishapurTurquoise,
                        contentColor = AlabasterSilk
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("بازیابی اطلاعات")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // In-App Automated Regression & Stress Test Lab (requested in Video batch_9)
            var regressionReport by remember { mutableStateOf<RegressionSuiteReport?>(null) }
            var showRegressionDetails by remember { mutableStateOf(false) }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(OrosiSurfaceContainer)
                    .border(
                        1.dp,
                        if (regressionReport?.allPassed == true) NishapurTurquoiseBright else OrosiHairline,
                        RoundedCornerShape(18.dp)
                    )
                    .padding(14.dp)
                    .testTag("regression_test_lab_card"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        val totalCasesPersian = PersianNumberUtils.toPersianDigits(OrosiRegressionSuite.EXPECTED_TOTAL_CASES.toString())
                        Text(
                            text = "آزمایشگاه تست خودکار رگرسیون و استرس",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = AlabasterSilk
                        )
                        Text(
                            text = "بررسی خودکار $totalCasesPersian سناریوی صوتی پیچیده، پیامکی، تقویم و بانک‌ها بدون نیاز به تست دستی",
                            style = MaterialTheme.typography.labelSmall,
                            color = OrosiOnSurfaceVariant
                        )
                    }
                    regressionReport?.let { report ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (report.allPassed) NishapurTurquoiseDeep else RubyRose.copy(alpha = 0.35f))
                                .border(
                                    1.dp,
                                    if (report.allPassed) NishapurTurquoiseBright else RubyRoseSoft,
                                    RoundedCornerShape(50)
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                .testTag("regression_report_badge")
                        ) {
                            Text(
                                text = "${PersianNumberUtils.toPersianDigits(report.passedCount.toString())}/${PersianNumberUtils.toPersianDigits(report.totalCount.toString())} موفق ✓",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = if (report.allPassed) NishapurTurquoiseSoft else RubyRoseSoft
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        regressionReport = OrosiRegressionSuite.runFullRegressionAndStressSuite()
                        showRegressionDetails = true
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NishapurTurquoiseDeep,
                        contentColor = AlabasterSilk
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, NishapurTurquoiseBright, RoundedCornerShape(12.dp))
                        .testTag("run_regression_suite_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = NishapurTurquoiseBright,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (regressionReport == null) {
                            "اجرای تست خودکار رگرسیون و استرس (${PersianNumberUtils.toPersianDigits(OrosiRegressionSuite.EXPECTED_TOTAL_CASES.toString())} سناریو)"
                        } else {
                            "اجرای مجدد تست استرس (${PersianNumberUtils.toPersianDigits(regressionReport!!.totalDurationMs.toString())} میلی‌ثانیه)"
                        },
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }

                regressionReport?.let { report ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showRegressionDetails = !showRegressionDetails }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "نتیجه: ${PersianNumberUtils.toPersianDigits(report.passedCount.toString())} تست موفق از ${PersianNumberUtils.toPersianDigits(report.totalCount.toString())} در ${PersianNumberUtils.toPersianDigits(report.totalDurationMs.toString())} میلی‌ثانیه",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (report.allPassed) NishapurTurquoiseSoft else RubyRoseSoft
                        )
                        Icon(
                            imageVector = if (showRegressionDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = OrosiOnSurfaceVariant
                        )
                    }

                    AnimatedVisibility(visible = showRegressionDetails) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            report.results.forEach { tc ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(OrosiSurfaceHigh)
                                        .border(
                                            1.dp,
                                            if (tc.passed) NishapurTurquoise.copy(alpha = 0.35f) else RubyRoseSoft,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .padding(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${PersianNumberUtils.toPersianDigits(tc.id.toString())}. ${tc.title}",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = AlabasterSilk,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = if (tc.passed) "✓ موفق" else "✗ خطا",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                            color = if (tc.passed) NishapurTurquoiseSoft else RubyRoseSoft
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "ورودی: ${tc.inputSummary}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = OrosiOnSurfaceVariant
                                    )
                                    Text(
                                        text = "خروجی: ${tc.actualSummary}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                        color = NishapurTurquoiseSoft
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCommitmentBottomSheet(
    defaultCategory: String = "SAYYAD_ISSUED",
    onAddCommitment: (String, String, String, Long, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var category by remember(defaultCategory) { mutableStateOf(defaultCategory) }
    var isReceivableDong by remember { mutableStateOf(true) } // true = طلب دارم (به من بدهکار است), false = بدهی دارم (من بدهکارم)
    var title by remember { mutableStateOf("") }
    var counterparty by remember { mutableStateOf("") }
    var amountField by remember { mutableStateOf(TextFieldValue("")) }
    var dueDateStr by remember { mutableStateOf("") }
    var sayyadId by remember { mutableStateOf("") }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = OrosiSurfaceLow,
        contentColor = AlabasterSilk
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .testTag("add_commitment_sheet")
        ) {
            Text(
                text = "ثبت تعهد جدید (چک، قسط یا دُنگ)",
                style = MaterialTheme.typography.titleLarge,
                color = AlabasterSilk
            )
            Spacer(modifier = Modifier.height(12.dp))

            val categoryOptions = listOf(
                "SAYYAD_ISSUED" to "چک صیادی",
                "LOAN_INSTALLMENT" to "وام و قسط",
                "DONG" to "بدهی و طلب (دُنگ)"
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categoryOptions.forEach { (catKey, catLabel) ->
                    val selected = category == catKey
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) RoyalAmethyst else OrosiSurfaceContainer)
                            .border(
                                1.dp,
                                if (selected) RoyalAmethystSoft else OrosiHairline,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { category = catKey }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = catLabel,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = AlabasterSilk
                        )
                    }
                }
            }

            // When "بدهی و طلب (دُنگ)" is selected, show a clear 2-way selector:
            // "طلب دارم (به من بدهکار است)" vs "بدهی دارم (من بدهکارم)"
            if (category == "DONG") {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dong_direction_selector"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isReceivableDong) NishapurTurquoiseDeep else OrosiSurfaceContainer)
                            .border(
                                1.dp,
                                if (isReceivableDong) NishapurTurquoiseBright else OrosiHairline,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { isReceivableDong = true }
                            .padding(vertical = 10.dp, horizontal = 6.dp)
                            .testTag("dong_type_receivable"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "طلب دارم (به من بدهکار است)",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isReceivableDong) NishapurTurquoiseSoft else OrosiOnSurfaceVariant,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (!isReceivableDong) RubyRose.copy(alpha = 0.28f) else OrosiSurfaceContainer)
                            .border(
                                1.dp,
                                if (!isReceivableDong) RubyRoseSoft else OrosiHairline,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { isReceivableDong = false }
                            .padding(vertical = 10.dp, horizontal = 6.dp)
                            .testTag("dong_type_payable"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "بدهی دارم (من بدهکارم)",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (!isReceivableDong) RubyRoseSoft else OrosiOnSurfaceVariant,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("عنوان (مثلاً: چک اجاره، قسط وام، دنگ شام)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = amountField,
                onValueChange = { amountField = formatAmountTextFieldValue(it) },
                label = { Text("مبلغ (تومان)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                placeholder = { Text("مثلاً: ۱۲,۰۰۰,۰۰۰") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = dueDateStr,
                onValueChange = { dueDateStr = PersianNumberUtils.toPersianDigits(it) },
                label = { Text("تاریخ سررسید (مثلاً: ۳۰ آبان ۱۴۰۵)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                placeholder = { Text("مثلاً: ۳۰ آبان ۱۴۰۵") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = counterparty,
                onValueChange = { counterparty = it },
                label = { Text(if (category == "DONG") "نام دوست / طرف حساب" else "طرف حساب / بانک", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val amount = PersianNumberUtils.parseAmountInputToLong(amountField.text) ?: 5_000_000L
                    val baseTitle = title.trim().ifEmpty {
                        when (category) {
                            "DONG" -> if (isReceivableDong) "طلب دُنگ" else "بدهی دُنگ"
                            "LOAN_INSTALLMENT" -> "قسط وام"
                            else -> "چک صیادی"
                        }
                    }
                    val cleanCounterparty = if (category == "DONG") {
                        val prefix = if (isReceivableDong) "طلب از" else "بدهی به"
                        val person = counterparty.trim().ifEmpty { "دوست" }
                        if (person.startsWith("طلب") || person.startsWith("بدهی")) person else "$prefix $person"
                    } else {
                        counterparty.trim()
                    }
                    val cleanDate = dueDateStr.trim().ifEmpty { "۳۰ آبان ۱۴۰۵" }
                    onAddCommitment(category, baseTitle, cleanCounterparty, amount, cleanDate, sayyadId)
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RoyalAmethyst,
                    contentColor = AlabasterSilk
                ),
                contentPadding = PaddingValues(vertical = 14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("submit_new_commitment_button")
            ) {
                Icon(imageVector = Icons.Default.VerifiedUser, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "تأیید و ثبت",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}
