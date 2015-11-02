function testAssert(b)
    total = total + 1
    if b then
        successes = successes + 1
    else
        print("Assertion failed:", debugstacktrace(nil, 2))
    end
end
function testCall(name, f, ...)
    if type(name) == "function" then name, f = f, name end
    assert(type(f) == "function", "expected a function, but got " .. tostring(f))

    local status, errormessage, stacktrace = pcall(f, ...)
    if status then
        errormessage, stacktrace = nil, nil
    end

    local stacktrace2 = debugstacktrace(nil, 3, nil, 1)
    assert(stacktrace == nil or type(stacktrace) == "string", type(stacktrace))
    assert(type(stacktrace2) == "string")
    if errormessage then
        print("Failed:", name, errormessage, stacktrace, stacktrace2)
        testAssert(false)
    else
        testAssert(true)
    end
end

totalTestCases, passedTestCases = 0, 0
for name, closure in pairs(tests) do
    print("Running:", name)
    successes = 0
    total = 1
    local outs = {pcall(closure)}
    if outs[1] then
        successes = successes + 1
    else
        print('Top-level failure in ' .. name .. ': ' .. outs[2])
        print(outs[3])
        print(outs[4])
    end
    print("    =>", successes .. "/" .. total)
    if successes == total then
        passedTestCases = passedTestCases + 1
    else
        print("Subtest failed in test: " .. name)
    end
    totalTestCases = totalTestCases + 1
end
print("Passed " .. passedTestCases .. "/" .. totalTestCases .. " test cases.")

success = (passedTestCases == totalTestCases)
