package org.scoula.notification.controller;

import lombok.RequiredArgsConstructor;
import org.scoula.notification.dto.NotificationResDTO;
import org.scoula.notification.service.NotificationService;
import org.scoula.security.account.domain.CustomUser;
import org.springframework.http.ResponseEntity;
import
        org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;

    // NOTI-01 목록
    @GetMapping
    public ResponseEntity<List<NotificationResDTO>> getNotifications(
            @AuthenticationPrincipal CustomUser user) {

        return ResponseEntity.ok(
                service.getNotifications(user.getMember().getMemberNo()));
    }

    // 헤더 배지용. 목록을 통째로 받지 않으려고 따로 둔다
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Integer>> getUnreadCount(
            @AuthenticationPrincipal CustomUser user) {

        int count =
                service.getUnreadCount(user.getMember().getMemberNo());
        return ResponseEntity.ok(Collections.singletonMap("count",
                count));
    }

    // NOTI-02 읽음
    @PatchMapping("/{notiNo}/read")
    public ResponseEntity<Void> markRead(
            @AuthenticationPrincipal CustomUser user,
            @PathVariable int notiNo) {

        service.markRead(user.getMember().getMemberNo(), notiNo);
        return ResponseEntity.noContent().build();
    }

    // NOTI-03 모두 읽음
    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllRead(
            @AuthenticationPrincipal CustomUser user) {

        service.markAllRead(user.getMember().getMemberNo());
        return ResponseEntity.noContent().build();
    }

    // NOTI-04 삭제
    @DeleteMapping("/{notiNo}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal CustomUser user,
            @PathVariable int notiNo) {

        service.delete(user.getMember().getMemberNo(), notiNo);
        return ResponseEntity.noContent().build();
    }
}