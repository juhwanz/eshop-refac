local time = redis.call('TIME')
local now = (time[1] * 1000) + math.floor(time[2] / 1000)
local activeUntil = redis.call('ZSCORE', KEYS[2], ARGV[1])

if activeUntil and tonumber(activeUntil) > now then
    return {2, 0}
end

if activeUntil then
    redis.call('ZREM', KEYS[2], ARGV[1])
end

local rank = redis.call('ZRANK', KEYS[1], ARGV[1])
if rank then
    return {1, rank + 1}
end

return {0, 0}
