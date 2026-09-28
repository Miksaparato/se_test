<#
.SYNOPSIS
    小型智能仓储库位分配仿真系统 —— 验收自检脚本（对应《需求分析文档》第 11 节 8 条验收标准）。

.DESCRIPTION
    只通过**公开 REST API** 验收，不直接读库：这样验的就是使用者真实走的链路。
    逐条打印 PASS/FAIL，全部通过时退出码为 0，可直接用于演示前的自检。

    覆盖范围：
      1. 录入仓库与货物，平面图正确展示库位布局
      2. 带评分与理由的推荐排序 + 权重可调整
      3. ≥4 种入库策略仿真（含智能推荐）
      4. 出库仿真 + 总/平均路程统计
      5. ≥2 方案对比 + 量化指标 + 优化建议 + 报告导出
      6. 数据导入导出（核心算法单测由 mvn test 覆盖，见文末提示）
      7. RBAC：角色权限隔离与越权 403 拦截
      8. 使用说明与算法说明文档齐备

    注意：脚本会**新建一个验收专用仓库**（编码 `ACC-<时间戳>`）并留下仿真产出，便于人工复核；
    如需清理，按脚本末尾打印的资源 id 依次删除（方案可用 API-057 重置一并清除）。

.PARAMETER BaseUrl
    后端地址，默认 http://127.0.0.1:8081

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File backend/tools/acceptance.ps1

.NOTES
    本脚本含中文，且以 UTF-8 with BOM 保存（Windows PowerShell 5.1 读无 BOM 的 .ps1 会按 GBK 解码而报错）。
#>
[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://127.0.0.1:8081',
    [string]$Password = 'admin123'
)

$ErrorActionPreference = 'Stop'
$script:Base = $BaseUrl.TrimEnd('/') + '/api/v1'
$script:Pass = 0
$script:Fail = 0
$suffix = (Get-Date -Format 'HHmmss')

<#
.SYNOPSIS 收发 UTF-8 JSON 的极简 HTTP 客户端。
.DESCRIPTION Windows PowerShell 5.1 默认按 ANSI 处理请求/响应体，中文会乱码，故统一按 UTF-8 收发。
#>
function Invoke-Api {
    param([string]$Method, [string]$Path, $Body, [string]$Token)
    $headers = @{ 'Accept' = 'application/json' }
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }
    $req = @{ Uri = $script:Base + $Path; Method = $Method; Headers = $headers; TimeoutSec = 60; ErrorAction = 'Stop' }
    if ($null -ne $Body) {
        $json = if ($Body -is [string]) { $Body } else { $Body | ConvertTo-Json -Depth 8 -Compress }
        $req['Body'] = [System.Text.Encoding]::UTF8.GetBytes($json)
        $req['ContentType'] = 'application/json; charset=utf-8'
    }
    try {
        $resp = Invoke-WebRequest @req -UseBasicParsing
        return [pscustomobject]@{ status = $resp.StatusCode; text = [System.Text.Encoding]::UTF8.GetString($resp.RawContentStream.ToArray()) }
    } catch {
        $status = $_.Exception.Response.StatusCode.value__
        $detail = ''
        if ($_.Exception.Response) {
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream(), [System.Text.Encoding]::UTF8)
            $detail = $reader.ReadToEnd()
        }
        return [pscustomobject]@{ status = $status; text = $detail }
    }
}

<# 取统一响应的 data 字段。 #>
function Data-Of {
    param($Response)
    if ([string]::IsNullOrWhiteSpace($Response.text)) { return $null }
    try { return ($Response.text | ConvertFrom-Json).data } catch { return $null }
}

<# 记录一条断言结果。 #>
function Assert-Check {
    param([string]$Name, [bool]$Condition, [string]$Detail = '')
    if ($Condition) { $script:Pass++; Write-Host ("    [PASS] {0}{1}" -f $Name, $(if ($Detail) { "  -> $Detail" } else { '' })) -ForegroundColor Green }
    else { $script:Fail++; Write-Host ("    [FAIL] {0}{1}" -f $Name, $(if ($Detail) { "  -> $Detail" } else { '' })) -ForegroundColor Red }
}

