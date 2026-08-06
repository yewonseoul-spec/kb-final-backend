package org.scoula.consumption.mapper;

import org.scoula.consumption.domain.ExpectedSpendingVO;
import org.scoula.consumption.domain.SpendingVO;

import java.util.List;

public interface ConsumptionMapper {

    List<SpendingVO> selectSpendingByMonth(Integer memberNo, String yearMonth);

    List<ExpectedSpendingVO> selectExpectedByMonth(Integer memberNo, String yearMonth);

    void insertExpected(ExpectedSpendingVO vo);

    void updateExpected(ExpectedSpendingVO vo);

    void deleteExpected(Long expectedNo);

    void replaceExpected();

    List<SpendingVO> selectRecentSpendingWithMerchant(Integer memberNo);

    int countExpectedByCondition(ExpectedSpendingVO condition);
}
