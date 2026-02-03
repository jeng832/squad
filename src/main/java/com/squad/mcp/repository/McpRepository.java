package com.squad.mcp.repository;

import com.squad.mcp.domain.Mcp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * MCP JPA Repository.
 */
public interface McpRepository extends JpaRepository<Mcp, Long> {

    Optional<Mcp> findByName(String name);
}
