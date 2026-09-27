/**
 * 前端联调验证用的静态服务器（A-F1~A-F4）。
 *
 * 与 `vite preview` 等价地做两件事：
 *   1. 提供 `dist/` 静态资源（SPA：未匹配路径回落 index.html）；
 *   2. 把 `/api` 代理到后端 8080，复现开发环境的同源调用链路。
 *
 * 之所以不直接用 `vite preview`：受限环境下 Vite 需要让 esbuild 派生服务子进程，
 * 会因进程限制失败；本脚本只用 Node 内置能力，可在任何环境稳定运行。
 *
 * 用法：node scripts/preview-server.mjs [port] [backendUrl]
 */

import { createReadStream, existsSync, statSync } from 'node:fs'
import { createServer } from 'node:http'
import { extname, join, normalize } from 'node:path'
import { fileURLToPath } from 'node:url'

const PORT = Number(process.argv[2] ?? 4173)
const BACKEND = process.argv[3] ?? 'http://127.0.0.1:8080'
const DIST = fileURLToPath(new URL('../dist', import.meta.url))

/** 扩展名 → Content-Type。 */
const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.map': 'application/json; charset=utf-8',
}

/**
 * 读取请求体。
 *
 * @param {import('node:http').IncomingMessage} req 请求
 * @returns {Promise<Buffer>} 请求体
 */
async function readBody(req) {
  const chunks = []
  for await (const chunk of req) {
    chunks.push(chunk)
  }
  return Buffer.concat(chunks)
}

const server = createServer(async (req, res) => {
  const url = new URL(req.url ?? '/', `http://127.0.0.1:${PORT}`)

  // 1) /api 反向代理到后端
  if (url.pathname.startsWith('/api')) {
    try {
      const body = ['GET', 'HEAD'].includes(req.method ?? 'GET') ? undefined : await readBody(req)
      const upstream = await fetch(`${BACKEND}${url.pathname}${url.search}`, {
        method: req.method,
        headers: {
          'Content-Type': req.headers['content-type'] ?? 'application/json',
          ...(req.headers.authorization ? { Authorization: req.headers.authorization } : {}),
        },
        body,
      })
      const buffer = Buffer.from(await upstream.arrayBuffer())
      res.writeHead(upstream.status, {
        'Content-Type': upstream.headers.get('content-type') ?? 'application/json; charset=utf-8',
        ...(upstream.headers.get('content-disposition')
          ? { 'Content-Disposition': upstream.headers.get('content-disposition') }
          : {}),
      })
      res.end(buffer)
    } catch (error) {
      res.writeHead(502, { 'Content-Type': 'application/json; charset=utf-8' })
      res.end(JSON.stringify({ code: 50002, message: `代理后端失败：${error.message}`, data: null }))
    }
    return
  }

  // 2) 静态资源（SPA 回落 index.html）
  const relative = normalize(url.pathname).replace(/^([/\\])+/, '')
  let filePath = join(DIST, relative)
  if (!existsSync(filePath) || statSync(filePath).isDirectory()) {
    filePath = join(DIST, 'index.html')
  }
  if (!existsSync(filePath)) {
    res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' })
    res.end('dist 目录不存在，请先执行 npm run build')
    return
  }
  res.writeHead(200, {
    'Content-Type': MIME[extname(filePath)] ?? 'application/octet-stream',
  })
  createReadStream(filePath).pipe(res)
})

server.listen(PORT, '127.0.0.1', () => {
  console.log(`前端静态服务器已启动：http://127.0.0.1:${PORT}（/api 代理到 ${BACKEND}）`)
})
