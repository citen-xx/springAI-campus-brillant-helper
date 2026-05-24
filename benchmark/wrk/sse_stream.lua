wrk.method = "POST"
wrk.headers["Content-Type"] = "application/json"
-- Uncomment if you want to pass a token:
-- wrk.headers["Authorization"] = "Bearer <TOKEN>"

wrk.body = [[
{
  "prompt": "Please explain the scholarship application process.",
  "query": "Please explain the scholarship application process.",
  "conversationId": "wrk-conv-1"
}
]]

response = function(status, headers, body)
  if status ~= 200 then
    io.stderr:write("non-200 status=" .. status .. "\n")
  end
end
