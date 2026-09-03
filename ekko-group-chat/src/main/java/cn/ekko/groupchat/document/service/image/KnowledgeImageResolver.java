package cn.ekko.groupchat.document.service.image;

import java.util.List;

/** 根据命中分片中的稳定图片标识解析可展示图片。 */
@FunctionalInterface
public interface KnowledgeImageResolver {

    List<KnowledgeImageReference> resolve(String chunkText);
}