<# 输出验收条目标题。 #>
function Section {
    param([string]$Title)
    Write-Host ''
    Write-Host "== $Title" -ForegroundColor Cyan
}

Write-Host ''
Write-Host '===================================================================='
Write-Host ' 小型智能仓储库位分配仿真系统 —— 验收自检'
Write-Host " 目标：$BaseUrl"
Write-Host '===================================================================='

# ---------------------------------------------------------------- 登录
$login = Invoke-Api Post '/auth/login' @{ account = 'admin'; password = $Password }
$adminToken = (Data-Of $login).token
if (-not $adminToken) {
    Write-Host "登录失败：$($login.text)" -ForegroundColor Red
    exit 1
}
Write-Host '已以 admin 登录' -ForegroundColor DarkGray

# ---------------------------------------------------------------- 验收 1
Section '验收标准 1：可录入仓库与货物，平面图正确展示库位布局'
$warehouse = Data-Of (Invoke-Api Post '/warehouses' @{
    code = "ACC-$suffix"; name = "验收仓-$suffix"
    length = 12; width = 8; height = 3; exitX = 0; exitY = 0
    remark = 'acceptance.ps1 自动创建'
} -Token $adminToken)
Assert-Check '创建仓库（API-016）' ($null -ne $warehouse.id) "id=$($warehouse.id)"

$rack = Data-Of (Invoke-Api Post '/racks' @{
    warehouseId = $warehouse.id; code = "A-$suffix"; aisle = 'A'
    columnCount = 2; layerCount = 2; x = 2; y = 2; orientation = 'row'
    generateLocations = $true; capacity = 100000
} -Token $adminToken)
Assert-Check '创建货架并批量生成库位（API-022）' ($null -ne $rack.id) "rackId=$($rack.id)"

$sku = Data-Of (Invoke-Api Post '/skus' @{
    skuCode = "ACC-SKU-$suffix"; name = "验收货物-$suffix"
    weight = 60; turnoverRate = 0.8; priority = 4; category = '电子'
    size = @{ length = 10; width = 10; height = 10 }
} -Token $adminToken)
Assert-Check '创建货物（API-030）' ($null -ne $sku.id) "skuId=$($sku.id)"

$layout = Data-Of (Invoke-Api Get "/warehouses/$($warehouse.id)/layout" -Token $adminToken)
$layoutLocations = @($layout.racks | ForEach-Object { $_.locations } | ForEach-Object { $_ })
Assert-Check '仓库平面布局返回货架与库位（API-021）' ($layoutLocations.Count -eq 4) "库位数=$($layoutLocations.Count)"
Assert-Check '库位含渲染所需字段（code/x/y/layer/status/capacity）' (
    $null -ne $layoutLocations[0].code -and $null -ne $layoutLocations[0].x -and
    $null -ne $layoutLocations[0].y -and $null -ne $layoutLocations[0].layer -and
    $null -ne $layoutLocations[0].status -and $null -ne $layoutLocations[0].capacity
) "示例：$($layoutLocations[0].code) @($($layoutLocations[0].x),$($layoutLocations[0].y)) 第$($layoutLocations[0].layer)层"
Assert-Check '布局带出库口坐标（距离计算终点）' ($null -ne $layout.exit) "exit=($($layout.exit.x),$($layout.exit.y))"

# ---------------------------------------------------------------- 验收 2
Section '验收标准 2：入库推荐给出带评分与理由的排序，且权重可调整'
$rec = Data-Of (Invoke-Api Post '/recommendations' @{ skuId = $sku.id; warehouseId = $warehouse.id; topN = 4 } -Token $adminToken)
Assert-Check '生成推荐（API-041）' (@($rec.candidates).Count -gt 0) "候选=$(@($rec.candidates).Count) 编号=$($rec.recommendationId)"
$scores = @($rec.candidates | ForEach-Object { $_.score })
$sorted = ($scores -join ',') -eq (($scores | Sort-Object -Descending) -join ',')
Assert-Check '候选按综合评分降序' $sorted ($scores -join ' > ')
Assert-Check '每个候选都给出分项得分与理由' (
    $null -ne $rec.candidates[0].subScores.weight -and $null -ne $rec.candidates[0].subScores.freq -and
    $null -ne $rec.candidates[0].subScores.priority -and $null -ne $rec.candidates[0].subScores.other -and
    -not [string]::IsNullOrWhiteSpace($rec.candidates[0].reason)
) "理由：$($rec.candidates[0].reason)"

