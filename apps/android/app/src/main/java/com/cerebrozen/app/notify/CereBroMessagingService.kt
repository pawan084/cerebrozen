package com.cerebrozen.app.notify

import com.cerebrozen.app.net.Api
import com.cerebrozen.app.net.Session
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The FCM entry point. Deliberately thin — every decision lives in [Push], which
 * is unit-testable; this class only exists because Firebase needs a Service.
 *
 * Never registered anywhere without a `google-services.json`: Firebase's
 * initialisation provider finds no configuration, so the service is simply
 * never started and the app runs exactly as before.
 */
class CereBroMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        // Data-only payload (see fcm.build_message server-side), so this runs in
        // every app state and the deeplink survives.
        Push.show(applicationContext, message.data)
    }

    /**
     * FCM rotates tokens on reinstall, restore-to-new-device and app-data clear.
     * Without this the install goes quiet and nothing anywhere reports it.
     */
    override fun onNewToken(token: String) {
        if (!Session.signedIn) return
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                Api.registerDevice(token, "android", com.cerebrozen.app.BuildConfig.VERSION_NAME)
                Session.prefPut("fcm_token", token)
            }
        }
    }
}
