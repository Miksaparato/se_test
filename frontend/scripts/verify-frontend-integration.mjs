/**
 * 前端联调验证脚本（A-F1~A-F4）。
 *
 * 目的：在不依赖浏览器的情况下，验证前端所依赖的接口链路与**权限显隐所依据的数据**是否正确：
 *   1. 前端静态资源可访问（vite preview 服务）；
 *   2. 前端 → 后端 的代理链路可用（/api 转发到 8080）；
 *   3. 四个角色登录后返回的权限码集合与前端菜单/按钮显隐逻辑一致；
 *   4. 越权访问被后端 403 拦截（前端仅做提示）。
 *
 * 用法：node scripts/verify-frontend-integration.mjs [frontendBaseUrl]
 */

const FRONTEND = process.argv[2] ?? 'http://127.0.0.1:4173'

/** 侧边菜单所需的权限码（与 src/router/index.ts 的 meta.permission 一致）。 */
const MENU_PERMISSIONS = [
  { path: '/overview', title: '仓库总览', permission: 'sim:view' },
  { path: '/data/warehouses', title: '仓库管理', permission: 'sim:view' },
  { path: '/data/racks', title: '货架管理', permission: 'sim:view' },
  { path: '/data/locations', title: '库位管理', permission: 'sim:view' },
  { path: '/data/skus', title: '货物管理', permission: 'sim:view' },
  { path: '/data/orders', title: '订单管理', permission: 'sim:view' },
  { path: '/admin/users', title: '用户管理', permission: 'user:manage' },
  { path: '/admin/roles', title: '角色与权限', permission: 'user:manage' },
]

const results = []
let failed = 0

/**
 * 记录断言结果。
 *
 * @param {string} name 用例名
 * @param {boolean} ok 是否通过
 * @param {string} detail 补充信息
 */
function check(name, ok, detail = '') {
  results.push({ name, ok, detail })
  if (!ok) {
    failed += 1
  }
  console.log(`${ok ? '  [OK]' : '[FAIL]'} ${name}${detail ? ` -> ${detail}` : ''}`)
}

/**
 * 通过前端代理调用后端接口。
 *
 * @param {string} path 接口路径
 * @param {object} init fetch 参数
 * @returns {Promise<{status:number, body:any}>}
 */