$weightsBefore = Data-Of (Invoke-Api Get '/config/weights' -Token $adminToken)
$weightsChanged = Data-Of (Invoke-Api Put '/config/weights' @{ weight = 0.10; freq = 0.70; priority = 0.15; other = 0.05 } -Token $adminToken)
Assert-Check '调整评分权重（API-045）' ([math]::Abs($weightsChanged.freq - 0.70) -lt 1e-9) "频次权重 $($weightsBefore.freq) → $($weightsChanged.freq)"
$recAfter = Data-Of (Invoke-Api Post '/recommendations' @{ skuId = $sku.id; warehouseId = $warehouse.id; topN = 4 } -Token $adminToken)
Assert-Check '按新权重重算推荐' (@($recAfter.candidates).Count -gt 0) "首位得分 $($rec.candidates[0].score) → $($recAfter.candidates[0].score)"
$badWeight = Invoke-Api Put '/config/weights' @{ weight = 0.5; freq = 0.5; priority = 0.5; other = 0.5 } -Token $adminToken
Assert-Check '权重之和不为 1 被拒（42211）' ($badWeight.status -eq 422) "HTTP $($badWeight.status)"
Invoke-Api Put '/config/weights' @{ weight = 0.30; freq = 0.40; priority = 0.20; other = 0.10 } -Token $adminToken | Out-Null
Assert-Check '一键采用推荐（API-043）' (
    $null -ne (Data-Of (Invoke-Api Post "/recommendations/$($rec.recommendationId)/adopt" -Token $adminToken)).locationId
)

# ---------------------------------------------------------------- 验收 3
Section '验收标准 3：至少 4 种入库策略仿真（含智能推荐）'
$strategies = Data-Of (Invoke-Api Get '/strategies' -Token $adminToken)
$strategyNames = @($strategies | ForEach-Object { $_.name })
Assert-Check '策略列表（API-054）≥ 4 种' ($strategyNames.Count -ge 4) ($strategyNames -join ', ')
Assert-Check '含智能推荐策略' ($strategyNames -contains 'smart')

$planIds = @()
foreach ($name in $strategyNames) {
    $sim = Data-Of (Invoke-Api Post '/simulations/inbound' @{
        warehouseId = $warehouse.id; strategy = $name; items = @(@{ skuId = $sku.id; quantity = 5 })
    } -Token $adminToken)
    if ($sim.planId) {
        $planIds += $sim.planId
        Write-Host ("         {0,-8} {1} 方案#{2} 理由：{3}" -f $name, $sim.simulationId, $sim.planId, $sim.assignments[0].reason) -ForegroundColor DarkGray
    }
}
Assert-Check '每种策略都产出方案' ($planIds.Count -eq $strategyNames.Count) "方案 id = $($planIds -join ', ')"
$inboundId = $sim.simulationId
$inboundGet = Data-Of (Invoke-Api Get "/simulations/inbound/$inboundId" -Token $adminToken)
Assert-Check '回读仿真记录含选位理由（API-056）' (-not [string]::IsNullOrWhiteSpace($inboundGet.assignments[0].reason))

# ---------------------------------------------------------------- 验收 4
Section '验收标准 4：出库仿真 + 总/平均路程统计'
$generated = Data-Of (Invoke-Api Post '/orders/generate' @{
    count = 10; skuIds = @($sku.id); priorityRange = @(1, 5); quantityRange = @(1, 5); randomSeed = 20260910
} -Token $adminToken)
Assert-Check '生成随机测试订单集（API-062）' ($generated.generated -eq 10) "种子=$($generated.randomSeed)"

