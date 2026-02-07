package com.squad.llm.config;

import com.squad.common.exception.ErrorCode;
import com.squad.common.exception.ValidationException;
import com.squad.llm.tool.LlmToolExecutor;
import com.squad.llm.tool.LlmToolResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LlmToolConfig {

    @Bean
    @ConditionalOnMissingBean(LlmToolExecutor.class)
    public LlmToolExecutor unsupportedToolExecutor() {
        return toolCall -> {
            throw new ValidationException(
                    ErrorCode.INVALID_REQUEST,
                    "Tool executor가 등록되지 않았습니다."
            );
        };
    }
}
