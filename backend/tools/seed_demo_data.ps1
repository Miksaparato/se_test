<#
.SYNOPSIS
    为小型智能仓储库位分配仿真系统写入演示业务数据（仓库 / 货架 / 库位 / 货物 / 订单）。

.DESCRIPTION
    通过**公开 REST API** 建数据，而不是直接写库：
      * 走的是真实业务校验（编码唯一、层号范围、优先级 1~5 等），顺带完成接口冒烟；
      * RBAC 种子（用户/角色/权限）完全不动，只补业务数据（验收标准 1「可录入仓库与货物」）；
      * 可重复执行：编码已存在时跳过并复用已有 id，不会重复插入。

    数据规模：1 个仓库（27×13，出库口在 (0,0)）、3 个货架（巷道 A/B/C，各 4 列 × 3 层，
    共 36 个库位）、5 个 SKU、12 张出库订单。

    分区约定与 backend/src/main/resources/application.yml 的 wms.zone.aisle-category 对应：
    巷道 A → 电子，B → 食品，C → 机械。

    库位容量：库位容量与 SKU 尺寸同为**体积口径**，单位必须一致，否则
    「尺寸不得超出库位容量」（《需求文档》4.4 约束三）会把所有库位判为放不下
    （schema 的默认容量是 100，而演示 SKU 体积是 6000~72000）。
    因此本脚本按 -LocationCapacity（默认 100000）建库位，并把容量过小的既有库位纠正过来。

.PARAMETER BaseUrl
    后端地址，默认 http://127.0.0.1:8080

.PARAMETER Account
    登录账号，默认 admin（需要 warehouse:manage / sku:manage 权限）。

.PARAMETER Password
    登录密码，默认 admin123（V3 种子脚本中的初始密码）。

.PARAMETER LocationCapacity
    库位容量（体积口径），默认 100000，需大于演示 SKU 的最大体积 72000。

.NOTES
    本脚本含中文，且**以 UTF-8 with BOM 保存**：Windows PowerShell 5.1 读取无 BOM 的 .ps1 时
    会按系统 ANSI（简体中文为 GBK）解码，中文会变乱码并破坏语法。用编辑器改动本文件后请保持 BOM。

.EXAMPLE
    pwsh backend/tools/seed_demo_data.ps1
    pwsh backend/tools/seed_demo_data.ps1 -BaseUrl http://127.0.0.1:8080 -Account admin -Password admin123
#>
[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://127.0.0.1:8080',
    [string]$Account = 'admin',
    [string]$Password = 'admin123',
    [double]$LocationCapacity = 100000
)

$ErrorActionPreference = 'Stop'
$script:Token = ''
$script:Base = $BaseUrl.TrimEnd('/') + '/api/v1'

<#
.SYNOPSIS
    调用后端接口，正确处理 UTF-8 请求体与响应体。
.DESCRIPTION
    Windows PowerShell 5.1 的 Invoke-RestMethod 默认按 ISO-8859-1 编码请求体、按 ANSI 解码
    响应体，中文会乱码。这里统一按 UTF-8 收发，避免「演示数据写进去是乱码」的假故障。
#>
function Invoke-Api {
    param(
        [Parameter(Mandatory)][string]$Method,
        [Parameter(Mandatory)][string]$Path,
        $Body
    )

    $uri = $script:Base + $Path
    $headers = @{ 'Accept' = 'application/json' }
    if ($script:Token) { $headers['Authorization'] = "Bearer $($script:Token)" }

    $req = @{
        Uri         = $uri
        Method      = $Method
        Headers     = $headers
        TimeoutSec  = 30
        ErrorAction = 'Stop'
    }
    if ($null -ne $Body) {
        $json = if ($Body -is [string]) { $Body } else { $Body | ConvertTo-Json -Depth 8 -Compress }
        $req['Body'] = [System.Text.Encoding]::UTF8.GetBytes($json)
        $req['ContentType'] = 'application/json; charset=utf-8'
    }

    try {
        $resp = Invoke-WebRequest @req -UseBasicParsing
    } catch {
        $status = $_.Exception.Response.StatusCode.value__
        $detail = ''
        if ($_.Exception.Response) {
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream(),
                [System.Text.Encoding]::UTF8)
            $detail = $reader.ReadToEnd()
        }
        throw "HTTP $status $Method $Path -> $detail"
    }

    $text = [System.Text.Encoding]::UTF8.GetString($resp.RawContentStream.ToArray())
    if ([string]::IsNullOrWhiteSpace($text)) { return $null }
    return $text | ConvertFrom-Json
}