$planForOutbound = $planIds[0]
$outbound = Data-Of (Invoke-Api Post '/simulations/outbound' @{
    planId = $planForOutbound; orderIds = $generated.orderIds; pickingMode = 'single'; speed = 1.5
} -Token $adminToken)
$stats = $outbound.stats
Assert-Check '出库仿真（API-058）' ($stats.orderCount -eq 10) "仿真单号=$($outbound.simulationId)"
$sumDistances = ($stats.orderDistances | Measure-Object -Sum).Sum
# 接口对统计值统一保留 3 位小数，故容差取 1e-3
Assert-Check '总路程 = 各订单路程之和' ([math]::Abs($sumDistances - $stats.totalDistance) -lt 1e-3) "总=$($stats.totalDistance)"
Assert-Check '平均路程 = 总路程 ÷ 订单数' ([math]::Abs($stats.avgDistance - ($stats.totalDistance / $stats.orderCount)) -lt 1e-3) "平均=$($stats.avgDistance)"
Assert-Check '耗时估算 = 总路程 ÷ 速度' ([math]::Abs($stats.estimatedTime - ($stats.totalDistance / $stats.speed)) -lt 1e-3) "耗时=$($stats.estimatedTime)s @ $($stats.speed)格/秒"
$paths = Data-Of (Invoke-Api Get "/simulations/outbound/$($outbound.simulationId)/path" -Token $adminToken)
$withPoints = @($paths | Where-Object { @($_.points).Count -ge 2 })
Assert-Check '拣选路径坐标可供平面图绘制（API-061）' ($withPoints.Count -gt 0) "示例：$(($withPoints[0].points | ForEach-Object { "($($_.x),$($_.y))" }) -join ' → ')"

# ---------------------------------------------------------------- 验收 5
Section '验收标准 5：≥2 方案对比 + 量化对比 + 优化建议 + 报告导出'
$compare = Data-Of (Invoke-Api Post '/plans/compare' @{ planIds = @($planIds[0], $planIds[1]) } -Token $adminToken)
Assert-Check '多方案对比（API-051）' (@($compare.metrics).Count -eq 2) "指标数=$(@($compare.metrics).Count)"
Assert-Check '对比指标含四项量化值' (
    $null -ne $compare.metrics[0].totalDistance -and $null -ne $compare.metrics[0].avgDistance -and
    $null -ne $compare.metrics[0].hotAvgDistance -and $null -ne $compare.metrics[0].violations
) "总路程 $($compare.metrics[0].totalDistance) / 平均 $($compare.metrics[0].avgDistance) / 高频 $($compare.metrics[0].hotAvgDistance) / 违规 $($compare.metrics[0].violations)"
Assert-Check '自动生成优化建议文字' (-not [string]::IsNullOrWhiteSpace($compare.suggestion)) $compare.suggestion
$report = Invoke-Api Get "/plans/$($planIds[0])/export?format=md" -Token $adminToken
Assert-Check '导出对比报告（API-053）' ($report.status -eq 200 -and $report.text.Contains('库位分配方案对比报告')) "报告长度=$($report.text.Length) 字符"

# ---------------------------------------------------------------- 验收 6
Section '验收标准 6：数据导入导出（核心算法单测见文末）'
$exportSku = Invoke-Api Get '/export/skus?format=csv' -Token $adminToken
Assert-Check '导出 SKU 为 CSV（API-039）' ($exportSku.status -eq 200 -and $exportSku.text.Contains('skuCode'))
$exportOrder = Invoke-Api Get '/export/orders?format=csv' -Token $adminToken
Assert-Check '导出订单为 CSV（API-040）' ($exportOrder.status -eq 200 -and $exportOrder.text.Length -gt 0)

