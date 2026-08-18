package org.scoula.notification.mapper;

import org.apache.ibatis.annotations.Param;
import org.scoula.notification.domain.NotificationVO;
import org.scoula.notification.dto.NotificationResDTO;

import java.util.List;

public interface NotificationMapper {

    List<NotificationResDTO> findByMemberNo(int memberNo);

    int countUnread(int memberNo);

    int insert(NotificationVO vo);

    // memberNo 를 WHERE 에 같이 넣어 남의 알림을 못 건드리게 한다
    int markRead(@Param("memberNo") int memberNo, @Param("notiNo") int
            notiNo);

    int markAllRead(int memberNo);

    int delete(@Param("memberNo") int memberNo, @Param("notiNo") int
            notiNo);
}