package cn.ekko.groupchat.document.service.image;

import cn.ekko.groupchat.config.GroupChatProperties;
import cn.ekko.groupchat.document.client.AliyunOssClient;
import cn.ekko.groupchat.document.client.ImageDescriptionClient;
import cn.ekko.groupchat.document.client.MineruParsedArchive;
import cn.ekko.groupchat.document.client.MineruParsedImage;
import cn.ekko.groupchat.document.entity.KnowledgeDocument;
import cn.ekko.groupchat.document.entity.KnowledgeImage;
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

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
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
}
