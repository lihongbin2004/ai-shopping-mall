import request from '@/utils/request'
import { getToken } from '@/utils/auth'

export function chatStream(message, sessionId, onMessage, onDone, onError, onSession, onToolResult, model) {
  const token = getToken()
  const controller = new AbortController()

  fetch('/api/ai/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': token ? 'Bearer ' + token : ''
    },
    body: JSON.stringify({ message, sessionId, model: model || 'auto' }),
    signal: controller.signal
  }).then(async response => {
    if (!response.ok) {
      const errData = await response.json().catch(() => ({}))
      onError && onError(new Error(errData.message || 'HTTP ' + response.status))
      return
    }
    const contentType = response.headers.get('content-type') || ''
    if (contentType.includes('text/event-stream') || contentType.includes('text/plain')) {
      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      const read = () => {
        reader.read().then(({ done, value }) => {
          if (done) {
            onDone && onDone()
            return
          }
          buffer += decoder.decode(value, { stream: true })
          // SSE 事件以 \n\n 分隔；单行内 event: 跟 data: 成对
          buffer = buffer.replace(/\r\n/g, '\n')
          const events = buffer.split('\n\n')
          buffer = events.pop() || ''

          for (const evt of events) {
            const lines = evt.split('\n')
            let eventName = 'message'
            let dataStr = ''
            for (const line of lines) {
              if (line.startsWith('event:')) {
                eventName = line.slice(6).trim()
              } else if (line.startsWith('data:')) {
                dataStr += line.slice(5).trim()
              }
            }
            if (!dataStr) continue
            if (dataStr === '[DONE]') { onDone && onDone(); return }
            try {
              const parsed = JSON.parse(dataStr)
              if (eventName === 'error' || parsed.error) {
                onError && onError(new Error(parsed.error || 'AI服务异常'))
                continue
              }
              // 工具调用结果（必须在 done 之后处理，不能 return）
              if (eventName === 'tool_result' || parsed.tools) {
                onToolResult && onToolResult(parsed)
                continue
              }
              if (parsed.done) {
                if (parsed.sessionId && onSession) onSession(parsed.sessionId)
                onDone && onDone(parsed)
                continue
              }
              if (parsed.sessionId && onSession) onSession(parsed.sessionId)
              const content = parsed.content || parsed.choices?.[0]?.delta?.content || parsed.text || ''
              if (content) onMessage && onMessage(content)
            } catch (e) {
              if (dataStr) onMessage && onMessage(dataStr)
            }
          }
          read()
        }).catch(err => {
          if (err.name !== 'AbortError') {
            onError && onError(err)
          }
        })
      }
      read()
    } else {
      const json = await response.json()
      if (json.data) {
        onMessage && onMessage(typeof json.data === 'string' ? json.data : JSON.stringify(json.data))
      }
      onDone && onDone()
    }
  }).catch(err => {
    if (err.name !== 'AbortError') {
      onError && onError(err)
    }
  })

  return controller
}

export function chat(data) {
  return request({
    url: '/ai/chat',
    method: 'post',
    data
  })
}

export function createSession(data) {
  return request({
    url: '/ai/session',
    method: 'post',
    data
  })
}

export function getSessionList() {
  return request({
    url: '/ai/session/list',
    method: 'get'
  })
}

export function deleteSession(id) {
  return request({
    url: '/ai/session/' + id,
    method: 'delete'
  })
}

// 多模型集成：获取可用模型列表
export function getModels() {
  return request({
    url: '/ai/models',
    method: 'get'
  })
}

export function getSessionMessages(id) {
  return request({
    url: '/ai/session/' + id + '/messages',
    method: 'get'
  })
}
export function getChatHistory() {
  return request({
    url: '/ai/session/list',
    method: 'get'
  })
}

// 加载用户最新的会话及其消息（用于刷新页面后恢复对话）
export function loadLatestSession() {
  return request({
    url: '/ai/session/list',
    method: 'get'
  }).then(res => {
    const list = (res && res.data) || []
    if (list.length === 0) return Promise.resolve({ sessionId: null, messages: [] })
    // 取最近更新的 session
    const latest = list[0]
    return getSessionMessages(latest.id).then(mres => {
      return Promise.resolve({ sessionId: latest.id, messages: (mres && mres.data) || [] })
    })
  })
}

export function clearChatHistory() {
  return request({
    url: '/ai/session/clear',
    method: 'delete'
  })
}

export function getKnowledge() {
  return request({
    url: '/ai/knowledge',
    method: 'get'
  })
}