package org.scoula.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.scoula.notification.domain.NotificationVO;
import org.scoula.notification.dto.NotificationResDTO;
import org.scoula.notification.mapper.NotificationMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;

@Log4j2
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper mapper;
    private final StringRedisTemplate redisTemplate;

    private static final int DEADLINE_DAYS = 14;

    // 같은 회원의 마감 알림 생성 쿼리를 하루에 한 번만 돌리기 위한 표식
    private static final Duration DEADLINE_TTL = Duration.ofHours(24);

    // 미읽음 개수 캐시. 무효화가 본체이고 TTL 은 이중 안전망이다
    private static final Duration UNREAD_TTL = Duration.ofMinutes(10);

    @Transactional
    @Override
    public List<NotificationResDTO> getNotifications(int memberNo) {
        ensureDeadlineNotifications(memberNo);
        return mapper.findByMemberNo(memberNo);
    }

    @Override
    public int getUnreadCount(int memberNo) {
        String key = unreadKey(memberNo);

        String cached = safeGet(key);
        if (cached != null) {
            return Integer.parseInt(cached);
        }

        int count = mapper.countUnread(memberNo);
        safeSet(key, String.valueOf(count), UNREAD_TTL);
        return count;
    }

    @Transactional
    @Override
    public void markRead(int memberNo, int notiNo) {
        if (mapper.markRead(memberNo, notiNo) == 0) {
            throw new NoSuchElementException();
        }
        invalidateUnread(memberNo);
    }

    @Transactional
    @Override
    public void markAllRead(int memberNo) {
        mapper.markAllRead(memberNo);   // 0 건이어도 정상이다
        invalidateUnread(memberNo);
    }

    @Transactional
    @Override
    public void delete(int memberNo, int notiNo) {
        if (mapper.delete(memberNo, notiNo) == 0) {
            throw new NoSuchElementException();
        }
        invalidateUnread(memberNo);
    }

    @Override
    public void notifyPasswordChanged(int memberNo) {
        save(memberNo, "SECURITY", null,
                "비밀번호가 변경되었어요. 본인이 아니라면 즉시 다시 변경해 주세요.");
    }

    @Override
    public void notifyProfileUpdated(int memberNo) {
        save(memberNo, "ACCOUNT", null, "내 정보가 수정되었어요.");
    }

    // ---------- 내부 ----------

    private void save(int memberNo, String type, Integer refNo, String
            content) {
        mapper.insert(NotificationVO.builder()
                .memberNo(memberNo)
                .notiType(type)
                .refNo(refNo)
                .content(content)
                .build());
        invalidateUnread(memberNo);
    }

    /*
     * 하루에 한 번만 생성 쿼리를 돌린다.
     * 표식이 사라져도(Redis 재시작) 매퍼의 NOT EXISTS 가 중복을 막으므로
     * 최악의 결과는 쿼리가 한 번 더 도는 것뿐이다.
     */
    private void ensureDeadlineNotifications(int memberNo) {
        String key = "noti:deadline:" + memberNo;

        Boolean first = safeSetIfAbsent(key, "1", DEADLINE_TTL);
        if (Boolean.FALSE.equals(first)) {
            return;   // 오늘 이미 돌렸다
        }
        // null 이면 Redis 가 답을 못 준 것 — 그냥 진행한다

        int created = mapper.insertDeadline(memberNo, DEADLINE_DAYS);
        if (created > 0) {
            invalidateUnread(memberNo);
        }
    }

    private String unreadKey(int memberNo) {
        return "noti:unread:" + memberNo;
    }

    private void invalidateUnread(int memberNo) {
        safeDelete(unreadKey(memberNo));
    }

    // Redis 가 죽어도 알림 기능 자체는 계속 동작해야 한다
    private String safeGet(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("redis get 실패 - key={}", key, e);
            return null;
        }
    }

    private void safeSet(String key, String value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
        } catch (Exception e) {
            log.warn("redis set 실패 - key={}", key, e);
        }
    }

    private Boolean safeSetIfAbsent(String key, String value, Duration
            ttl) {
        try {
            return redisTemplate.opsForValue().setIfAbsent(key, value,
                    ttl);
        } catch (Exception e) {
            log.warn("redis setIfAbsent 실패 - key={}", key, e);
            return null;
        }
    }

    private void safeDelete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("redis delete 실패 - key={}", key, e);
        }
    }
}