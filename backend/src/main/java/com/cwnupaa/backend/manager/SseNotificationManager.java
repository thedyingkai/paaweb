package com.cwnupaa.backend.manager;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class SseNotificationManager implements DisposableBean {
    private static final int MAX_CONNECTIONS = 300;
    // Map 存储 userId 和对应的 SseEmitter
    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public SseNotificationManager() {
        // 每15秒发送一次心跳，防止连接超时
        scheduler.scheduleAtFixedRate(this::sendHeartbeat, 15, 15, TimeUnit.SECONDS);
    }

    @Override
    public void destroy() {
        emitters.values().forEach(SseEmitter::complete);
        emitters.clear();
        scheduler.shutdownNow();
    }

    public synchronized SseEmitter createEmitter(Long userId) {
        SseEmitter previous = emitters.remove(userId);
        if (previous != null) previous.complete();
        if (emitters.size() >= MAX_CONNECTIONS) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "实时通知连接已满，请稍后重试");
        }

        // 设置超时时间为 30 分钟 (1800000 毫秒)
        SseEmitter emitter = new SseEmitter(1800000L);
        emitters.put(userId, emitter);

        // 条件删除，避免旧连接的异步回调误删同一用户刚建立的新连接。
        emitter.onCompletion(() -> emitters.remove(userId, emitter));
        emitter.onTimeout(() -> emitters.remove(userId, emitter));
        emitter.onError((e) -> emitters.remove(userId, emitter));
        
        return emitter;
    }

    public void send(Long userId, Object data) {
        sendEvent(userId, "notification", data);
    }

    /**
     * 向指定用户推送命名事件。鉴权状态变更与普通站内通知共用一条 SSE 连接，
     * 避免浏览器为同一账号建立多条长连接。
     */
    public void sendEvent(Long userId, String eventName, Object data) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException e) {
                emitters.remove(userId, emitter);
                emitter.completeWithError(e);
            } catch (IllegalStateException e) {
                // A response can complete before its removal callback runs. A stale
                // notification connection must not fail the caller's HTTP request.
                emitters.remove(userId, emitter);
            }
        }
    }

    public int activeConnectionCount() {
        return emitters.size();
    }

    private void sendHeartbeat() {
        emitters.forEach((userId, emitter) -> {
            try {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (IOException e) {
                emitters.remove(userId, emitter);
                emitter.completeWithError(e);
            } catch (IllegalStateException e) {
                emitters.remove(userId, emitter);
            }
        });
    }
}
