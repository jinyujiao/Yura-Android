package com.yura.app.reader

import android.app.Application
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CompletableDeferred

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MediaService : MediaSessionService() {

    inner class Binder : android.os.Binder() {
        private var ttsMediaSession: MediaSession? = null

        fun openTtsSession(
            player: Player,
            sessionId: String,
            onPrevious: () -> Unit,
            onNext: () -> Unit,
            onStop: () -> Unit,
        ) {
            closeTtsSession()

            val stopCommand = SessionCommand(TTS_COMMAND_STOP, Bundle.EMPTY)
            val compactNextExtras = Bundle().apply {
                putInt(DefaultMediaNotificationProvider.COMMAND_KEY_COMPACT_VIEW_INDEX, 2)
            }
            val mediaButtons = listOf(
                CommandButton.Builder(CommandButton.ICON_PREVIOUS)
                    .setDisplayName("上一句")
                    .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .setSlots(CommandButton.SLOT_BACK)
                    .build(),
                CommandButton.Builder(CommandButton.ICON_NEXT)
                    .setDisplayName("下一句")
                    .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .setSlots(CommandButton.SLOT_FORWARD)
                    .setExtras(compactNextExtras)
                    .build(),
                CommandButton.Builder(CommandButton.ICON_STOP)
                    .setDisplayName("停止")
                    .setSessionCommand(stopCommand)
                    .setSlots(CommandButton.SLOT_OVERFLOW)
                    .build(),
            )
            val sessionPlayer = TtsSessionPlayer(player, onPrevious, onNext, onStop)
            val callback = object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                ): MediaSession.ConnectionResult {
                    val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                        .buildUpon()
                        .add(stopCommand)
                        .build()
                    return MediaSession.ConnectionResult.accept(
                        sessionCommands,
                        MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS,
                    )
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle,
                ): ListenableFuture<SessionResult> {
                    return when (customCommand.customAction) {
                        TTS_COMMAND_STOP -> {
                            onStop()
                            Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                        }
                        else -> Futures.immediateFuture(
                            SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED),
                        )
                    }
                }
            }

            ttsMediaSession = MediaSession.Builder(applicationContext, sessionPlayer)
                .setSessionActivity(createSessionActivityIntent())
                .setId(sessionId)
                .setCallback(callback)
                .setCustomLayout(mediaButtons)
                .setMediaButtonPreferences(mediaButtons)
                .build()
                .also { addSession(it) }
        }

        fun closeTtsSession() {
            ttsMediaSession?.release()
            ttsMediaSession = null
        }

        fun currentTtsSession(): MediaSession? = ttsMediaSession

        private fun createSessionActivityIntent(): PendingIntent {
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags = flags or PendingIntent.FLAG_IMMUTABLE
            }
            val intent = application.packageManager.getLaunchIntentForPackage(application.packageName)
            return PendingIntent.getActivity(applicationContext, 0, intent, flags)
        }
    }

    private class TtsSessionPlayer(
        player: Player,
        private val onPrevious: () -> Unit,
        private val onNext: () -> Unit,
        private val onStop: () -> Unit,
    ) : ForwardingPlayer(player) {
        override fun getAvailableCommands(): Player.Commands =
            super.getAvailableCommands()
                .buildUpon()
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(Player.COMMAND_STOP)
                .build()

        override fun isCommandAvailable(command: Int): Boolean =
            command == Player.COMMAND_SEEK_TO_PREVIOUS ||
                command == Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM ||
                command == Player.COMMAND_SEEK_TO_NEXT ||
                command == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM ||
                command == Player.COMMAND_STOP ||
                super.isCommandAvailable(command)

        override fun hasPreviousMediaItem(): Boolean = true
        override fun hasNextMediaItem(): Boolean = true
        override fun getPreviousMediaItemIndex(): Int = currentMediaItemIndex
        override fun getNextMediaItemIndex(): Int = currentMediaItemIndex
        override fun seekToPrevious() = onPrevious()
        override fun seekToPreviousMediaItem() = onPrevious()
        override fun seekToNext() = onNext()
        override fun seekToNextMediaItem() = onNext()
        override fun stop() = onStop()
    }

    private val binder by lazy { Binder() }

    override fun onBind(intent: Intent?): IBinder? {
        return if (intent?.action == SERVICE_INTERFACE) {
            super.onBind(intent)
            binder
        } else {
            super.onBind(intent)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        binder.currentTtsSession()

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (binder.currentTtsSession()?.player?.isPlaying != true) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        binder.closeTtsSession()
        super.onDestroy()
    }

    companion object {
        const val SERVICE_INTERFACE = "com.yura.app.reader.MediaService"
        private const val TTS_COMMAND_STOP = "com.yura.app.tts.STOP"

        suspend fun bind(application: Application): Binder {
            val deferred = CompletableDeferred<Binder>()
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder) {
                    deferred.complete(service as Binder)
                }

                override fun onServiceDisconnected(name: ComponentName?) = Unit
                override fun onNullBinding(name: ComponentName?) {
                    if (!deferred.isCompleted) {
                        deferred.completeExceptionally(IllegalStateException("Failed to bind MediaService."))
                    }
                }
            }
            application.bindService(intent(application), connection, 0)
            return deferred.await()
        }

        private fun intent(application: Application): Intent =
            Intent(SERVICE_INTERFACE).apply { setClass(application, MediaService::class.java) }
    }
}
