local time = redis.call('TIME')
local now = (time[1] * 1000) + math.floor(time[2] / 1000)
local activeUntil = redis.call('ZSCORE', KEYS[3], ARGV[1])

if activeUntil and tonumber(activeUntil) > now then
    return {2, 0, 0}
end

if activeUntil then
    redis.call('ZREM', KEYS[3], ARGV[1])
end

local waitingScore = redis.call('ZSCORE', KEYS[1], ARGV[1])
if waitingScore then
    local rank = redis.call('ZRANK', KEYS[1], ARGV[1])
    return {1, rank + 1, 0}
end

local sequence = redis.call('INCR', KEYS[2])
redis.call('ZADD', KEYS[1], sequence, ARGV[1])
redis.call('ZADD', KEYS[4], 'NX', now, ARGV[2])
local rank = redis.call('ZRANK', KEYS[1], ARGV[1])
return {1, rank + 1, 1}
