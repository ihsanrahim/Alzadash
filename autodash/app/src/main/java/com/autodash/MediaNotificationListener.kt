package com.autodash

import android.service.notification.NotificationListenerService

/** Exists only so Android lets the Now Playing widget read the active media sessions. */
class MediaNotificationListener : NotificationListenerService()
