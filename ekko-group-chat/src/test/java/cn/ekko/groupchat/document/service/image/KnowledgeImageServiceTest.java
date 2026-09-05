package cn.ekko.groupchat.document.service.image;

import cn.ekko.groupchat.config.GroupChatProperties;
import cn.ekko.groupchat.document.client.AliyunOssClient;
import cn.ekko.groupchat.document.client.ImageDescriptionClient;
import cn.ekko.groupchat.document.client.MineruParsedArchive;
import cn.ekko.groupchat.document.client.MineruParsedImage;
import cn.ekko.groupchat.document.entity.KnowledgeDocument;
import cn.ekko.groupchat.document.entity.KnowledgeImage;
import cn.ekko.groupchat.document.entity.KnowledgeImageStatus;
import cn.ekko.groupchat.document.mapper.KnowledgeChunkImageMapper;
import cn.ekko.groupchat.document.mapper.KnowledgeImageMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.imageio.ImageIO;
import java.time.Duration;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeImageServiceTest {

    @Mock
    private KnowledgeImageMapper imageMapper;
    @Mock
    private KnowledgeChunkImageMapper chunkImageMapper;
    @Mock
    private AliyunOssClient ossClient;
    @Mock
    private ImageDescriptionClient descriptionClient;

    @BeforeAll
    static void initializeMybatisMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "knowledge-image-test"),
                KnowledgeImage.class
        );
    }

    @Test
    void uploadsImageDescribesItAndRewritesMarkdownToStableId() {
        GroupChatProperties properties = new GroupChatProperties();
        properties.getOss().setParsedPrefix("knowledge/parsed");
        properties.getImage().setSignedUrlExpiration(Duration.ofMinutes(10));
        when(imageMapper.insert(any(KnowledgeImage.class))).thenAnswer(invocation -> {
            KnowledgeImage image = invocation.getArgument(0);
            image.setId(88L);
            return 1;
        });
        when(imageMapper.updateById(any(KnowledgeImage.class))).thenReturn(1);
        when(ossClient.presignGet(anyString(), any(Duration.class)))
                .thenReturn("https://signed.example/router.png");
        when(descriptionClient.describe(any(), anyString(), anyString()))
                .thenReturn("路由器背面包含一个2.5G接口和三个千兆接口");

        KnowledgeImageService service = new KnowledgeImageService(
                imageMapper, chunkImageMapper, ossClient, descriptionClient, properties
        );
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(15L);
        document.setSha256("doc-sha");
        document.setTitle("TL-7DR6430 产品介绍");
        document.setProductModel("TL-7DR6430");
        MineruParsedImage image = new MineruParsedImage(
                "result/images/router.png",
                "router.png",
                "image/png",
                "png",
                new byte[]{1, 2, 3},
                "image-sha"
        );
        MineruParsedArchive archive = new MineruParsedArchive(
                "result/full.md",
                "# 接口\n\n![背部接口](images/router.png)",
                List.of(image)
        );

        ImageProcessingResult result = service.process(document, archive);

        assertThat(result.imageCount()).isEqualTo(1);
        assertThat(result.processedMarkdown())
                .contains("![背部接口](knowledge-image://88)")
                .contains("图片说明：路由器背面包含一个2.5G接口和三个千兆接口")
                .doesNotContain("images/router.png");
        verify(ossClient).put(
                "knowledge/parsed/15/doc-sha/images/image-sha.png",
                image.content(),
                "image/png"
        );
    }

    @Test
    void excludesConfirmedPlaceholderFromExistingChunksButKeepsUsefulUndescribedImages() {
        GroupChatProperties properties = new GroupChatProperties();
        properties.getImage().setExcludedSha256(List.of("placeholder-sha"));
        KnowledgeImage placeholder = storedImage(594L, "placeholder-sha");
        KnowledgeImage useful = storedImage(596L, "diagram-sha");
        when(imageMapper.selectByIds(any())).thenReturn(List.of(placeholder, useful));
        when(ossClient.presignGet("images/596.jpg", properties.getImage().getSignedUrlExpiration()))
                .thenReturn("https://signed.example/diagram.jpg");

        List<KnowledgeImageReference> result = service(properties).resolve(
                "![](knowledge-image://594) ![](knowledge-image://596)"
        );

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().imageId()).isEqualTo(596L);
        assertThat(result.getFirst().sha256()).isEqualTo("diagram-sha");
        verify(ossClient, never()).presignGet(org.mockito.ArgumentMatchers.eq("images/594.jpg"), any());
    }

    @Test
    void retainsOriginalPlaceholderForTraceabilityButSkipsDescriptionAndNewMarkdownReference() {
        GroupChatProperties properties = new GroupChatProperties();
        properties.getImage().setExcludedSha256(List.of("placeholder-sha"));
        when(imageMapper.insert(any(KnowledgeImage.class))).thenAnswer(invocation -> {
            KnowledgeImage image = invocation.getArgument(0);
            image.setId(594L);
            return 1;
        });
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(95L);
        document.setSha256("doc-sha");
        MineruParsedImage placeholder = new MineruParsedImage(
                "images/placeholder.jpg", "placeholder.jpg", "image/jpeg", "jpg",
                new byte[]{1}, "placeholder-sha"
        );
        ImageProcessingResult result = service(properties).process(document, new MineruParsedArchive(
                "full.md", "复位前须知\n![](images/placeholder.jpg)", List.of(placeholder)
        ));

        assertThat(result.processedMarkdown()).contains("复位前须知").doesNotContain("knowledge-image://");
        assertThat(result.imageCount()).isEqualTo(1);
        verify(ossClient).put(anyString(), any(byte[].class), org.mockito.ArgumentMatchers.eq("image/jpeg"));
        verifyNoInteractions(descriptionClient);
    }

    @Test
    void excludesNewMineruRedGlyphWithoutRequiringItsSha256ToBePreconfigured() throws Exception {
        when(imageMapper.insert(any(KnowledgeImage.class))).thenAnswer(invocation -> {
            KnowledgeImage image = invocation.getArgument(0);
            image.setId(700L);
            return 1;
        });
        MineruParsedImage placeholder = new MineruParsedImage(
                "images/red-x.png", "red-x.png", "image/png", "png", redBrokenImageGlyph(), "new-sha"
        );

        ImageProcessingResult result = service(new GroupChatProperties()).process(document(96L), new MineruParsedArchive(
                "full.md", "说明\n![](images/red-x.png)", List.of(placeholder)
        ));

        assertThat(result.processedMarkdown()).contains("说明").doesNotContain("knowledge-image://");
        verify(imageMapper).insert(org.mockito.ArgumentMatchers.<KnowledgeImage>argThat(
                image -> image.getStatus() == KnowledgeImageStatus.EXCLUDED
        ));
        verifyNoInteractions(descriptionClient);
    }

    @Test
    void signingFailureDoesNotPreventTextReferencesOrOtherImages() {
        GroupChatProperties properties = new GroupChatProperties();
        when(imageMapper.selectByIds(any())).thenReturn(List.of(storedImage(1L, "sha1"), storedImage(2L, "sha2")));
        when(ossClient.presignGet("images/1.jpg", properties.getImage().getSignedUrlExpiration()))
                .thenThrow(new IllegalStateException("OSS unavailable"));
        when(ossClient.presignGet("images/2.jpg", properties.getImage().getSignedUrlExpiration()))
                .thenReturn("https://signed.example/2.jpg");
        assertThat(service(properties).resolve("![](knowledge-image://1) ![](knowledge-image://2)"))
                .extracting(KnowledgeImageReference::imageId).containsExactly(2L);
    }

    private KnowledgeImageService service(GroupChatProperties properties) {
        return new KnowledgeImageService(imageMapper, chunkImageMapper, ossClient, descriptionClient, properties);
    }

    private KnowledgeImage storedImage(long id, String sha) {
        KnowledgeImage image = new KnowledgeImage();
        image.setId(id);
        image.setSha256(sha);
        image.setObjectKey("images/" + id + ".jpg");
        return image;
    }

    private KnowledgeDocument document(long id) {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(id);
        document.setSha256("doc-sha");
        return document;
    }

    private byte[] redBrokenImageGlyph() throws Exception {
        BufferedImage image = new BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, 300, 300);
        graphics.setColor(Color.RED);
        graphics.fillRect(80, 90, 42, 25);
        graphics.fillRect(178, 90, 42, 25);
        graphics.fillRect(108, 110, 84, 80);
        graphics.fillRect(80, 185, 42, 25);
        graphics.fillRect(178, 185, 42, 25);
        graphics.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
