package org.scoula.notification.service;

import org.scoula.notification.dto.NotificationResDTO;

import java.util.List;

public interface NotificationService {

    List<NotificationResDTO> getNotifications(int memberNo);

    int getUnreadCount(int memberNo);

    void markRead(int memberNo, int notiNo);

    void markAllRead(int memberNo);

    void delete(int memberNo, int notiNo);

    // 다른 도메인이 부르는 두 개
    void notifyPasswordChanged(int memberNo);

    void notifyProfileUpdated(int memberNo);
}