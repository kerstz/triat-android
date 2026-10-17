package com.ivor.ivormusic.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.ivor.ivormusic.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.data.PlaylistDisplayItem
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.YouTubeLinkParser
import com.ivor.ivormusic.ui.home.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.regex.Pattern

/**
 * Modern Jetpack Compose Bottom Sheet for importing YouTube Music playlists
 * directly from a .TXT file or pasted text/links.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TxtImportSheet(
    viewModel: HomeViewModel,
    onDismiss: () -> Unit,
    onImportSuccess: (playlistName: String, count: Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val userPlaylists by viewModel.userPlaylists.collectAsStateWithLifecycle()
    val availableLocalPlaylists = remember(userPlaylists) { userPlaylists.filter { it.id != "LM" && it.id != "RTM" } }

    var pastedText by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf<String?>(null) }
    var extractedVideoIds by remember { mutableStateOf<List<String>>(emptyList()) }

    var createNewPlaylist by remember { mutableStateOf(true) }
    val defaultPlaylistName = stringResource(R.string.txt_default_name)
    var playlistNameInput by remember { mutableStateOf(defaultPlaylistName) }
    var selectedPlaylistId by remember { mutableStateOf<String?>(null) }

    var isDropdownExpanded by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }

    // Data class holding extracted video reference and optional inline title
    data class ExtractedVideoRef(
        val videoId: String,
        val inlineTitle: String? = null
    )

    // Helper to extract YouTube video IDs and optional inline titles from raw text
    fun parseTextToVideoRefs(text: String): List<ExtractedVideoRef> {
        val result = mutableListOf<ExtractedVideoRef>()
        val seenIds = mutableSetOf<String>()
        val regex = Pattern.compile("https?://(?:www\\.|music\\.)?(?:youtube\\.com/(?:watch\\?v=|shorts/|v/|embed/)|youtu\\.be/)([A-Za-z0-9_-]{11})")

        text.split("\n").forEach { line ->
            val trimmedLine = line.trim()
            if (trimmedLine.isBlank()) return@forEach

            val matcher = regex.matcher(trimmedLine)
            if (matcher.find()) {
                val videoId = matcher.group(1)
                if (videoId != null && seenIds.add(videoId)) {
                    // Extract text on the same line that isn't the URL
                    val lineWithoutUrl = matcher.replaceAll("").trim()
                        .removePrefix("-").removeSuffix("-").trim()
                    val inlineTitle = if (lineWithoutUrl.length > 2) lineWithoutUrl else null
                    result.add(ExtractedVideoRef(videoId, inlineTitle))
                }
            } else {
                // Fallback for line-separated tokens or plain URLs
                trimmedLine.split(" ", "\r", "\t").forEach { token ->
                    val parsed = YouTubeLinkParser.parse(token.trim())
                    if (parsed?.videoId != null && seenIds.add(parsed.videoId)) {
                        result.add(ExtractedVideoRef(parsed.videoId, null))
                    }
                }
            }
        }
        return result
    }

    // Helper to extract YouTube video IDs from raw text
    fun parseTextToVideoIds(text: String): List<String> {
        return parseTextToVideoRefs(text).map { it.videoId }
    }

    // Launcher for file picker (.TXT or plain text)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        val reader = BufferedReader(InputStreamReader(inputStream))
                        val content = reader.readText()
                        withContext(Dispatchers.Main) {
                            pastedText = content
                            fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "fichier.txt"
                            extractedVideoIds = parseTextToVideoIds(content)
                        }
                    }
                } catch (e: Exception) {
                    // Ignore errors
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.FileUpload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.txt_import_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.txt_import_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_close))
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Step 1: File selection or text paste
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.txt_step_source),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        OutlinedButton(
                            onClick = { filePickerLauncher.launch("text/*") },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = stringResource(R.string.txt_file_button))
                        }
                    }

                    if (fileName != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = fileName ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = pastedText,
                        onValueChange = {
                            pastedText = it
                            fileName = null
                            extractedVideoIds = parseTextToVideoIds(it)
                        },
                        placeholder = { Text(stringResource(R.string.txt_paste_hint)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        shape = RoundedCornerShape(12.dp),
                        maxLines = 6
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Detected count badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (extractedVideoIds.isNotEmpty()) Icons.Rounded.CheckCircle else Icons.Rounded.Link,
                            contentDescription = null,
                            tint = if (extractedVideoIds.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (extractedVideoIds.isNotEmpty())
                                pluralStringResource(R.plurals.txt_detected_count, extractedVideoIds.size, extractedVideoIds.size)
                            else
                                stringResource(R.string.txt_none_detected),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (extractedVideoIds.isNotEmpty()) FontWeight.Bold else FontWeight.Normal,
                            color = if (extractedVideoIds.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Step 2: Destination selection
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.txt_step_destination),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Choice 1: New Playlist
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { createNewPlaylist = true }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = createNewPlaylist,
                            onClick = { createNewPlaylist = true }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.txt_new_playlist),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }

                    if (createNewPlaylist) {
                        OutlinedTextField(
                            value = playlistNameInput,
                            onValueChange = { playlistNameInput = it },
                            label = { Text(stringResource(R.string.txt_playlist_name)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 36.dp, top = 4.dp, bottom = 8.dp),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                    }

                    // Choice 2: Existing Playlist
                    if (availableLocalPlaylists.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { createNewPlaylist = false }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = !createNewPlaylist,
                                onClick = { createNewPlaylist = false }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.txt_existing_playlist),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }

                        if (!createNewPlaylist) {
                            val selectedPlaylist = availableLocalPlaylists.find { it.id == selectedPlaylistId }
                                ?: availableLocalPlaylists.first()

                            ExposedDropdownMenuBox(
                                expanded = isDropdownExpanded,
                                onExpandedChange = { isDropdownExpanded = !isDropdownExpanded },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 36.dp, top = 4.dp, bottom = 8.dp)
                            ) {
                                OutlinedTextField(
                                    value = selectedPlaylist.name,
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDropdownExpanded) },
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                ExposedDropdownMenu(
                                    expanded = isDropdownExpanded,
                                    onDismissRequest = { isDropdownExpanded = false }
                                ) {
                                    availableLocalPlaylists.forEach { item ->
                                        DropdownMenuItem(
                                            text = { Text(item.name) },
                                            onClick = {
                                                selectedPlaylistId = item.id
                                                isDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Button
            Button(
                onClick = {
                    if (extractedVideoIds.isEmpty() || isImporting) return@Button
                    isImporting = true

                    scope.launch(Dispatchers.IO) {
                        val finalPlaylistName = if (createNewPlaylist) {
                            playlistNameInput.trim().ifEmpty { defaultPlaylistName }
                        } else {
                            userPlaylists.find { it.id == selectedPlaylistId }?.name ?: defaultPlaylistName
                        }

                        val refs = parseTextToVideoRefs(pastedText)
                        val refsToUse = if (refs.isNotEmpty()) refs else extractedVideoIds.map { ExtractedVideoRef(it) }

                        // Resolve video details in parallel using OEmbed & YouTubeTitleParser
                        val songsToImport = kotlinx.coroutines.coroutineScope {
                            refsToUse.map { ref ->
                                async {
                                    fetchYouTubeSongMetadata(ref.videoId, finalPlaylistName, ref.inlineTitle)
                                }
                            }.awaitAll()
                        }

                        val targetId = if (createNewPlaylist) {
                            viewModel.createLocalPlaylistWithSongs(
                                name = finalPlaylistName,
                                description = context.resources.getQuantityString(
                                    R.plurals.txt_imported_description, songsToImport.size, songsToImport.size
                                ),
                                songs = songsToImport
                            )
                        } else {
                            val target = selectedPlaylistId ?: availableLocalPlaylists.firstOrNull()?.id
                            if (target != null) {
                                viewModel.addSongsToLocalPlaylist(target, songsToImport)
                                target
                            } else {
                                viewModel.createLocalPlaylistWithSongs(
                                    name = finalPlaylistName,
                                    description = context.resources.getQuantityString(
                                    R.plurals.txt_imported_description, songsToImport.size, songsToImport.size
                                ),
                                    songs = songsToImport
                                )
                            }
                        }

                        withContext(Dispatchers.Main) {
                            isImporting = false
                            onDismiss()
                            onImportSuccess(finalPlaylistName, songsToImport.size)
                        }
                    }
                },
                enabled = extractedVideoIds.isNotEmpty() && !isImporting,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                if (isImporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(stringResource(R.string.txt_importing))
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (extractedVideoIds.isNotEmpty())
                            pluralStringResource(R.plurals.txt_import_n, extractedVideoIds.size, extractedVideoIds.size)
                        else
                            stringResource(R.string.txt_start_import),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

/**
 * Fetches real song metadata for [videoId] via YouTube OEmbed or falls back to inline/parsed title.
 */
