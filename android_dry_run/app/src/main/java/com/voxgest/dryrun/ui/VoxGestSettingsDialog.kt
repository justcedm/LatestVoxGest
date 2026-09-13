package com.voxgest.dryrun.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.voxgest.dryrun.PreferredCameraLens
import com.voxgest.dryrun.VoxGestAppLanguage
import com.voxgest.dryrun.VoxGestColorStyle
import com.voxgest.dryrun.VoxGestMessageLanguage
import com.voxgest.dryrun.VoxGestThemePreference
import com.voxgest.dryrun.VoxGestUserSettings
import com.voxgest.dryrun.VoxGestUserSettingsSnapshot
import java.util.Locale

internal data class PackagedProfilePresentation(
    val title: String,
    val badge: String,
    val model: String,
    val classCount: Int,
    val contract: String,
    val inputTensor: String,
    val modelSize: String,
    val orientation: String
)

private enum class SettingsPage { DASHBOARD, APP_LANGUAGE, MESSAGE_LANGUAGE, ABOUT }
private enum class SettingsConfirmation { NONE, CLEAR_CONVERSATION, RESET_PREFERENCES }

@Composable
internal fun VoxGestSettingsDialog(
    settings: VoxGestUserSettingsSnapshot,
    activeProfile: PackagedProfilePresentation,
    experimentalProfiles: List<PackagedProfilePresentation>,
    appVersion: String,
    messagePreview: String,
    onSettingsChange: (VoxGestUserSettingsSnapshot) -> Unit,
    onClearConversation: () -> Unit,
    onResetPreferences: () -> Unit,
    onDismiss: () -> Unit
) {
    val copy = LocalVoxGestCopy.current
    val isFilipino = settings.appLanguage == VoxGestAppLanguage.FILIPINO
    fun tr(english: String, filipino: String) = if (isFilipino) filipino else english
    var page by remember { mutableStateOf(SettingsPage.DASHBOARD) }
    var confirmation by remember { mutableStateOf(SettingsConfirmation.NONE) }

    BackHandler(enabled = page != SettingsPage.DASHBOARD && confirmation == SettingsConfirmation.NONE) {
        page = SettingsPage.DASHBOARD
    }

    Dialog(
        onDismissRequest = {
            if (page == SettingsPage.DASHBOARD) onDismiss() else page = SettingsPage.DASHBOARD
        },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)
    ) {
        val dialogView = LocalView.current
        val barColor = MaterialTheme.colorScheme.background
        val navigationColor = MaterialTheme.colorScheme.surface
        val useDarkIcons = barColor.luminance() > 0.5f
        SideEffect {
            (dialogView.parent as? DialogWindowProvider)?.window?.let { window ->
                window.statusBarColor = barColor.toArgb()
                window.navigationBarColor = navigationColor.toArgb()
                WindowCompat.getInsetsController(window, dialogView).apply {
                    isAppearanceLightStatusBars = useDarkIcons
                    isAppearanceLightNavigationBars = useDarkIcons
                }
            }
        }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                SettingsHeader(
                    title = when (page) {
                        SettingsPage.DASHBOARD -> copy.settings
                        SettingsPage.APP_LANGUAGE -> copy.appLanguage
                        SettingsPage.MESSAGE_LANGUAGE -> copy.messageLanguage
                        SettingsPage.ABOUT -> copy.about
                    },
                    subtitle = when (page) {
                        SettingsPage.DASHBOARD -> tr("Offline app and display preferences", "Offline na mga kagustuhan ng app at display")
                        SettingsPage.APP_LANGUAGE -> tr("Choose the whole-interface language", "Piliin ang wika ng buong interface")
                        SettingsPage.MESSAGE_LANGUAGE -> tr("Independent from recognition tokens", "Hiwalay sa mga token ng pagkilala")
                        SettingsPage.ABOUT -> tr("Verified project information", "Beripikadong impormasyon ng proyekto")
                    },
                    canGoBack = page != SettingsPage.DASHBOARD,
                    onBack = { page = SettingsPage.DASHBOARD },
                    onDone = onDismiss,
                    doneLabel = copy.done
                )
                when (page) {
                    SettingsPage.DASHBOARD -> SettingsDashboard(
                        settings = settings,
                        activeProfile = activeProfile,
                        experimentalProfiles = experimentalProfiles,
                        tr = ::tr,
                        onSettingsChange = onSettingsChange,
                        onOpenAppLanguage = { page = SettingsPage.APP_LANGUAGE },
                        onOpenMessageLanguage = { page = SettingsPage.MESSAGE_LANGUAGE },
                        onOpenAbout = { page = SettingsPage.ABOUT },
                        onClearConversation = { confirmation = SettingsConfirmation.CLEAR_CONVERSATION },
                        onResetPreferences = { confirmation = SettingsConfirmation.RESET_PREFERENCES }
                    )
                    SettingsPage.APP_LANGUAGE -> AppLanguagePage(settings, onSettingsChange, copy)
                    SettingsPage.MESSAGE_LANGUAGE -> MessageLanguagePage(settings, messagePreview, onSettingsChange, copy)
                    SettingsPage.ABOUT -> AboutPage(appVersion, activeProfile, tr = ::tr)
                }
            }
        }
    }

    if (confirmation != SettingsConfirmation.NONE) {
        val clearing = confirmation == SettingsConfirmation.CLEAR_CONVERSATION
        AlertDialog(
            onDismissRequest = { confirmation = SettingsConfirmation.NONE },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text(if (clearing) tr("Clear this conversation?", "Burahin ang usapan?") else tr("Reset preferences?", "I-reset ang mga setting?")) },
            text = {
                Text(
                    if (clearing) tr(
                        "Only messages shown in this app session will be removed.",
                        "Ang mga mensahe lamang sa kasalukuyang session ang mabubura."
                    ) else tr(
                        "Safe display defaults will be restored. Models, labels, and recognition assets are untouched.",
                        "Ibabalik ang ligtas na display defaults. Hindi gagalawin ang models, labels, o recognition assets."
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (clearing) onClearConversation() else onResetPreferences()
                    confirmation = SettingsConfirmation.NONE
                }) { Text(if (clearing) copy.clear else tr("Reset", "I-reset")) }
            },
            dismissButton = { TextButton(onClick = { confirmation = SettingsConfirmation.NONE }) { Text(copy.cancel) } }
        )
    }
}

