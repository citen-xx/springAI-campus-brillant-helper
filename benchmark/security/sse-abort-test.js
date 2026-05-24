const url = process.env.TEST_URL
const method = process.env.TEST_METHOD || 'POST'
const token = process.env.TEST_TOKEN || ''
const body = process.env.TEST_BODY || ''
const abortMs = Number(process.env.ABORT_MS || '1000')

if (!url) {
  console.error('TEST_URL is required')
  process.exit(1)
}

const headers = {
  Accept: 'text/event-stream'
}

if (body) {
  headers['Content-Type'] = 'application/json; charset=utf-8'
}

if (token) {
  headers.Authorization = `Bearer ${token}`
}

async function main() {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), abortMs)
  const result = {
    url,
    method,
    abortMs,
    aborted: false,
    errorName: '',
    status: null,
    contentType: '',
    chunks: 0,
    bytes: 0
  }

  try {
    const response = await fetch(url, {
      method,
      headers,
      body: body || undefined,
      signal: controller.signal
    })
    result.status = response.status
    result.contentType = response.headers.get('content-type') || ''

    if (!response.body) {
      throw new Error('response body missing')
    }

    const reader = response.body.getReader()
    try {
      while (true) {
        const { done, value } = await reader.read()
        if (done) {
          break
        }
        result.chunks += 1
        result.bytes += value ? value.length : 0
      }
    } finally {
      try {
        await reader.cancel()
      } catch (e) {
        // ignore cancel errors after abort
      }
    }
  } catch (error) {
    result.aborted = controller.signal.aborted
    result.errorName = error && error.name ? error.name : 'Error'
  } finally {
    clearTimeout(timer)
  }

  process.stdout.write(`${JSON.stringify(result)}\n`)
}

main().catch((error) => {
  console.error(error)
  process.exit(1)
})
