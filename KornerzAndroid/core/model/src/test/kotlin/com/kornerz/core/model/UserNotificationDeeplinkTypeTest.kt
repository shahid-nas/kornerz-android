package com.kornerz.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The notification type parser.
 *
 * Before P10 the comparison was case-**sensitive** against the
 * lower-camel spellings only, so every real notification — which
 * carries iOS's capitalised `DeeplinkType` raw value — parsed as
 * [UserNotificationDeeplinkType.UNKNOWN] and no notification in the
 * app was routable.
 */
class UserNotificationDeeplinkTypeTest {

    @Test
    fun `parses the iOS capitalised raw values`() {
        assertEquals(
            UserNotificationDeeplinkType.NEW_EVENT,
            UserNotificationDeeplinkType.fromRaw("NewEvent"),
        )
        assertEquals(
            UserNotificationDeeplinkType.NEW_ARTICLE,
            UserNotificationDeeplinkType.fromRaw("NewArticle"),
        )
        assertEquals(
            UserNotificationDeeplinkType.NEW_FORUM,
            UserNotificationDeeplinkType.fromRaw("NewForum"),
        )
        assertEquals(
            UserNotificationDeeplinkType.VIDEO_CALL,
            UserNotificationDeeplinkType.fromRaw("VideoCall"),
        )
        assertEquals(
            UserNotificationDeeplinkType.NEW_COMMENT,
            UserNotificationDeeplinkType.fromRaw("NewComment"),
        )
        assertEquals(
            UserNotificationDeeplinkType.NEW_LOOP,
            UserNotificationDeeplinkType.fromRaw("NewLoop"),
        )
        assertEquals(
            UserNotificationDeeplinkType.LOOP_COMMENT,
            UserNotificationDeeplinkType.fromRaw("LoopComment"),
        )
    }

    @Test
    fun `parses the legacy lower-camel spellings`() {
        assertEquals(
            UserNotificationDeeplinkType.NEW_EVENT,
            UserNotificationDeeplinkType.fromRaw("newEvent"),
        )
        assertEquals(
            UserNotificationDeeplinkType.LOOP_COMMENT,
            UserNotificationDeeplinkType.fromRaw("loopComment"),
        )
    }

    @Test
    fun `is case-insensitive`() {
        assertEquals(
            UserNotificationDeeplinkType.NEW_LOOP,
            UserNotificationDeeplinkType.fromRaw("newloop"),
        )
        assertEquals(
            UserNotificationDeeplinkType.NEW_LOOP,
            UserNotificationDeeplinkType.fromRaw("NEWLOOP"),
        )
        assertEquals(
            UserNotificationDeeplinkType.NEW_LOOP,
            UserNotificationDeeplinkType.fromRaw("NewLoop"),
        )
    }

    @Test
    fun `an unknown or missing type is UNKNOWN`() {
        assertEquals(UserNotificationDeeplinkType.UNKNOWN, UserNotificationDeeplinkType.fromRaw(null))
        assertEquals(UserNotificationDeeplinkType.UNKNOWN, UserNotificationDeeplinkType.fromRaw(""))
        assertEquals(UserNotificationDeeplinkType.UNKNOWN, UserNotificationDeeplinkType.fromRaw("Nope"))
    }
}
