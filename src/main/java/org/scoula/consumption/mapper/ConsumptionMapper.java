package org.scoula.consumption.mapper;

import org.apache.ibatis.annotations.Param;
import org.scoula.consumption.domain.ExpectedSpendingVO;
import org.scoula.consumption.domain.SpendingVO;

import java.util.List;

public interface ConsumptionMapper {
    List<SpendingVO> selectSpendingByMonth(
            @Param("memberNo") Long memberNo,
            @Param("yearMonth") String yearMonth
    );

    List<ExpectedSpendingVO> selectExpectedByMonth(
            @Param("memberNo") Long memberNo,
            @Param("yearMonth") String yearMonth
    );
}
