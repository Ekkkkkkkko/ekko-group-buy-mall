package cn.ekko.groupchat.chat.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 问答来源关联的知识图片，URL 为本次响应生成的短期 OSS 签名地址。 */
@Getter
@RequiredArgsConstructor
public class ChatImageResponse {

    private final long imageId;
    private final String description;
    private final String url;
    private final String sha256;
}
