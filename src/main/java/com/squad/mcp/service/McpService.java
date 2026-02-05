package com.squad.mcp.service;

import com.squad.common.exception.ErrorCode;
import com.squad.common.exception.NotFoundException;
import com.squad.common.exception.ValidationException;
import com.squad.mcp.domain.Mcp;
import com.squad.mcp.dto.McpCreateRequest;
import com.squad.mcp.dto.McpResponse;
import com.squad.mcp.dto.McpUpdateRequest;
import com.squad.mcp.repository.McpRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class McpService {

    private final McpRepository mcpRepository;

    public McpService(McpRepository mcpRepository) {
        this.mcpRepository = mcpRepository;
    }

    public List<McpResponse> findAll() {
        return mcpRepository.findAll().stream()
                .map(McpResponse::from)
                .toList();
    }

    public McpResponse findById(Long id) {
        Mcp mcp = mcpRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.MCP_NOT_FOUND));
        return McpResponse.from(mcp);
    }

    @Transactional
    public McpResponse create(McpCreateRequest request) {
        validateConfig(request.config());
        Mcp mcp = Mcp.builder()
                .name(request.name())
                .description(request.description())
                .config(request.config())
                .build();
        return McpResponse.from(mcpRepository.save(mcp));
    }

    @Transactional
    public McpResponse update(Long id, McpUpdateRequest request) {
        validateConfig(request.config());
        Mcp mcp = mcpRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.MCP_NOT_FOUND));
        mcp.update(request.name(), request.description(), request.config());
        return McpResponse.from(mcp);
    }

    @Transactional
    public void delete(Long id) {
        mcpRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.MCP_NOT_FOUND));
        mcpRepository.deleteById(id);
    }

    /**
     * config JSON에 필수 키 {@code command}가 존재하는지 검증합니다.
     */
    private void validateConfig(Map<String, Object> config) {
        if (!config.containsKey("command") || config.get("command") == null) {
            throw new ValidationException(ErrorCode.VALIDATION_FAILED, "config에 'command' 키가 필수입니다.");
        }
    }
}
