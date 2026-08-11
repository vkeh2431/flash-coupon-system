package com.junsoo.coupon.domain.campaign;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RMap;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.redisson.client.codec.StringCodec;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class IssueGate {

    private static final String SCRIPT_PATH = "scripts/issue.lua";

    private final RedissonClient redissonClient;

    private String source;
    private String sha;

    @PostConstruct
    void loadScript() {
        this.source = readScript();
        this.sha = script().scriptLoad(source);
    }

    public IssueResult tryIssue(long campaignId, long userId) {
        List<Object> keys = List.of(stockKey(campaignId), issuedKey(campaignId), metaKey(campaignId));
        String raw;
        try {
            raw = evalSha(keys, userId);
        } catch (RedisException e) {
            // Redis가 재시작하면 올려둔 스크립트가 사라진다. 한 번만 다시 올리고 재시도한다.
            if (e.getMessage() == null || !e.getMessage().contains("NOSCRIPT")) {
                throw e;
            }
            this.sha = script().scriptLoad(source);
            raw = evalSha(keys, userId);
        }
        return IssueResult.valueOf(raw);
    }

    // meta를 마지막에 넣는다. 그 전까지 스크립트는 NOT_FOUND를 돌려주므로
    // 재고만 심어진 어중간한 상태에서 발급이 시작될 일이 없다.
    public void initialize(Campaign campaign) {
        long campaignId = campaign.getId();

        redissonClient.getSet(issuedKey(campaignId), StringCodec.INSTANCE).delete();
        redissonClient.getBucket(stockKey(campaignId), StringCodec.INSTANCE)
                .set(String.valueOf(campaign.getRemainingQuantity()));

        RMap<String, String> meta = redissonClient.getMap(metaKey(campaignId), StringCodec.INSTANCE);
        meta.delete();
        meta.putAll(Map.of(
                "opensAt", String.valueOf(toEpochMilli(campaign.getOpensAt())),
                "closesAt", String.valueOf(toEpochMilli(campaign.getClosesAt())),
                "paused", campaign.isPaused() ? "1" : "0"
        ));
    }

    public int remainingStock(long campaignId) {
        RBucket<String> stock = redissonClient.getBucket(stockKey(campaignId), StringCodec.INSTANCE);
        String value = stock.get();
        return value == null ? 0 : Integer.parseInt(value);
    }

    public void updatePaused(long campaignId, boolean paused) {
        redissonClient.getMap(metaKey(campaignId), StringCodec.INSTANCE)
                .fastPut("paused", paused ? "1" : "0");
    }

    private String evalSha(List<Object> keys, long userId) {
        return script().evalSha(RScript.Mode.READ_WRITE, sha, RScript.ReturnType.VALUE,
                keys, String.valueOf(userId));
    }

    // 기본 코덱은 키와 값을 직렬화해서 넣는다. Lua가 평문으로 읽으려면 StringCodec이어야 한다.
    private RScript script() {
        return redissonClient.getScript(StringCodec.INSTANCE);
    }

    // Lua는 epoch millis로 비교한다. LocalDateTime에는 존이 없으므로 앱의 기본 존으로 해석한다.
    private static long toEpochMilli(LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    static String stockKey(long campaignId) {
        return "campaign:" + campaignId + ":stock";
    }

    static String issuedKey(long campaignId) {
        return "campaign:" + campaignId + ":issued";
    }

    static String metaKey(long campaignId) {
        return "campaign:" + campaignId + ":meta";
    }

    private String readScript() {
        try (InputStream in = new ClassPathResource(SCRIPT_PATH).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(SCRIPT_PATH + " 을 읽지 못했다", e);
        }
    }
}
