-- 판정과 차감이 갈라지면 그 사이에 다른 요청이 끼어들어 초과 발급이 난다.
--
-- KEYS[1] campaign:{id}:stock   String  남은 재고
-- KEYS[2] campaign:{id}:issued  Set     발급받은 userId
-- KEYS[3] campaign:{id}:meta    Hash    opensAt · closesAt (epoch millis) · paused ('0'/'1')
-- ARGV[1] userId

local meta = redis.call('HMGET', KEYS[3], 'opensAt', 'closesAt', 'paused')
if not meta[1] then
    return 'NOT_FOUND'
end

-- 시각은 Redis 시계로 판정한다. 앱이 여러 대여도 시계는 하나다.
local time = redis.call('TIME')
local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)

if now < tonumber(meta[1]) then
    return 'NOT_OPENED'
end
if now > tonumber(meta[2]) then
    return 'CLOSED'
end
if meta[3] == '1' then
    return 'PAUSED'
end

if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
    return 'ALREADY_ISSUED'
end

local stock = tonumber(redis.call('GET', KEYS[1]))
if stock == nil or stock <= 0 then
    return 'OUT_OF_STOCK'
end

redis.call('DECR', KEYS[1])
redis.call('SADD', KEYS[2], ARGV[1])
return 'OK'