async function api(path, init = {}) {
  const response = await fetch(`${FRONTEND}/api/v1${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init.headers ?? {}) },
  })
  const text = await response.text()
  let body = null
  try {
    body = JSON.parse(text)
  } catch {
    body = text
  }
  return { status: response.status, body }
}

/**
 * 登录并返回 Token 与权限。
 *
 * @param {string} account 账号
 */
async function login(account) {
  const { status, body } = await api('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ account, password: 'admin123' }),
  })
  if (status !== 200 || body?.code !== 0) {
    return { ok: false, status, body }
  }
  return {
    ok: true,
    token: body.data.token,
    permissions: body.data.user.permissions,
    roles: body.data.user.roles,
  }
}

console.log(`\n=== 前端联调验证（frontend=${FRONTEND}） ===\n`)

// 1. 前端静态资源
console.log('1) 前端静态资源')
const indexResponse = await fetch(`${FRONTEND}/`)
const html = await indexResponse.text()
check('index.html 可访问', indexResponse.status === 200, `HTTP ${indexResponse.status}`)
check('index.html 含挂载点 #app', html.includes('id="app"'))
check('index.html 引用 main.ts 或打包产物', html.includes('/src/main.ts') || html.includes('/assets/'))

// 2. 代理链路
console.log('\n2) 前端 → 后端 代理链路')
const health = await api('/health')
check('匿名访问受保护接口返回 401（代理链路通）', health.status === 401, `HTTP ${health.status}`)
check('401 为统一响应结构', health.body?.code === 40101, JSON.stringify(health.body))

// 3. 四角色登录与权限码
console.log('\n3) 四角色登录与权限码（前端菜单/按钮显隐依据）')
const expected = {
  admin: ['user:manage', 'warehouse:manage', 'sku:manage', 'recommend:view', 'sim:run', 'sim:view', 'compare:view', 'report:export', 'config:manage'],
  operator: ['sku:manage', 'recommend:view', 'sim:run', 'sim:view'],
  analyst: ['sim:run', 'sim:view', 'compare:view', 'report:export'],
  viewer: ['sim:view', 'compare:view'],
}
const sessions = {}
for (const [account, codes] of Object.entries(expected)) {
  const session = await login(account)
  sessions[account] = session
  if (!session.ok) {
    check(`${account} 登录成功`, false, JSON.stringify(session.body))
    continue
  }
  const actual = [...session.permissions].sort()
  const want = [...codes].sort()
  check(
    `${account} 登录成功且权限码一致`,
    JSON.stringify(actual) === JSON.stringify(want),
    actual.join(','),
  )
}

// 4. 菜单可见性推导（与 MainLayout 的过滤逻辑一致）
console.log('\n4) 各角色可见菜单（由权限码推导）')
for (const [account, session] of Object.entries(sessions)) {
  if (!session.ok) continue
  const visible = MENU_PERMISSIONS.filter((item) => session.permissions.includes(item.permission)).map((item) => item.title)
  check(`${account} 可见菜单`, visible.length > 0, visible.join('、'))
}
const viewerMenus = MENU_PERMISSIONS.filter((item) => sessions.viewer?.permissions?.includes(item.permission))
check(
  'viewer 不可见系统管理菜单（user:manage）',
  !viewerMenus.some((item) => item.permission === 'user:manage'),
)
const operatorMenus = MENU_PERMISSIONS.filter((item) => sessions.operator?.permissions?.includes(item.permission))
check(
  'operator 不可见系统管理菜单（user:manage）',
  !operatorMenus.some((item) => item.permission === 'user:manage'),
)
check(
  'admin 可见全部菜单',
  MENU_PERMISSIONS.every((item) => sessions.admin?.permissions?.includes(item.permission)),
)

// 5. 携带 Token 访问 + 越权拦截
console.log('\n5) 携带 Token 访问与越权拦截')
if (sessions.admin?.ok) {
  const auth = { Authorization: `Bearer ${sessions.admin.token}` }
  const me = await api('/auth/me', { headers: auth })
  check('admin GET /auth/me 返回 200', me.status === 200, `HTTP ${me.status}`)
  check('个人信息含角色数组', Array.isArray(me.body?.data?.roles), JSON.stringify(me.body?.data?.roles))
  check('个人信息不含 password', !JSON.stringify(me.body).includes('password'))

  const users = await api('/users', { headers: auth })
  check('admin 可访问用户列表（user:manage）', users.status === 200, `HTTP ${users.status}`)

  const roles = await api('/roles', { headers: auth })
  check('admin 可访问角色列表', roles.status === 200, `HTTP ${roles.status}`)

  const perms = await api('/permissions', { headers: auth })
  check('admin 可访问权限清单（9 项）', perms.status === 200 && perms.body?.data?.length === 9)
}

if (sessions.viewer?.ok) {
  const auth = { Authorization: `Bearer ${sessions.viewer.token}` }
  const forbidden = await api('/users', { headers: auth })
  check('viewer 访问用户列表被 403 拦截', forbidden.status === 403, `HTTP ${forbidden.status}`)
  check('403 为统一响应结构 40301', forbidden.body?.code === 40301, JSON.stringify(forbidden.body))

  const layoutOk = await api('/warehouses', { headers: auth })
  check('viewer 可读仓库列表（sim:view）', layoutOk.status === 200, `HTTP ${layoutOk.status}`)
}

if (sessions.operator?.ok) {
  const auth = { Authorization: `Bearer ${sessions.operator.token}` }
  const createSku = await api('/skus', {
    method: 'POST',
    headers: auth,
    body: JSON.stringify({
      skuCode: `FE-VERIFY-${Date.now() % 100000}`,
      name: '联调验证货物',
      weight: 10,
      turnoverRate: 0.5,
      priority: 3,
    }),
  })
  check('operator 可创建货物（sku:manage）', createSku.status === 200, `HTTP ${createSku.status}`)

  const createWarehouse = await api('/warehouses', {
    method: 'POST',
    headers: auth,
    body: JSON.stringify({ code: `FE-WH-${Date.now() % 100000}`, name: '越权测试仓' }),
  })
  check(
    'operator 创建仓库被 403 拦截（warehouse:manage）',
    createWarehouse.status === 403,
    `HTTP ${createWarehouse.status}`,
  )
}

console.log(`\n=== 结果：${results.length - failed}/${results.length} 通过 ===\n`)
process.exit(failed === 0 ? 0 : 1)