# ---------------------------------------------------------------- 验收 7
Section '验收标准 7：RBAC 角色隔离与越权拦截'
$expected = @{
    admin    = @('user:manage','warehouse:manage','sku:manage','recommend:view','sim:run','sim:view','compare:view','report:export','config:manage')
    operator = @('sku:manage','recommend:view','sim:run','sim:view')
    analyst  = @('sim:run','sim:view','compare:view','report:export')
    viewer   = @('sim:view','compare:view')
}
$tokens = @{}
foreach ($account in $expected.Keys) {
    $session = Data-Of (Invoke-Api Post '/auth/login' @{ account = $account; password = $Password })
    $tokens[$account] = $session.token
    $actual = ($session.user.permissions | Sort-Object) -join ','
    $want = ($expected[$account] | Sort-Object) -join ','
    Assert-Check "$account 权限码与权限矩阵一致" ($actual -eq $want) $actual
}
$probe = @{ warehouseId = $warehouse.id; strategy = 'fifo'; items = @(@{ skuId = $sku.id; quantity = 1 }) }
Assert-Check 'viewer 执行入库仿真被 403 拦截' ((Invoke-Api Post '/simulations/inbound' $probe -Token $tokens.viewer).status -eq 403)
Assert-Check 'viewer 访问用户管理被 403 拦截' ((Invoke-Api Get '/users' -Token $tokens.viewer).status -eq 403)
Assert-Check 'viewer 查看仓库布局被放行（sim:view）' ((Invoke-Api Get "/warehouses/$($warehouse.id)/layout" -Token $tokens.viewer).status -eq 200)
Assert-Check 'operator 执行入库仿真被放行（sim:run）' ((Invoke-Api Post '/simulations/inbound' $probe -Token $tokens.operator).status -eq 200)
Assert-Check 'operator 导出报告被 403 拦截（需 report:export）' ((Invoke-Api Get "/plans/$($planIds[0])/export" -Token $tokens.operator).status -eq 403)
Assert-Check 'analyst 导出报告被放行（report:export）' ((Invoke-Api Get "/plans/$($planIds[0])/export" -Token $tokens.analyst).status -eq 200)
Assert-Check 'analyst 查看配置被 403 拦截（需 config:manage）' ((Invoke-Api Get '/config/weights' -Token $tokens.analyst).status -eq 403)
Assert-Check '匿名访问受保护接口返回 401' ((Invoke-Api Get '/plans').status -eq 401)

# ---------------------------------------------------------------- 验收 8
Section '验收标准 8：使用说明与算法说明文档齐备'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$docs = @(
    @{ path = 'docs/使用说明文档.md';               name = '使用说明文档（T-5，a 牵头）' },
    @{ path = 'document/T-6-评分模型算法说明.md';    name = '算法说明：评分模型（T-6，b）' },
    @{ path = 'document/T-6-仿真与距离口径说明.md';  name = '算法说明：仿真与距离口径（T-6，c）' },
    @{ path = 'docs/数据库设计说明书.md';            name = '数据库设计说明书（含一致性核对结论）' },
    @{ path = 'docs/鉴权中间件规范.md';              name = '鉴权中间件规范' },
    @{ path = 'document/接口文档.md';                name = 'REST API 接口文档（冻结契约）' }
)
foreach ($doc in $docs) {
    Assert-Check $doc.name (Test-Path (Join-Path $repoRoot $doc.path))
}

# ---------------------------------------------------------------- 汇总
Write-Host ''
Write-Host '===================================================================='
$total = $script:Pass + $script:Fail
if ($script:Fail -eq 0) {
    Write-Host " 验收自检结果：$($script:Pass)/$total 全部通过" -ForegroundColor Green
} else {
    Write-Host " 验收自检结果：$($script:Pass)/$total 通过，$($script:Fail) 项失败" -ForegroundColor Red
}
Write-Host '===================================================================='
Write-Host ''
Write-Host '本次验收创建的资源（可在界面上人工复核）：' -ForegroundColor DarkGray
Write-Host "  warehouseId = $($warehouse.id)（ACC-$suffix）" -ForegroundColor DarkGray
Write-Host "  rackId      = $($rack.id)，4 个库位" -ForegroundColor DarkGray
Write-Host "  skuId       = $($sku.id)（ACC-SKU-$suffix）" -ForegroundColor DarkGray
Write-Host "  产出方案    = $($planIds -join ', ')" -ForegroundColor DarkGray
Write-Host ''
Write-Host '核心算法单元测试（验收标准 6 的另一半）请单独执行：' -ForegroundColor DarkGray
Write-Host '  cd backend && mvn -s maven-settings.xml test        # 115 项' -ForegroundColor DarkGray
Write-Host '  cd backend && mvn -s maven-settings.xml test -Pe2e  # 61 项真实 HTTP（独立库 wms_sim_e2e*）' -ForegroundColor DarkGray
Write-Host ''

exit $(if ($script:Fail -eq 0) { 0 } else { 1 })
