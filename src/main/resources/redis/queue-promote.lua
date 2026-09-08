local time = redis.call('TIME')
local now = (time[1] * 1000) + math.floor(time[2] / 1000)
local expiresAt = now + tonumber(ARGV[3])

redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', now)
local users = redis.call('ZRANGE', KEYS[1], 0, tonumber(ARGV[2]) - 1)

for _, userId in ipairs(users) do
    redis.call('ZADD', KEYS[2], expiresAt, userId)
    redis.call('ZREM', KEYS[1], userId)
end

if #users > 0 then
    redis.call('PEXPIREAT', KEYS[2], expiresAt)
end

local remaining = redis.call('ZCARD', KEYS[1])
if remaining == 0 then
    redis.call('ZREM', KEYS[3], ARGV[1])
else
    redis.call('ZADD', KEYS[3], now, ARGV[1])
end

return {#users, remaining}