@Composable
private fun SettingsHeader(
    title: String,
    subtitle: String,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onDone: () -> Unit,
    doneLabel: String
) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (canGoBack) {
                TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("‹", fontSize = 30.sp) }
            } else {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                    Text("V", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.ExtraBold)
                }
            }
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
            }
            TextButton(onClick = onDone, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(doneLabel, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SettingsDashboard(
    settings: VoxGestUserSettingsSnapshot,
    activeProfile: PackagedProfilePresentation,
    experimentalProfiles: List<PackagedProfilePresentation>,
    tr: (String, String) -> String,
    onSettingsChange: (VoxGestUserSettingsSnapshot) -> Unit,
    onOpenAppLanguage: () -> Unit,
    onOpenMessageLanguage: () -> Unit,
    onOpenAbout: () -> Unit,
    onClearConversation: () -> Unit,
    onResetPreferences: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsSection(tr("APPEARANCE", "HITSURA")) {
            Text(tr("Appearance mode", "Mode ng hitsura"), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf(
                    VoxGestThemePreference.SYSTEM to tr("System", "System"),
                    VoxGestThemePreference.LIGHT to tr("Light", "Maliwanag"),
                    VoxGestThemePreference.DARK to tr("Dark", "Madilim")
                ).forEach { (theme, label) ->
                    SettingsChoice(label, settings.themePreference == theme, Modifier.weight(1f)) {
                        onSettingsChange(settings.copy(themePreference = theme))
                    }
                }
            }
            SettingsDivider()
            Text(tr("Color Style", "Estilo ng Kulay"), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            Text(
                tr("Curated accents; safety colors stay unchanged.", "Piniling accents; hindi nagbabago ang safety colors."),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
            )
            VoxGestColorStyle.entries.forEach { style ->
                ColorStylePreviewCard(
                    style = style,
                    selected = settings.colorStyle == style,
                    tr = tr,
                    onClick = { onSettingsChange(settings.copy(colorStyle = style)) }
                )
                if (style != VoxGestColorStyle.entries.last()) Spacer(Modifier.height(8.dp))
            }
        }

        SettingsSection(tr("LANGUAGE & COMMUNICATION", "WIKA AT KOMUNIKASYON")) {
            SettingsNavigationRow(
                tr("App language", "Wika ng app"),
                if (settings.appLanguage == VoxGestAppLanguage.ENGLISH) "English" else "Filipino",
                onOpenAppLanguage
            )
            SettingsDivider()
            SettingsNavigationRow(
                tr("Message output language", "Wika ng mensahe"),
                settings.messageLanguage.name.lowercase().replaceFirstChar { it.titlecase() },
                onOpenMessageLanguage
            )
        }

        SettingsSection(tr("COMMUNICATION SUPPORT", "SUPORTA SA KOMUNIKASYON")) {
            SettingsToggleRow(
                tr("Sentence suggestions", "Mga mungkahing pangungusap"),
                tr("Deterministic suggestions from accepted recognition tokens only. Default OFF.", "Deterministikong mungkahi mula lamang sa tinanggap na recognition tokens. Default OFF."),
                settings.sentenceSuggestions
            ) { onSettingsChange(settings.copy(sentenceSuggestions = it)) }
            SettingsDivider()
            SettingsToggleRow(
                tr("Auto-speak accepted message", "Awtomatikong bigkasin ang tinanggap na mensahe"),
                tr("Uses post-gate composed text only. Default OFF.", "Post-gate na binuong teksto lamang. Default OFF."),
                settings.autoSpeakAcceptedMessage
            ) { onSettingsChange(settings.copy(autoSpeakAcceptedMessage = it)) }
            SettingsDivider()
            SettingsToggleRow(
                tr("Haptic acknowledgement", "Haptic na pagkilala"),
                tr("Vibrate only after a user-facing sign is accepted.", "Mag-vibrate lamang kapag may tinanggap na user-facing sign."),
                settings.hapticFeedback
            ) { onSettingsChange(settings.copy(hapticFeedback = it)) }
            SettingsDivider()
            Text(tr("TTS speech rate", "Bilis ng TTS"), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            Text(String.format(Locale.US, "%.1fx", settings.ttsRate), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Slider(
                value = settings.ttsRate,
                onValueChange = { onSettingsChange(settings.copy(ttsRate = it)) },
                valueRange = VoxGestUserSettings.MIN_TTS_RATE..VoxGestUserSettings.MAX_TTS_RATE,
                steps = 3
            )
        }

        SettingsSection(tr("RECOGNITION EXPERIENCE", "KARANASAN SA PAGKILALA")) {
            Text(tr("Preferred camera", "Gustong camera"), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsChoice(tr("Auto", "Auto"), settings.preferredCameraLens == PreferredCameraLens.AUTO, Modifier.weight(1f)) {
                    onSettingsChange(settings.copy(preferredCameraLens = PreferredCameraLens.AUTO))
                }
                SettingsChoice(tr("Front", "Harap"), settings.preferredCameraLens == PreferredCameraLens.FRONT, Modifier.weight(1f)) {
                    onSettingsChange(settings.copy(preferredCameraLens = PreferredCameraLens.FRONT))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsChoice(tr("Rear", "Likod"), settings.preferredCameraLens == PreferredCameraLens.REAR, Modifier.weight(1f)) {
                    onSettingsChange(settings.copy(preferredCameraLens = PreferredCameraLens.REAR))
                }
                SettingsChoice(tr("External", "External"), settings.preferredCameraLens == PreferredCameraLens.EXTERNAL, Modifier.weight(1f)) {
                    onSettingsChange(settings.copy(preferredCameraLens = PreferredCameraLens.EXTERNAL))
                }
            }
            SettingsDivider()
            SettingsToggleRow(
                tr("Camera guidance", "Gabay sa camera"),
                tr("Show concise positioning and readiness guidance.", "Ipakita ang maikling gabay sa puwesto at kahandaan."),
                settings.trackingOverlay
            ) { onSettingsChange(settings.copy(trackingOverlay = it)) }
            SettingsDivider()
            SettingsToggleRow(
                tr("Show AI Landmarks", "Ipakita ang AI Landmarks"),
                tr(
                    "Display the body and hand landmarks VoxGest uses while analyzing signs. Local only; default OFF.",
                    "Ipakita ang body at hand landmarks na sinusuri ng VoxGest. Lokal lamang; default OFF."
                ),
                settings.showAiLandmarks
            ) { onSettingsChange(settings.copy(showAiLandmarks = it)) }
            if (settings.showAiLandmarks) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        tr(
                            "Uses the same 33-pose and two 21-hand landmark results already produced by recognition. No face mesh, second detector, frame storage, or upload.",
                            "Parehong 33-pose at dalawang 21-hand landmark results ng recognition ang ginagamit. Walang face mesh, pangalawang detector, pag-save, o upload ng frame."
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
            SettingsDivider()
            SettingsToggleRow(
                tr("Mirror front preview", "I-mirror ang front preview"),
                tr("Display only. Analyzer/model-input orientation never changes.", "Display lamang. Hindi nagbabago ang orientation ng analyzer/model input."),
                settings.mirrorFrontPreview
            ) { onSettingsChange(settings.copy(mirrorFrontPreview = it)) }
        }

        SettingsSection(tr("ACCESSIBILITY", "ACCESSIBILITY")) {
            SettingsToggleRow(
                tr("Reduced motion", "Bawas galaw"),
                tr("Minimizes decorative animation; recognition timing is unaffected.", "Binabawasan ang dekorasyong animation; hindi apektado ang recognition timing."),
                settings.reducedMotion
            ) { onSettingsChange(settings.copy(reducedMotion = it)) }
        }

        SettingsSection(tr("RECOGNITION SYSTEM", "SISTEMA NG PAGKILALA")) {
            ProfileSummary(activeProfile)
            SettingsDivider()
            SettingsToggleRow(
                tr("Runtime diagnostics", "Runtime diagnostics"),
                tr("Shows profile facts. Legacy tools still require the explicit debug launch extra.", "Ipinapakita ang profile facts. Kailangan pa rin ng explicit debug launch extra para sa legacy tools."),
                settings.runtimeDiagnostics
            ) { onSettingsChange(settings.copy(runtimeDiagnostics = it)) }
            if (settings.runtimeDiagnostics) {
                experimentalProfiles.forEach { profile -> SettingsDivider(); ProfileSummary(profile) }
            }
        }

        SettingsSection(tr("ABOUT VOXGEST", "TUNGKOL SA VOXGEST")) {
            SettingsNavigationRow(tr("About VoxGest", "Tungkol sa VoxGest"), tr("Verified project and privacy information", "Beripikadong proyekto at privacy information"), onOpenAbout)
            SettingsDivider()
            SettingsDangerAction(tr("Clear conversation", "Burahin ang usapan"), tr("Removes current session entries only.", "Kasalukuyang session entries lamang."), onClearConversation)
            SettingsDivider()
            SettingsDangerAction(tr("Reset preferences", "I-reset ang mga setting"), tr("Restores safe defaults; protected assets remain untouched.", "Ibabalik ang ligtas na defaults; hindi gagalawin ang protected assets."), onResetPreferences)
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun AppLanguagePage(settings: VoxGestUserSettingsSnapshot, onSettingsChange: (VoxGestUserSettingsSnapshot) -> Unit, copy: VoxGestCopy) {
    var draft by remember(settings.appLanguage) { mutableStateOf(settings.appLanguage) }
    ChoicePage(
        title = "Choose your interface language / Piliin ang wika ng interface",
        options = listOf(
            Triple(VoxGestAppLanguage.ENGLISH, "English", "Use English throughout the app"),
            Triple(VoxGestAppLanguage.FILIPINO, "Filipino", "Gamitin ang Filipino sa buong app")
        ),
        selected = draft,
        onSelected = { draft = it },
        applyLabel = copy.apply,
        onApply = { onSettingsChange(settings.copy(appLanguage = draft)) }
    )
}

@Composable
private fun MessageLanguagePage(
    settings: VoxGestUserSettingsSnapshot,
    messagePreview: String,
    onSettingsChange: (VoxGestUserSettingsSnapshot) -> Unit,
    copy: VoxGestCopy
) {
    var draft by remember(settings.messageLanguage) { mutableStateOf(settings.messageLanguage) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Message presentation only — recognition tokens never change", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        listOf(
            Triple(VoxGestMessageLanguage.ENGLISH, copy.english, "Show the English presentation"),
            Triple(VoxGestMessageLanguage.FILIPINO, copy.filipino, "Show Filipino when a verified offline translation exists"),
            Triple(VoxGestMessageLanguage.BOTH, copy.both, "Show English and available Filipino together")
        ).forEach { (value, label, detail) ->
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { draft = value },
                shape = RoundedCornerShape(20.dp),
                color = if (draft == value) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, if (draft == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = draft == value, onClick = { draft = value })
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(label, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                        Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    }
                }
            }
        }
        VoxGestCapstoneCard {
            Text("PREVIEW", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            val preview = VoxGestOfflineMessagePresenter.present(messagePreview)
            if (messagePreview.isBlank()) {
                Text(copy.noMessage, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            } else {
                preview.visibleLines(draft).ifEmpty { listOf("" to copy.translationUnavailable) }.forEach { (language, value) ->
                    Text(if (language.isBlank()) value else "$language  $value", color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        Button(
            onClick = { onSettingsChange(settings.copy(messageLanguage = draft)) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) { Text(copy.apply, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun <T> ChoicePage(
    title: String,
    options: List<Triple<T, String, String>>,
    selected: T,
    onSelected: (T) -> Unit,
    applyLabel: String,
    onApply: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 17.sp)
        options.forEach { (value, label, detail) ->
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onSelected(value) },
                shape = RoundedCornerShape(20.dp),
                color = if (selected == value) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, if (selected == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selected == value, onClick = { onSelected(value) })
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(label, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                        Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 15.sp)
                    }
                }
            }
        }
        Button(
            onClick = onApply,
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) { Text(applyLabel, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun AboutPage(appVersion: String, activeProfile: PackagedProfilePresentation, tr: (String, String) -> String) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { VoxGestBrandMark(Modifier.size(104.dp)) }
        Text("VoxGest", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text("Version $appVersion", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        AboutCard(tr("CAPSTONE PURPOSE", "LAYUNIN NG CAPSTONE"), tr(
            "A BSIT capstone accessibility application that connects accepted FSL recognition output, speech, conversation presentation, and sign visualization on Android.",
            "Isang BSIT capstone accessibility app na nag-uugnay ng tinanggap na FSL recognition output, pananalita, usapan, at sign visualization sa Android."
        ))
        AboutCard(tr("PACKAGED PROFILE METADATA", "METADATA NG PACKAGED PROFILE"), tr(
            "The launcher manifest declares ${activeProfile.classCount} classes, ${activeProfile.contract}, ${activeProfile.inputTensor}. Runtime gates and device logs remain authoritative. The gated FullSign225 artifact is not presented as fully live-qualified.",
            "Idinedeklara ng launcher manifest ang ${activeProfile.classCount} classes, ${activeProfile.contract}, ${activeProfile.inputTensor}. Ang runtime gates at device logs pa rin ang awtoridad. Hindi ipinapakitang ganap na live-qualified ang gated FullSign225 artifact."
        ))
        AboutCard(tr("PRIVACY & OFFLINE BEHAVIOR", "PRIVACY AT OFFLINE NA GAWI"), tr(
            "On-device recognition, deterministic sentence suggestions, and current session presentation state remain local. Android speech recognition availability and privacy behavior depend on the installed speech service.",
            "Nananatili sa device ang recognition, deterministikong sentence suggestions, at kasalukuyang session state. Nakadepende sa naka-install na speech service ang availability at privacy ng Android speech recognition."
        ))
        AboutCard(tr("PROJECT TEAM", "PANGKAT NG PROYEKTO"), tr(
            "BSIT capstone team details have not been configured in this build. No partner organization or contact claim is implied.",
            "Hindi pa naka-configure sa build na ito ang detalye ng BSIT capstone team. Walang ipinahihiwatig na partner organization o contact claim."
        ))
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun AboutCard(title: String, body: String) {
    VoxGestCapstoneCard {
        Text(title, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    VoxGestCapstoneCard {
        Text(title, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun SettingsToggleRow(title: String, detail: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 15.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
    }
}

@Composable
private fun ColorStylePreviewCard(
    style: VoxGestColorStyle,
    selected: Boolean,
    tr: (String, String) -> String,
    onClick: () -> Unit
) {
    val preview = colorPreviewFor(style)
    val label = when (style) {
        VoxGestColorStyle.TERRACOTTA -> tr("Terracotta", "Terracotta")
        VoxGestColorStyle.SUN_GOLD -> tr("Sun Gold", "Sun Gold")
        VoxGestColorStyle.WARM_SAND -> tr("Warm Sand", "Warm Sand")
        VoxGestColorStyle.FILIPINO_HERITAGE -> tr("Filipino Heritage", "Pamanang Filipino")
    }
    val detail = when (style) {
        VoxGestColorStyle.TERRACOTTA -> tr("Confident and warm — VoxGest default", "Matatag at mainit — default ng VoxGest")
        VoxGestColorStyle.SUN_GOLD -> tr("Bright, focused, and restrained", "Maliwanag, malinaw, at kontrolado")
        VoxGestColorStyle.WARM_SAND -> tr("Calm and softly neutral", "Kalmado at banayad na neutral")
        VoxGestColorStyle.FILIPINO_HERITAGE -> tr("Terracotta, sunlight, and subtle local rhythm", "Terracotta, sikat ng araw, at banayad na lokal na ritmo")
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(preview.primary, preview.highlight, preview.support).forEachIndexed { index, color ->
                    Box(
                        Modifier
                            .size(if (index == 0) 28.dp else 18.dp)
                            .clip(if (index == 0) RoundedCornerShape(9.dp) else CircleShape)
                            .background(color)
                    )
                }
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, lineHeight = 14.sp)
            }
            if (selected) {
                Text("✓", color = MaterialTheme.colorScheme.primary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SettingsChoice(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(modifier = modifier.heightIn(min = 48.dp).clickable(onClick = onClick), shape = RoundedCornerShape(14.dp), color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun SettingsNavigationRow(title: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 54.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
        Text("›", color = MaterialTheme.colorScheme.primary, fontSize = 28.sp)
    }
}

@Composable
private fun ProfileSummary(profile: PackagedProfilePresentation) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(profile.title, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Text(profile.badge, color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
        }
    }
    Text("model: ${profile.model}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, modifier = Modifier.padding(top = 7.dp))
    Text("classes: ${profile.classCount}  contract: ${profile.contract}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
    Text("manifest tensor: ${profile.inputTensor}  size: ${profile.modelSize}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
    Text("orientation: ${profile.orientation}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
}

@Composable
private fun SettingsDangerAction(title: String, detail: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick), verticalArrangement = Arrangement.Center) {
        Text(title, color = MaterialTheme.colorScheme.error, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
}
