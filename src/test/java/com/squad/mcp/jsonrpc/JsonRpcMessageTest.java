package com.squad.mcp.jsonrpc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JsonRpcMessageTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    @DisplayName("JsonRpcRequest")
    class JsonRpcRequestTest {

        @Test
        @DisplayName("파라미터 있는 요청을 생성한다")
        void createWithParams() {
            JsonNode params = objectMapper.createObjectNode().put("key", "value");

            JsonRpcRequest request = JsonRpcRequest.of("1", "test/method", params);

            assertThat(request.jsonrpc()).isEqualTo("2.0");
            assertThat(request.id()).isEqualTo("1");
            assertThat(request.method()).isEqualTo("test/method");
            assertThat(request.params()).isNotNull();
        }

        @Test
        @DisplayName("파라미터 없는 요청을 생성한다")
        void createWithoutParams() {
            JsonRpcRequest request = JsonRpcRequest.of("2", "test/method");

            assertThat(request.jsonrpc()).isEqualTo("2.0");
            assertThat(request.id()).isEqualTo("2");
            assertThat(request.method()).isEqualTo("test/method");
            assertThat(request.params()).isNull();
        }

        @Test
        @DisplayName("JSON 직렬화 시 null params는 제외된다")
        void serializeExcludesNullParams() throws Exception {
            JsonRpcRequest request = JsonRpcRequest.of("1", "test/method");

            String json = objectMapper.writeValueAsString(request);

            assertThat(json).doesNotContain("params");
        }

        @Test
        @DisplayName("JSON 직렬화/역직렬화 라운드트립이 성공한다")
        void serializationRoundTrip() throws Exception {
            JsonNode params = objectMapper.createObjectNode().put("name", "test");
            JsonRpcRequest original = JsonRpcRequest.of("1", "initialize", params);

            String json = objectMapper.writeValueAsString(original);
            JsonRpcRequest deserialized = objectMapper.readValue(json, JsonRpcRequest.class);

            assertThat(deserialized).isEqualTo(original);
        }
    }

    @Nested
    @DisplayName("JsonRpcResponse")
    class JsonRpcResponseTest {

        @Test
        @DisplayName("성공 응답은 isError가 false이다")
        void successResponseIsNotError() throws Exception {
            String json = """
                    {"jsonrpc":"2.0","id":"1","result":{"tools":[]}}
                    """;

            JsonRpcResponse response = objectMapper.readValue(json, JsonRpcResponse.class);

            assertThat(response.isError()).isFalse();
            assertThat(response.id()).isEqualTo("1");
            assertThat(response.result()).isNotNull();
        }

        @Test
        @DisplayName("에러 응답은 isError가 true이다")
        void errorResponseIsError() throws Exception {
            String json = """
                    {"jsonrpc":"2.0","id":"1","error":{"code":-32600,"message":"Invalid Request"}}
                    """;

            JsonRpcResponse response = objectMapper.readValue(json, JsonRpcResponse.class);

            assertThat(response.isError()).isTrue();
            assertThat(response.error().code()).isEqualTo(-32600);
            assertThat(response.error().message()).isEqualTo("Invalid Request");
        }

        @Test
        @DisplayName("알 수 없는 필드가 포함된 응답도 역직렬화된다")
        void ignoresUnknownFields() throws Exception {
            String json = """
                    {"jsonrpc":"2.0","id":"1","result":{},"_meta":{"extra":"data"}}
                    """;

            JsonRpcResponse response = objectMapper.readValue(json, JsonRpcResponse.class);

            assertThat(response.id()).isEqualTo("1");
            assertThat(response.isError()).isFalse();
        }
    }

    @Nested
    @DisplayName("JsonRpcNotification")
    class JsonRpcNotificationTest {

        @Test
        @DisplayName("파라미터 있는 알림을 생성한다")
        void createWithParams() {
            JsonNode params = objectMapper.createObjectNode();

            JsonRpcNotification notification = JsonRpcNotification.of("notifications/initialized", params);

            assertThat(notification.jsonrpc()).isEqualTo("2.0");
            assertThat(notification.method()).isEqualTo("notifications/initialized");
            assertThat(notification.params()).isNotNull();
        }

        @Test
        @DisplayName("파라미터 없는 알림을 생성한다")
        void createWithoutParams() {
            JsonRpcNotification notification = JsonRpcNotification.of("notifications/initialized");

            assertThat(notification.params()).isNull();
        }
    }

    @Nested
    @DisplayName("JsonRpcError")
    class JsonRpcErrorTest {

        @Test
        @DisplayName("data가 포함된 에러를 역직렬화한다")
        void deserializeWithData() throws Exception {
            String json = """
                    {"code":-32602,"message":"Invalid params","data":{"detail":"missing field"}}
                    """;

            JsonRpcError error = objectMapper.readValue(json, JsonRpcError.class);

            assertThat(error.code()).isEqualTo(-32602);
            assertThat(error.message()).isEqualTo("Invalid params");
            assertThat(error.data()).isNotNull();
        }
    }
}
