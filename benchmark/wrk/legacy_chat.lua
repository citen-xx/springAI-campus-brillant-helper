wrk.method = "POST"
wrk.headers["Content-Type"] = "application/json"

wrk.body = [[
{
  "query": "Please explain the campus leave process.",
  "user": "wrk-user-1"
}
]]

response = function(status, headers, body)
  if status ~= 200 then
    io.stderr:write("non-200 status=" .. status .. "\n")
  end
end
