package org.scoula.notification.mapper;

import org.apache.ibatis.annotations.Param;
import org.scoula.notification.domain.NotificationVO;
import org.scoula.notification.dto.NotificationResDTO;

import java.util.List;

public interface NotificationMapper {

    List<NotificationResDTO> findByMemberNo(int memberNo);

    int countUnread(int memberNo);

    int insert(NotificationVO vo);

    // 관심 혜택 중 마감이 임박했고 아직 안 만든 것만 골라 한 번에 넣는다
    int insertDeadline(@Param("memberNo") int memberNo, @Param("days") int
            days);

    // memberNo 를 WHERE 에 같이 넣어 남의 알림을 못 건드리게 한다
    int markRead(@Param("memberNo") int memberNo, @Param("notiNo") int
            notiNo);

    int markAllRead(int memberNo);

    int delete(@Param("memberNo") int memberNo, @Param("notiNo") int
            notiNo);
}