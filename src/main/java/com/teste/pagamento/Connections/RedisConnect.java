
package com.teste.pagamento.Connections;

import com.teste.pagamento.ExceptionHandlers.CheckoutException;
import com.teste.pagamento.data.CheckoutError;

import io.lettuce.core.*;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;

import java.util.UUID;

@Component
public class RedisConnect {

    @Value("${app.redis.host:localhost}")
    private String redisHost;

    @Value("${app.redis.port:6379}")
    private int redisPort;

    private RedisURI uri;
    StatefulRedisConnection<String,String> connection;
    RedisClient redisClient;
    ObjectMapper objectMapper = new ObjectMapper();

    public void connectBasic(){
        try{
            uri = RedisURI.Builder.redis(redisHost,redisPort).build();
            redisClient = RedisClient.create(uri);
            connection = redisClient.connect();
        }catch(RedisException error){
            throw new CheckoutException(CheckoutError.REDIS_UNAVAILABLE);
        }
    }

    @PostConstruct
    public void init(){
        connectBasic();
    }

    public void CloseConnection(){
        if(connection != null){
            connection.close();
        }
        if(redisClient != null){
            redisClient.shutdown();
        }
    }

    @PreDestroy
    public void destroy(){
        CloseConnection();
    }

    public JsonNode GetInformation(String value){
        try{
            if(connection == null){
                throw new CheckoutException(CheckoutError.REDIS_UNAVAILABLE,value);
            }

            RedisCommands<String,String> commands = connection.sync();
            String json = commands.get(value);

            if(json == null){
                throw new CheckoutException(CheckoutError.CART_NOT_FOUND,value);
            }

            return objectMapper.readTree(json);

        }catch (Exception e){
            if(e instanceof CheckoutException){
                throw (CheckoutException) e;
            }

            CheckoutError error = e instanceof RedisException
                    ? CheckoutError.REDIS_UNAVAILABLE
                    : CheckoutError.INTERNAL_ERROR;

            throw new CheckoutException(error,value);
        }
    }

    public JsonNode InsertRedis(String key,JsonNode data){
        try{
            RedisCommands<String,String> commands = connection.sync();

            commands.setex(key,1800,objectMapper.writeValueAsString(data));

            return data;
        }catch(Exception e){
            e.printStackTrace();

            CheckoutError error = e instanceof RedisException
                    ? CheckoutError.REDIS_UNAVAILABLE
                    : CheckoutError.INTERNAL_ERROR;

            throw new CheckoutException(error,key);
        }
    }

    public JsonNode UpdateRedis(String key,JsonNode data){
        try{
            RedisCommands<String,String> commands = connection.sync();

            String result = commands.set(
                    key,
                    objectMapper.writeValueAsString(data),
                    SetArgs.Builder.xx().keepttl()
            );

            if(result == null){
                throw new CheckoutException(CheckoutError.CART_NOT_FOUND,key);
            }

            return data;
        }catch (Exception e){
            if(e instanceof CheckoutException){
                throw (CheckoutException) e;
            }

            throw new CheckoutException(CheckoutError.REDIS_UNAVAILABLE,key);
        }
    }

    public boolean DeleteRedis(String key){
        try{
            RedisCommands<String,String> commands = connection.sync();

            Long result = commands.del(key);

            if(result == 0){
                throw new CheckoutException(CheckoutError.CART_NOT_FOUND,key);
            }

            return true;
        }catch (Exception e){
            if(e instanceof CheckoutException){
                throw (CheckoutException) e;
            }

            throw new CheckoutException(CheckoutError.REDIS_UNAVAILABLE,key);
        }
    }

    public String AcquireLock(String cartId){
        try{
            String key = "lock:checkout:"+cartId;
            String token = UUID.randomUUID().toString();

            String result = connection.sync().set(
                    key,
                    token,
                    SetArgs.Builder.nx().ex(30)
            );

            return "OK".equals(result) ? token : null;

        }catch (RedisException e){
            throw new CheckoutException(CheckoutError.REDIS_UNAVAILABLE,cartId);
        }
    }

    public void ReleaseLock(String cartId,String token){
        try{
            String key = "lock:checkout:"+cartId;

            String script = """
                    if redis.call('GET',KEYS[1]) == ARGV[1] then
                        return redis.call('DEL',KEYS[1])
                    end
                    return 0
                    """;

            connection.sync().eval(
                    script,
                    ScriptOutputType.INTEGER,
                    new String[]{key},
                    token
            );

        }catch (RedisException e){
            throw new CheckoutException(CheckoutError.REDIS_UNAVAILABLE,cartId);
        }
    }
}
