package cn.ekko.groupchat.document.service.image;

/** 聊天来源中可展示的图片及本次生成的短期签名地址。 */
public record KnowledgeImageReference(long imageId, String description, String url, String sha256) {
}
