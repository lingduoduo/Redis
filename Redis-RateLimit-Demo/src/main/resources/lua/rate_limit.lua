local now = tonumber(ARGV[1])
local requestMember = ARGV[2]

for i = 1, #KEYS do
    local argOffset = 3 + ((i - 1) * 2)
    local key = KEYS[i]
    local window = tonumber(ARGV[argOffset])
    local maxCount = tonumber(ARGV[argOffset + 1])

    redis.call('ZREMRANGEBYSCORE', key, 0, now - window)

    if redis.call('ZCARD', key) >= maxCount then
        return 0
    end
end

for i = 1, #KEYS do
    local argOffset = 3 + ((i - 1) * 2)
    local key = KEYS[i]
    local window = tonumber(ARGV[argOffset])
    local member = requestMember .. ':' .. i

    redis.call('ZADD', key, now, member)
    redis.call('PEXPIRE', key, window)
end

return 1
