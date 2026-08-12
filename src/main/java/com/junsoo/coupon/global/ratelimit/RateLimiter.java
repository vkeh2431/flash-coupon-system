package com.junsoo.coupon.global.ratelimit;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.redisson.client.codec.StringCodec;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RateLimiter {

    private static final String SCRIPT_PATH = "scripts/ratelimit.lua";

    private final RedissonClient redissonClient;
    private final RateLimitProperties properties;

    private String source;
    private String sha;

    @PostConstruct
    void loadScript() {
        this.source = readScript();
        this.sha = script().scriptLoad(source);
    }

    // bucket은 "무엇을 한 바구니로 셀 것인가"다. 발급은 "issue:{campaignId}:{userId}".
    public boolean tryAcquire(String bucket) {
        List<Object> keys = List.of(windowKey(bucket));
        try {
            return evalSha(keys);
        } catch (RedisException e) {
            // Redis가 재시작하면 올려둔 스크립트가 사라진다. 한 번만 다시 올리고 재시도한다.
            if (e.getMessage() == null || !e.getMessage().contains("NOSCRIPT")) {
                throw e;
            }
            this.sha = script().scriptLoad(source);
            return evalSha(keys);
        }
    }

    // 창이 바뀌면 키 자체가 바뀐다. 카운터를 되돌리는 일이 따로 없고 TTL이 지난 창을 치운다.
    private String windowKey(String bucket) {
        long window = Instant.now().getEpochSecond() / properties.windowSeconds();
        return "rl:" + bucket + ":" + window;
    }

    private boolean evalSha(List<Object> keys) {
        Long allowed = script().evalSha(RScript.Mode.READ_WRITE, sha, RScript.ReturnType.INTEGER, keys,
                String.valueOf(properties.limit()), String.valueOf(properties.windowSeconds()));
        return allowed != null && allowed == 1L;
    }

    // 기본 코덱은 키와 값을 직렬화해서 넣는다. Lua가 평문으로 읽으려면 StringCodec이어야 한다.
    private RScript script() {
        return redissonClient.getScript(StringCodec.INSTANCE);
    }

    private String readScript() {
        try (InputStream in = new ClassPathResource(SCRIPT_PATH).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(SCRIPT_PATH + " 을 읽지 못했다", e);
        }
    }
}
