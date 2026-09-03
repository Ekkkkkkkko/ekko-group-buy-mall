package cn.ekko.groupchat.document.service;

import cn.ekko.groupchat.document.entity.KnowledgeChunk;
import cn.ekko.groupchat.document.mapper.KnowledgeChunkMapper;
import cn.ekko.groupchat.document.service.chunk.DocumentChunk;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.ArrayList;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 负责知识分片在 MySQL 中的整体替换、查询和删除。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeChunkService {

    private final KnowledgeChunkMapper chunkMapper;
    private final StringRedisTemplate redisTemplate;
    private final cn.ekko.groupchat.config.GroupChatProperties properties;

    /** 同一文档重新切分时先删旧分片，再保存本次完整切分结果。 */
    @Transactional
    public void replaceAll(long documentId, List<DocumentChunk> chunks) {
        deleteByDocumentId(documentId);
        for (DocumentChunk chunk : chunks) {
            KnowledgeChunk entity = toEntity(documentId, chunk);
            if (chunkMapper.insert(entity) != 1) {
                throw new IllegalStateException("保存知识分片失败: " + chunk.getChunkId());
            }
        }
    }

    /** 批量读取命中的父分片，避免逐条查询造成 N+1。 */
    public Map<String, KnowledgeChunk> findByChunkIds(Collection<String> chunkIds) {
        if (chunkIds == null || chunkIds.isEmpty()) {
            return Map.of();
        }
        Map<String, KnowledgeChunk> result = new LinkedHashMap<>();
        List<String> misses = new ArrayList<>();
        try {
            for (String chunkId : chunkIds) {
                String cached = redisTemplate.opsForValue().get(cacheKey(chunkId));
                if (cached == null) {
                    misses.add(chunkId);
                } else if (!cached.isEmpty()) {
                    KnowledgeChunk parent = new KnowledgeChunk();
                    parent.setChunkId(chunkId);
                    parent.setChunkType(cn.ekko.groupchat.document.service.chunk.ChunkType.PARENT);
                    parent.setContent(cached);
                    result.put(chunkId, parent);
                }
            }
        } catch (RuntimeException exception) {
            log.warn("读取父分片 Redis 缓存失败，回退 MySQL", exception);
            misses = new ArrayList<>(chunkIds);
            result.clear();
        }

        if (!misses.isEmpty()) {
            Map<String, KnowledgeChunk> stored = chunkMapper.selectByIds(misses).stream()
                    .collect(Collectors.toMap(
                            KnowledgeChunk::getChunkId,
                            Function.identity(),
                            (left, right) -> left,
                            LinkedHashMap::new
                    ));
            result.putAll(stored);
            cacheParents(misses, stored);
        }
        return result;
    }

    /** 按兄弟组批量读取全部分片，并按组内序号排序。 */
    public Map<String, List<KnowledgeChunk>> findByBrotherChunkIds(Collection<String> brotherChunkIds) {
        if (brotherChunkIds == null || brotherChunkIds.isEmpty()) {
            return Map.of();
        }
        return chunkMapper.selectList(Wrappers.<KnowledgeChunk>lambdaQuery()
                        .in(KnowledgeChunk::getBrotherChunkId, brotherChunkIds)
                        .orderByAsc(KnowledgeChunk::getBrotherChunkId)
                        .orderByAsc(KnowledgeChunk::getBrotherChunkIndex))
                .stream()
                .collect(Collectors.groupingBy(
                        KnowledgeChunk::getBrotherChunkId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    /** 向量化阶段从 MySQL 恢复本次已经提交的完整切片快照。 */
    public List<DocumentChunk> findDocumentChunks(long documentId) {
        return chunkMapper.selectList(Wrappers.<KnowledgeChunk>lambdaQuery()
                        .eq(KnowledgeChunk::getDocumentId, documentId)
                        .orderByAsc(KnowledgeChunk::getChunkIndex))
                .stream()
                .map(this::toDocumentChunk)
                .toList();
    }

    @Transactional
    public void deleteByDocumentId(long documentId) {
        List<String> chunkIds = chunkMapper.selectList(Wrappers.<KnowledgeChunk>lambdaQuery()
                        .eq(KnowledgeChunk::getDocumentId, documentId)
                        .select(KnowledgeChunk::getChunkId))
                .stream()
                .map(KnowledgeChunk::getChunkId)
                .toList();
        chunkMapper.delete(Wrappers.<KnowledgeChunk>lambdaQuery()
                .eq(KnowledgeChunk::getDocumentId, documentId));
        evict(chunkIds);
    }

    private KnowledgeChunk toEntity(long documentId, DocumentChunk chunk) {
        KnowledgeChunk entity = new KnowledgeChunk();
        entity.setChunkId(chunk.getChunkId());
        entity.setDocumentId(documentId);
        entity.setParentChunkId(chunk.getParentChunkId());
        entity.setBrotherChunkId(chunk.getBrotherChunkId());
        entity.setBrotherChunkIndex(chunk.getBrotherChunkIndex());
        entity.setBrotherChunkTotal(chunk.getBrotherChunkTotal());
        entity.setChunkType(chunk.getType());
        entity.setHeadingPath(chunk.getHeadingPath());
        entity.setChunkIndex(chunk.getChunkIndex());
        entity.setSearchable(chunk.isSearchable());
        entity.setContent(chunk.getText());
        return entity;
    }

    private DocumentChunk toDocumentChunk(KnowledgeChunk entity) {
        return new DocumentChunk(
                entity.getChunkId(),
                entity.getParentChunkId(),
                entity.getChunkType(),
                entity.getHeadingPath(),
                entity.getChunkIndex(),
                entity.getContent(),
                entity.getBrotherChunkId(),
                entity.getBrotherChunkIndex(),
                entity.getBrotherChunkTotal()
        );
    }

    private void cacheParents(List<String> requestedIds, Map<String, KnowledgeChunk> stored) {
        Duration ttl = properties.getRedis().getParentCacheTtl();
        try {
            for (String chunkId : requestedIds) {
                KnowledgeChunk chunk = stored.get(chunkId);
                redisTemplate.opsForValue().set(
                        cacheKey(chunkId), chunk == null ? "" : chunk.getContent(), ttl
                );
            }
        } catch (RuntimeException exception) {
            log.warn("写入父分片 Redis 缓存失败", exception);
        }
    }

    private void evict(Collection<String> chunkIds) {
        if (chunkIds == null || chunkIds.isEmpty()) {
            return;
        }
        try {
            redisTemplate.delete(chunkIds.stream().map(this::cacheKey).toList());
        } catch (RuntimeException exception) {
            log.warn("清理父分片 Redis 缓存失败", exception);
        }
    }

    private String cacheKey(String chunkId) {
        return "group-chat:knowledge:chunk:" + chunkId;
    }
}
