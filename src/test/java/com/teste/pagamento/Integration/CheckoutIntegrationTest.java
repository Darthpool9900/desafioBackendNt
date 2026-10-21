
package com.teste.pagamento.Integration;

import com.teste.pagamento.Config.RabbitMQConfig;
import com.teste.pagamento.Connections.RedisConnect;
import com.teste.pagamento.Messaging.OutboxPublisherService;
import com.teste.pagamento.repositories.CheckoutRepository;
import com.teste.pagamento.repositories.OutboxEventRepository;
import com.teste.pagamento.repositories.ProcessedEventRepository;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class CheckoutIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("pagamento_test")
                    .withUsername("test")
                    .withPassword("test");

    @Container
    static final RabbitMQContainer rabbit =
            new RabbitMQContainer("rabbitmq:4-management");

    @Container
    static final GenericContainer<?> redis =
            new GenericContainer<>(
                    DockerImageName.parse("redis:7-alpine")
            ).withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry){
        registry.add("spring.datasource.url",postgres::getJdbcUrl);
        registry.add("spring.datasource.username",postgres::getUsername);
        registry.add("spring.datasource.password",postgres::getPassword);

        registry.add("spring.rabbitmq.host",rabbit::getHost);
        registry.add("spring.rabbitmq.port",rabbit::getAmqpPort);
        registry.add("spring.rabbitmq.username",rabbit::getAdminUsername);
        registry.add("spring.rabbitmq.password",rabbit::getAdminPassword);

        registry.add("app.redis.host",redis::getHost);
        registry.add("app.redis.port",() -> redis.getMappedPort(6379));

        registry.add("spring.jpa.open-in-view",() -> "false");
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    CheckoutRepository checkouts;

    @Autowired
    OutboxEventRepository outbox;

    @Autowired
    ProcessedEventRepository processed;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    OutboxPublisherService publisher;

    @Autowired
    RedisConnect redisConnect;

    @Autowired
    JdbcTemplate jdbc;

    private final HttpClient client = HttpClient.newHttpClient();

    record Result(int status,JsonNode body) {}

    private Result post(String path,String json) throws Exception{
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:"+port+path)
                )
                .header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        return new Result(
                response.statusCode(),
                mapper.readTree(response.body())
        );
    }

    private String cart() throws Exception{
        Result result = post(
                "/operations/pre-checkout",
                "{\"products\":[{\"slug\":\"premium\"}]}"
        );

        assertEquals(200,result.status());

        String id = result.body().path("cartId").asText();

        assertEquals(36,id.length());

        return id;
    }

    private Result pix(String id) throws Exception{
        return post(
                "/operations/checkout",
                "{\"cartId\":\""+id+"\",\"paymentMethod\":{\"name\":\"Pix\"}}"
        );
    }

    private void await(BooleanSupplier condition) throws Exception{
        long deadline = System.nanoTime()
                + Duration.ofSeconds(20).toNanos();

        while(System.nanoTime()<deadline){
            if(condition.getAsBoolean()){
                return;
            }

            Thread.sleep(150);
        }

        fail("Condição não atingida em 20 segundos");
    }

    @Test
    void checkoutPersistsOrderAndOutboxAtomically() throws Exception{
        String id = cart();

        Result result = pix(id);

        assertEquals(200,result.status());
        assertEquals(
                "APROVADO",
                result.body().path("status").asText()
        );

        assertTrue(checkouts.existsById(id));

        assertEquals(
                1,
                outbox.findAll().stream()
                        .filter(e -> e.getPayload().contains(id))
                        .count()
        );

        assertEquals(
                1L,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM checkout_products WHERE cart_id = ?",
                        Long.class,
                        id
                )
        );
    }

    @Test
    void secondCheckoutCannotApproveSameCartAgain() throws Exception{
        String id = cart();

        assertEquals(200,pix(id).status());
        assertEquals(409,pix(id).status());

        assertEquals(
                1L,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM checkouts WHERE cart_id = ?",
                        Long.class,
                        id
                )
        );
    }

    @Test
    void redisCartHasTtlAndLockPreventsParallelCheckout() throws Exception{
        String id = cart();

        assertTrue(redisConnect.GetInformation(id).has("products"));

        RedisClient client = RedisClient.create(
                RedisURI.Builder.redis(
                        redis.getHost(),
                        redis.getMappedPort(6379)
                ).build()
        );

        try(var conn = client.connect()){
            long ttl = conn.sync().ttl(id);

            assertTrue(
                    ttl > 0 && ttl <= 1800,
                    "TTL deveria ser 1 a 1800 segundos, foi "+ttl
            );
        }finally{
            client.shutdown();
        }

        String token = redisConnect.AcquireLock(id);

        assertNotNull(token);

        try{
            assertNull(redisConnect.AcquireLock(id));
            assertEquals(409,pix(id).status());
        }finally{
            redisConnect.ReleaseLock(id,token);
        }

        assertEquals(200,pix(id).status());
    }

    @Test
    void invalidProductReturnsErrorWithoutCreatingCheckout() throws Exception{
        Result result = post(
                "/operations/pre-checkout",
                "{\"products\":[{\"slug\":\"not-found\"}]}"
        );

        assertEquals(404,result.status());

        assertEquals(
                "PRODUCT_NOT_FOUND",
                result.body().path("errorCode").asText()
        );
    }

    @Test
    void cardDeclinedDoesNotPersistCheckoutOrOutbox() throws Exception{
        String id = cart();

        Result result = post(
                "/operations/checkout",
                "{\"cartId\":\""+id+"\","
                        +"\"paymentMethod\":{"
                        +"\"name\":\"Cartão de Crédito\","
                        +"\"card\":{"
                        +"\"number\":\"4000000000000002\","
                        +"\"expiry\":\"12/30\","
                        +"\"cvv\":\"123\","
                        +"\"holderName\":\"Maria Teste\""
                        +"}}}"
        );

        assertNotEquals(200,result.status());
        assertFalse(checkouts.existsById(id));

        assertEquals(
                0,
                outbox.findAll().stream()
                        .filter(e -> e.getPayload().contains(id))
                        .count()
        );
    }

    @Test
    void consumerProcessesDuplicateMessageOnlyOnce() throws Exception{
        UUID eventId = UUID.randomUUID();

        MessageProperties props = new MessageProperties();

        props.setMessageId(eventId.toString());
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);

        Message message = new Message(
                "{\"eventType\":\"PAYMENT_APPROVED\"}"
                        .getBytes(StandardCharsets.UTF_8),
                props
        );

        rabbitTemplate.send(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.ROUTING_KEY,
                message
        );

        rabbitTemplate.send(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.ROUTING_KEY,
                message
        );

        await(() -> processed.existsById(eventId));

        assertEquals(
                1L,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM processed_events WHERE event_id = ?",
                        Long.class,
                        eventId
                )
        );
    }

    @Test
    void publisherDeliversOutboxToConsumer() throws Exception{
        String id = cart();

        assertEquals(200,pix(id).status());

        publisher.PublishPendingEvents();

        await(() ->
                outbox.findAll().stream()
                        .anyMatch(e ->
                                e.getPayload().contains(id) && e.isPublished()
                        )
        );

        UUID eventId = outbox.findAll().stream()
                .filter(e -> e.getPayload().contains(id))
                .findFirst()
                .orElseThrow()
                .getId();

        await(() -> processed.existsById(eventId));
    }

    @Test
    void failedOutboxInsertRollsBackCheckout() throws Exception{
        String id = cart();

        jdbc.execute(
                "CREATE OR REPLACE FUNCTION reject_outbox_test() "
                        +"RETURNS trigger LANGUAGE plpgsql AS $$ "
                        +"BEGIN "
                        +"IF NEW.cart_id = '"+id+"' THEN "
                        +"RAISE EXCEPTION 'outbox test rollback'; "
                        +"END IF; "
                        +"RETURN NEW; "
                        +"END $$"
        );

        jdbc.execute(
                "CREATE TRIGGER reject_outbox_test_trigger "
                        +"BEFORE INSERT ON outbox_events "
                        +"FOR EACH ROW "
                        +"EXECUTE FUNCTION reject_outbox_test()"
        );

        try{
            assertEquals(500,pix(id).status());

            assertFalse(checkouts.existsById(id));

            assertEquals(
                    0L,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM checkout_products WHERE cart_id = ?",
                            Long.class,
                            id
                    )
            );
        }finally{
            jdbc.execute(
                    "DROP TRIGGER IF EXISTS reject_outbox_test_trigger "
                            +"ON outbox_events"
            );

            jdbc.execute(
                    "DROP FUNCTION IF EXISTS reject_outbox_test()"
            );
        }
    }
}
