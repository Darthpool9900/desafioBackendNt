package com.teste.pagamento.Connections;

import com.teste.pagamento.ExceptionHandlers.CheckoutException;
import com.teste.pagamento.data.CheckoutError;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RedisConnectTest {
    @Test void insertionUsesThirtyMinuteTtl(){
        RedisConnect redis = new RedisConnect();
        StatefulRedisConnection<String,String> connection = mock(StatefulRedisConnection.class);
        RedisCommands<String,String> commands = mock(RedisCommands.class);
        when(connection.sync()).thenReturn(commands);
        redis.connection = connection;
        var payload = new ObjectMapper().readTree("{\"cartId\":\"abc\"}");
        redis.InsertRedis("abc",payload);
        verify(commands).setex(eq("abc"),eq(1800L),anyString());
    }
    @Test void missingKeyThrowsCartNotFound(){
        RedisConnect redis = new RedisConnect();
        StatefulRedisConnection<String,String> connection = mock(StatefulRedisConnection.class);
        RedisCommands<String,String> commands = mock(RedisCommands.class);
        when(connection.sync()).thenReturn(commands);
        redis.connection = connection;
        assertEquals(CheckoutError.CART_NOT_FOUND,assertThrows(CheckoutException.class,() -> redis.GetInformation("missing")).getError());
    }
    @Test void lockUnavailableReturnsNull(){
        RedisConnect redis = new RedisConnect();
        StatefulRedisConnection<String,String> connection = mock(StatefulRedisConnection.class);
        RedisCommands<String,String> commands = mock(RedisCommands.class);
        when(connection.sync()).thenReturn(commands);
        redis.connection = connection;
        assertNull(redis.AcquireLock(UUID.randomUUID().toString()));
        verify(commands).set(startsWith("lock:checkout:"),anyString(),any(io.lettuce.core.SetArgs.class));
    }
}