<# 取统一响应里的 data，code != 0 直接抛错，避免把失败当成功。 #>
function Get-Data {
    param($Response, [string]$What)
    if ($null -eq $Response) { throw "$What：响应为空" }
    if ($Response.code -ne 0) { throw "$What：code=$($Response.code) message=$($Response.message)" }
    return $Response.data
}

Write-Host "== 登录 $Account @ $BaseUrl" -ForegroundColor Cyan
$login = Get-Data (Invoke-Api -Method Post -Path '/auth/login' -Body @{
    account  = $Account
    password = $Password
}) '登录'
$script:Token = $login.token
Write-Host "   登录成功：$($login.user.name) 角色=$($login.user.roles -join ',')"

# ---------------------------------------------------------------- 1. 货物（API-030）
Write-Host '== 货物 SKU' -ForegroundColor Cyan
$skuSeed = @(
    @{ skuCode = 'SKU-001'; name = '高频电子元件'; weight = 50;  turnoverRate = 0.90; priority = 5; category = '电子'; size = @{ length = 30; width = 20; height = 10 } },
    @{ skuCode = 'SKU-002'; name = '重型机械部件'; weight = 180; turnoverRate = 0.20; priority = 3; category = '机械'; size = @{ length = 60; width = 40; height = 30 } },
    @{ skuCode = 'SKU-003'; name = '服装成衣';     weight = 8;   turnoverRate = 0.60; priority = 2; category = '服装'; size = @{ length = 40; width = 30; height = 15 } },
    @{ skuCode = 'SKU-004'; name = '包装食品';     weight = 12;  turnoverRate = 0.70; priority = 4; category = '食品'; size = @{ length = 35; width = 25; height = 20 } },
    @{ skuCode = 'SKU-005'; name = '五金配件';     weight = 90;  turnoverRate = 0.40; priority = 3; category = '机械'; size = @{ length = 25; width = 25; height = 15 } }
)

$skuIds = @{}
$existingSkus = Get-Data (Invoke-Api -Method Get -Path '/skus?page=1&page_size=100') '查询 SKU'
foreach ($s in $existingSkus.list) { $skuIds[$s.skuCode] = $s.id }

foreach ($seed in $skuSeed) {
    if ($skuIds.ContainsKey($seed.skuCode)) {
        Write-Host "   - $($seed.skuCode) 已存在（id=$($skuIds[$seed.skuCode])），跳过"
        continue
    }
    $created = Get-Data (Invoke-Api -Method Post -Path '/skus' -Body $seed) "创建 $($seed.skuCode)"
    $skuIds[$seed.skuCode] = $created.id
    Write-Host "   + $($seed.skuCode) $($seed.name) -> id=$($created.id)"
}

# ---------------------------------------------------------------- 2. 仓库（API-016）
Write-Host '== 仓库' -ForegroundColor Cyan
$whCode = 'WH-01'
$warehouses = Get-Data (Invoke-Api -Method Get -Path '/warehouses?page=1&page_size=100') '查询仓库'
$warehouse = $warehouses.list | Where-Object { $_.code -eq $whCode } | Select-Object -First 1
if ($warehouse) {
    Write-Host "   - $whCode 已存在（id=$($warehouse.id)），复用"
} else {
    $warehouse = Get-Data (Invoke-Api -Method Post -Path '/warehouses' -Body @{
        code    = $whCode
        name    = '一号仓'
        length  = 27
        width   = 13
        height  = 3
        exitX   = 0
        exitY   = 0
        remark  = '演示数据：3 个货架（巷道 A/B/C），出库口位于 (0,0)'
    }) '创建仓库'
    Write-Host "   + $whCode 一号仓 -> id=$($warehouse.id)"
}
$warehouseId = $warehouse.id

