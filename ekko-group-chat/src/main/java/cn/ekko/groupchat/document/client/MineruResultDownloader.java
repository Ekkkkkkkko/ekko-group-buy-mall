package cn.ekko.groupchat.document.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;

/**
 * MinerU 解析结果下载器，从 full_zip_url 下载结果 ZIP 包字节。
 *
 * <p>使用独立的 {@code RestClient}（不带 MinerU API 鉴权头），
 * 因为下载地址是 CDN 预签名链接。
 */
@Component
public class MineruResultDownloader {

    private final RestClient restClient;

    public MineruResultDownloader(
            @Qualifier("mineruResultRestClient") RestClient restClient
    ) {
        this.restClient = restClient;
    }

    public byte[] download(String fullZipUrl) {
        byte[] archive = restClient.get()
                .uri(URI.create(fullZipUrl))
                .retrieve()
                .body(byte[].class);
        if (archive == null || archive.length == 0) {
            throw new IllegalStateException("MinerU 结果 ZIP 为空");
        }
        return archive;
    }
}
