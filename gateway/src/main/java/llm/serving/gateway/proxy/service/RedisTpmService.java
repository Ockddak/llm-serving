package llm.serving.gateway.proxy.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * key 기준으로 requestedTokens를 차감해도 되는지 판단하고 된다면 바로 차감
 */
@Service
public class RedisTpmService {

    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<Long> tpmScript;

    public RedisTpmService(
            final StringRedisTemplate stringRedisTemplate
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        String lua = """
        local current = redis.call("GET", KEYS[1])
        if not current then current = "0" end
        local cur = tonumber(current)
        local requested = tonumber(ARGV[1])
        local limit = tonumber(ARGV[2])
        local ttl = tonumber(ARGV[3])
        if (cur + requested) > limit then
          return -1
        end
        local new = redis.call("INCRBY", KEYS[1], requested)
        if ttl and ttl > 0 then
          redis.call("EXPIRE", KEYS[1], ttl)
        end
        return new
        """;

        this.tpmScript = new DefaultRedisScript<>();
        this.tpmScript.setScriptText(lua);
        this.tpmScript.setResultType(Long.class);
    }

    public Mono<Long> tryConsume(String key, long requestedTokens, long limit, long ttlSeconds) {
        // blocking execute on boundedElastic
        return Mono.fromCallable(() -> {
            List<String> keys = List.of(key);
            Object res = stringRedisTemplate.execute(tpmScript, keys, String.valueOf(requestedTokens), String.valueOf(limit), String.valueOf(ttlSeconds));
            if (res == null) return -1L;
            return ((Number) res).longValue();
        }).subscribeOn(Schedulers.boundedElastic());
    }

}
