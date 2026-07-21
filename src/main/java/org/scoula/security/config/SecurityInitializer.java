package org.scoula.security.config;

import lombok.extern.log4j.Log4j2;
import org.springframework.security.web.context.AbstractSecurityWebApplicationInitializer;
import org.springframework.web.filter.CharacterEncodingFilter;
import org.springframework.web.multipart.support.MultipartFilter;

import javax.servlet.ServletContext;

@Log4j2
public class SecurityInitializer extends AbstractSecurityWebApplicationInitializer {
    // 문자셋 필터
    private CharacterEncodingFilter encodingFilter() {
        CharacterEncodingFilter encodingFilter = new CharacterEncodingFilter();
        encodingFilter.setEncoding("UTF-8");
        encodingFilter.setForceEncoding(true);
        return encodingFilter;
    }

    @Override
    protected void beforeSpringSecurityFilterChain(ServletContext servletContext) {
        log.warn("필터 설정 ================================");
        insertFilters(servletContext, encodingFilter(), new MultipartFilter());
    }

}