private suspend fun fetchYouTubeSongMetadata(
    videoId: String,
    fallbackPlaylistName: String,
    inlineTitle: String? = null
): Song = withContext(Dispatchers.IO) {
    if (!inlineTitle.isNullOrBlank()) {
        val (cleanTitle, cleanArtist) = com.ivor.ivormusic.data.YouTubeTitleParser.parseTitleAndArtist(inlineTitle)
        return@withContext Song.fromYouTube(
            videoId = videoId,
            title = cleanTitle,
            artist = cleanArtist,
            album = fallbackPlaylistName,
            duration = 0L,
            thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        )
    }

    try {
        val url = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$videoId&format=json"
        val request = okhttp3.Request.Builder().url(url).build()
        val client = okhttp3.OkHttpClient()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (!body.isNullOrBlank()) {
                    val json = org.json.JSONObject(body)
                    val rawTitle = json.optString("title")
                    val rawAuthor = json.optString("author_name")
                    val thumbUrl = json.optString("thumbnail_url").takeIf { it.isNotBlank() }
                        ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                    if (rawTitle.isNotBlank()) {
                        val (title, artist) = com.ivor.ivormusic.data.YouTubeTitleParser.parseTitleAndArtist(rawTitle, rawAuthor)
                        return@withContext Song.fromYouTube(
                            videoId = videoId,
                            title = title,
                            artist = artist,
                            album = fallbackPlaylistName,
                            duration = 0L,
                            thumbnailUrl = thumbUrl
                        )
                    }
                }
            }
        }
    } catch (_: Exception) { }

    Song.fromYouTube(
        videoId = videoId,
        title = "YouTube $videoId",
        artist = "YouTube",
        album = fallbackPlaylistName,
        duration = 0L,
        thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
    )
}

