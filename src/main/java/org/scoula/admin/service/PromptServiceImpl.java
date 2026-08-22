package org.scoula.admin.service;

import lombok.RequiredArgsConstructor;
import org.scoula.admin.constant.AiPromptDefaults;
import org.scoula.admin.domain.AiPromptVO;
import org.scoula.admin.mapper.AiPromptMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PromptServiceImpl implements PromptService {

    private final AiPromptMapper aiPromptMapper;

    /**
     * 프롬프트 관리 기능이 죽어도 AI 기능 자체는 계속 돌아야 한다.
     * 조회에 실패하면 예외를 올리지 않고 코드에 박힌 기본값으로 떨어진다.
     */
    @Override
    public String get(String promptKey) {
        String fallback = AiPromptDefaults.DEFAULTS.get(promptKey);
        if (fallback == null) {
            throw new IllegalStateException("등록되지 않은 프롬프트 키입니다: " + promptKey);
        }
        return getOrDefault(promptKey, fallback);
    }

    /**
     * 폴백을 호출하는 쪽이 직접 넘긴다.
     * 소비 분석처럼 자기 상수를 이미 가진 기능은 그 상수를 그대로 넘기면 되고,
     * 그러면 기존 파일을 지우지 않아도 된다.
     */
    @Override
    public String getOrDefault(String promptKey, String fallback) {
        try {
            String content = aiPromptMapper.findActiveContent(promptKey);
            if (content != null && !content.trim().isEmpty()) {
                System.out.println("[프롬프트] PROMPT_SOURCE=DB / " + promptKey);
                return content;
            }
            System.out.println("[프롬프트] PROMPT_SOURCE=FALLBACK / DB에 활성 버전 없음: " + promptKey);
        } catch (Exception e) {
            System.out.println("[프롬프트] PROMPT_SOURCE=FALLBACK / 조회 실패: " + promptKey
                    + " / " + e.getMessage());
        }
        return fallback;
    }

    /**
     * 재현성을 위해 Candidate 에 함께 저장한다.
     * 폴백으로 떨어졌으면 null 이 맞다. 코드 기본값에는 버전 개념이 없기 때문이다.
     */
    @Override
    public Integer getActiveVersion(String promptKey) {
        try {
            return aiPromptMapper.findActiveVersion(promptKey);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 저장을 동반하는 경로에서 쓴다.
     *
     * 활성 버전이 없는 상태에서 분석을 계속하면
     * prompt_version 이 비어 있는 Candidate 가 쌓이고,
     * 그 행은 세대별 조회 어디에도 걸리지 않아 조용히 묻힌다.
     * 그래서 여기서 멈춘다.
     */
    @Override
    public Integer requireActiveVersion(String promptKey) {
        Integer version = getActiveVersion(promptKey);
        if (version == null) {
            throw new IllegalStateException(
                    "사용중인 프롬프트 버전이 없어 분석을 진행할 수 없습니다: " + promptKey);
        }
        return version;
    }

    /**
     * 사용중이 아닌 버전도 가져온다.
     *
     * 새 버전을 활성화하기 전에 결과를 먼저 쌓아보려면
     * 활성 버전을 건드리지 않고 그 버전으로 분석할 수 있어야 한다.
     */
    @Override
    public AiPromptVO getVersion(String promptKey, Integer version) {
        if (version == null) {
            throw new IllegalArgumentException("프롬프트 버전을 지정해야 합니다: " + promptKey);
        }
        AiPromptVO prompt = aiPromptMapper.findByKeyAndVersion(promptKey, version);
        if (prompt == null) {
            throw new IllegalStateException(
                    "해당 프롬프트 버전이 없습니다: " + promptKey + " v" + version);
        }
        return prompt;
    }

    @Override
    public List<AiPromptVO> getVersions(String promptKey) {
        return aiPromptMapper.findVersions(promptKey);
    }

    @Override
    public List<String> getKeys() {
        return aiPromptMapper.findKeys();
    }

    @Override
    public int createVersion(String promptKey, String content, String memo, Integer memberNo) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("프롬프트 본문이 비었습니다.");
        }
        AiPromptVO vo = new AiPromptVO();
        vo.setPromptKey(promptKey);
        vo.setVersion(aiPromptMapper.findNextVersion(promptKey));
        vo.setContent(content);
        vo.setMemo(memo);
        vo.setMemberNo(memberNo);
        aiPromptMapper.insertPrompt(vo);
        return vo.getPromptNo();
    }

    /**
     * 순서가 중요하다. 먼저 전부 내린 뒤 하나를 올린다.
     * 반대로 하면 잠깐 두 개가 Y 가 되고, 그 사이 호출이 어느 쪽을 볼지 알 수 없다.
     */
    @Override
    public void activate(String promptKey, int promptNo) {
        AiPromptVO target = aiPromptMapper.findByNo(promptNo);
        if (target == null || !promptKey.equals(target.getPromptKey())) {
            throw new IllegalArgumentException("해당 키의 버전이 아닙니다: " + promptNo);
        }
        aiPromptMapper.deactivateAll(promptKey);
        aiPromptMapper.activate(promptNo);
    }

    /**
     * 실험하다 보면 버전이 금방 쌓여 목록에서 무엇이 무엇인지 알기 어려워진다.
     * 그래서 지울 수 있게 하되 두 가지는 막는다.
     *
     * 사용중인 버전을 지우면 활성 버전이 사라져 코드 기본값으로 떨어진다.
     * 마지막 하나를 지우면 되돌아갈 곳이 없어진다.
     */
    @Override
    public void deleteVersion(String promptKey, int promptNo) {
        AiPromptVO target = aiPromptMapper.findByNo(promptNo);
        if (target == null || !promptKey.equals(target.getPromptKey())) {
            throw new IllegalArgumentException("해당 키의 버전이 아닙니다: " + promptNo);
        }
        if ("Y".equals(target.getIsActive())) {
            throw new IllegalStateException("사용중인 버전은 삭제할 수 없습니다. 다른 버전을 먼저 적용하세요.");
        }
        if (aiPromptMapper.countByKey(promptKey) <= 1) {
            throw new IllegalStateException("마지막 버전은 삭제할 수 없습니다.");
        }
        aiPromptMapper.deletePrompt(promptNo);
    }
}