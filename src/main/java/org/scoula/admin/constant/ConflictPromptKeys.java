package org.scoula.admin.constant;

/**
 * 중복수혜 분석이 쓰는 프롬프트 키.
 *
 * 이 값을 여러 클래스에 각자 적어두면
 * 한쪽만 고쳤을 때 서로 다른 프롬프트를 보게 된다.
 * 분석하는 쪽과 판정하는 쪽이 같은 세대를 가리켜야 하므로 한 곳에 둔다.
 *
 * @author 박상호
 * @since 2026-08-21
 */
public final class ConflictPromptKeys {

    public static final String CONFLICT_DETECTION = "CONFLICT_DETECTION";

    private ConflictPromptKeys() {}
}
