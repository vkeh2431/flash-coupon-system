-- 고정 윈도우 카운터.
-- INCR과 EXPIRE가 갈라지면 INCR 직후 죽었을 때 TTL 없는 키가 남아 그 유저가 영구 차단된다.
--
-- KEYS[1] rl:{bucket}:{창 번호}
-- ARGV[1] 창당 허용 건수
-- ARGV[2] 창 길이(초) = TTL

local count = redis.call('INCR', KEYS[1])
if count == 1 then
    redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2]))
end
if count > tonumber(ARGV[1]) then
    return 0
end
return 1