# ---------------------------------------------------------------- 3. 货架 + 库位（API-022，generateLocations 批量生成库位）
Write-Host '== 货架与库位' -ForegroundColor Cyan
$rackSeed = @(
    @{ code = 'A-01'; aisle = 'A'; x = 2;  y = 2; columnCount = 4; layerCount = 3 },
    @{ code = 'B-01'; aisle = 'B'; x = 10; y = 2; columnCount = 4; layerCount = 3 },
    @{ code = 'C-01'; aisle = 'C'; x = 18; y = 2; columnCount = 4; layerCount = 3 }
)
foreach ($r in $rackSeed) {
    # 货架没有独立列表接口（见《接口文档》第 5 节），是否已存在取自仓库平面布局（API-021）
    $layout = Get-Data (Invoke-Api -Method Get -Path "/warehouses/$warehouseId/layout") '查询仓库布局'
    if (@($layout.racks | ForEach-Object { $_.code }) -contains $r.code) {
        Write-Host "   - 货架 $($r.code) 已存在，跳过"
        continue
    }
    $payload = $r.Clone()
    $payload['warehouseId'] = $warehouseId
    $payload['orientation'] = 'row'
    $payload['generateLocations'] = $true
    $payload['capacity'] = $LocationCapacity
    $created = Get-Data (Invoke-Api -Method Post -Path '/racks' -Body $payload) "创建货架 $($r.code)"
    Write-Host "   + 货架 $($r.code)（巷道 $($r.aisle)）-> id=$($created.id)"
}

# 纠正容量过小的既有库位：容量与 SKU 尺寸同为体积口径，容量太小时容量校验会过滤掉全部库位
$locations = Get-Data (Invoke-Api -Method Get -Path "/locations?warehouse_id=$warehouseId&page=1&page_size=100") '查询库位'
$tooSmall = @($locations.list | Where-Object { [double]$_.capacity -lt $LocationCapacity })
if ($tooSmall.Count -gt 0) {
    foreach ($loc in $tooSmall) {
        Invoke-Api -Method Put -Path "/locations/$($loc.id)" -Body @{ capacity = $LocationCapacity } | Out-Null
    }
    Write-Host "   ~ 已把 $($tooSmall.Count) 个库位的容量修正为 $LocationCapacity"
    $locations = Get-Data (Invoke-Api -Method Get -Path "/locations?warehouse_id=$warehouseId&page=1&page_size=100") '查询库位'
}
Write-Host "   库位总数：$($locations.total)，容量：$LocationCapacity"

# ---------------------------------------------------------------- 4. 订单（API-034）
Write-Host '== 出库订单' -ForegroundColor Cyan
$orderSeeds = @(
    @{ sku = 'SKU-001'; quantity = 20; priority = 5 },
    @{ sku = 'SKU-002'; quantity = 5;  priority = 3 },
    @{ sku = 'SKU-003'; quantity = 30; priority = 2 },
    @{ sku = 'SKU-004'; quantity = 15; priority = 4 },
    @{ sku = 'SKU-005'; quantity = 10; priority = 3 },
    @{ sku = 'SKU-001'; quantity = 8;  priority = 4 },
    @{ sku = 'SKU-004'; quantity = 12; priority = 5 },
    @{ sku = 'SKU-003'; quantity = 25; priority = 1 },
    @{ sku = 'SKU-002'; quantity = 3;  priority = 2 },
    @{ sku = 'SKU-005'; quantity = 18; priority = 4 },
    @{ sku = 'SKU-001'; quantity = 6;  priority = 3 },
    @{ sku = 'SKU-004'; quantity = 9;  priority = 2 }
)

$orders = Get-Data (Invoke-Api -Method Get -Path '/orders?page=1&page_size=100') '查询订单'
if ($orders.total -gt 0) {
    Write-Host "   - 已存在 $($orders.total) 张订单，跳过"
} else {
    $seq = 0
    foreach ($o in $orderSeeds) {
        $seq++
        $orderNo = 'SO-{0}-{1:d3}' -f (Get-Date -Format 'yyyyMMdd'), $seq
        $payload = @{
            orderNo  = $orderNo
            skuId    = $skuIds[$o.sku]
            quantity = $o.quantity
            priority = $o.priority
            remark   = '演示数据'
        }
        $created = Get-Data (Invoke-Api -Method Post -Path '/orders' -Body $payload) "创建订单 $orderNo"
        Write-Host "   + $orderNo sku=$($o.sku) qty=$($o.quantity) priority=$($o.priority) -> id=$($created.id)"
    }
}

Write-Host ''
Write-Host '演示数据就绪：' -ForegroundColor Green
Write-Host "   warehouseId = $warehouseId"
Write-Host "   库位         = $($locations.total)"
Write-Host "   SKU          = $($skuIds.Count)（$($skuIds.Keys -join ', ')）"
Write-Host '   订单         = 12'
Write-Host ''
Write-Host '下一步可试：' -ForegroundColor Green
Write-Host "   POST /api/v1/recommendations  {`"skuId`":$($skuIds['SKU-001']),`"warehouseId`":$warehouseId,`"topN`":10}"
