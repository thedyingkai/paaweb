package com.cwnupaa.backend.manager;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.test.util.ReflectionTestUtils;
import com.cwnupaa.backend.service.AuthStateChangeService;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

class SseNotificationManagerTests {

    @Test void completedConnectionCannotTurnAuthenticationRefreshIntoHttp500() {
        var manager = new SseNotificationManager();
        try {
            manager.createEmitter(42L).complete();
            assertThatCode(() -> new AuthStateChangeService(manager).notifyChanged(42L)).doesNotThrowAnyException();
            assertThat(manager.activeConnectionCount()).isZero();
        } finally { manager.destroy(); }
    }

    @Test void heartbeatContinuesAfterOneConnectionHasCompleted() {
        var manager = new SseNotificationManager();
        try {
            manager.createEmitter(42L).complete();
            manager.createEmitter(43L);
            assertThatCode(() -> ReflectionTestUtils.invokeMethod(manager,"sendHeartbeat")).doesNotThrowAnyException();
            assertThat(manager.activeConnectionCount()).isEqualTo(1);
            assertThatCode(() -> ReflectionTestUtils.invokeMethod(manager,"sendHeartbeat")).doesNotThrowAnyException();
        } finally { manager.destroy(); }
    }

    @SuppressWarnings("unchecked")
    @Test void failedOldSendCannotRemoveAReplacementConnection() throws Exception {
        var manager = new SseNotificationManager();
        try {
            var old = mock(SseEmitter.class);
            var connections=(Map<Long,SseEmitter>)ReflectionTestUtils.getField(manager,"emitters");
            connections.put(42L,old);
            doAnswer(call -> {
                manager.createEmitter(42L);
                throw new IllegalStateException("ResponseBodyEmitter has already completed");
            }).when(old).send(any(SseEmitter.SseEventBuilder.class));
            assertThatCode(() -> manager.sendEvent(42L,"auth-state-change",Map.of())).doesNotThrowAnyException();
            assertThat(manager.activeConnectionCount()).isEqualTo(1);
            assertThat(connections.get(42L)).isNotSameAs(old);
        } finally { manager.destroy(); }
    }

    @Test
    void replacesPreviousConnectionForSameUser() {
        SseNotificationManager manager = new SseNotificationManager();
        try {
            SseEmitter first = manager.createEmitter(42L);
            SseEmitter second = manager.createEmitter(42L);

            assertThat(second).isNotSameAs(first);
            assertThat(manager.activeConnectionCount()).isEqualTo(1);
        } finally {
            manager.destroy();
        }
    }
}
