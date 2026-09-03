package cn.ekko.groupchat.config;

import cn.ekko.groupchat.document.client.MineruClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ClientConfigurationTest {

    @Test
    void mineruCreateTaskRequestContainsUrlAndExplicitContentLength() {
        GroupChatProperties properties = new GroupChatProperties();
        properties.getMineru().setBaseUrl("https://mineru.test");
        properties.getMineru().setApiKey("test-token");

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = new ClientConfiguration().mineruRestClient(builder, properties);

        server.expect(requestTo("https://mineru.test/api/v4/extract/task"))
                .andExpect(method(POST))
                .andExpect(request -> {
                    assertThat(request).isInstanceOf(MockClientHttpRequest.class);
                    MockClientHttpRequest mockRequest = (MockClientHttpRequest) request;
                    byte[] body = mockRequest.getBodyAsBytes();
                    String json = new String(body, StandardCharsets.UTF_8);

                    assertThat(request.getHeaders().getFirst("Authorization"))
                            .isEqualTo("Bearer test-token");
                    assertThat(request.getHeaders().getContentLength()).isEqualTo(body.length);
                    assertThat(json)
                            .contains("\"url\":\"https://oss.example.com/manual.pdf\"")
                            .contains("\"model_version\":\"vlm\"")
                            .contains("\"data_id\":\"document-11\"");
                })
                .andRespond(withSuccess(
                        "{\"code\":0,\"data\":{\"task_id\":\"task-123\"},\"trace_id\":\"trace-123\"}",
                        MediaType.APPLICATION_JSON
                ));

        MineruClient mineruClient = new MineruClient(restClient, properties);

        assertThat(mineruClient.createTask(
                "https://oss.example.com/manual.pdf",
                "document-11"
        )).isEqualTo("task-123");
        server.verify();
    }
}
