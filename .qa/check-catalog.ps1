$ErrorActionPreference = 'Stop'
$fixture = Get-Content -Raw -LiteralPath "$PSScriptRoot/test-auth.json" | ConvertFrom-Json
$url = 'http://127.0.0.1:55421'
$anon = @{apikey = $fixture.Key; Authorization = "Bearer $($fixture.Key)"}
$login = @{email = 'admin@premiumpe.com'; password = $fixture.Password} | ConvertTo-Json
$session = Invoke-RestMethod -Method Post -Uri "$url/auth/v1/token?grant_type=password" -Headers $anon -ContentType 'application/json' -Body $login
$admin = @{apikey = $fixture.Key; Authorization = "Bearer $($session.access_token)"; Prefer = 'return=representation'}
function Change($method, $path, $payload) {
    $opts = if ($null -ne $payload) { @{Body=($payload | ConvertTo-Json -Depth 7 -Compress); ContentType='application/json'} } else { @{} }
    Invoke-RestMethod -Method $method -Uri "$url/rest/v1/$path" -Headers $admin @opts
}

$name = 'QA PremiumPe ' + [Guid]::NewGuid().ToString('N').Substring(0,8)
$photo = Get-Item "$PSScriptRoot/store/src/client/assets/covers/disney.webp"
if ($photo) {
    $file = [Guid]::NewGuid().ToString('N') + '.webp'
    $upload = $admin.Clone()
    $upload['x-upsert'] = 'true'
    $null = Invoke-WebRequest -Method Post -Uri "$url/storage/v1/object/product-images/$file" -Headers $upload -ContentType 'image/webp' -InFile $photo.FullName
    $imageUrl = "$url/storage/v1/object/public/product-images/$file"
}
$a = (Change Post 'products' @{name=$name;category='IA';price=12;image_url=$imageUrl;visible=$true;available=$true})[0]
$b = (Change Post 'products' @{name="$name segundo";category='IA';price=14;visible=$true;available=$true})[0]
if (!$a.id -or !$b.id) { throw 'Faltan productos recién creados.' }
$public = Invoke-RestMethod -Uri "$url/rest/v1/products?id=eq.$($a.id)&select=name,image_url" -Headers $anon
if ($public[0].name -ne $name -or $public[0].image_url -ne $imageUrl) { throw 'No coincide la lectura pública.' }
if ($imageUrl -and (Invoke-WebRequest -Uri $imageUrl).StatusCode -ne 200) { throw 'Falla la portada pública.' }
'Alta de producto y consulta pública comprobadas.'
$env:TEMP = $PSScriptRoot
$env:TMP = $PSScriptRoot
& node "$PSScriptRoot/store/verify-qa.mjs" $name
if ($LASTEXITCODE -ne 0) { throw 'Falló la verificación en el navegador.' }
$edited = (Change Patch "products?id=eq.$($a.id)" @{price=13})[0]
if ($edited.price -ne 13) { throw 'El precio no cambió.' }
$id = Change Post 'rpc/save_promotion' @{p_id=$null;p_details=@{name=$name;price=18;active=$true};p_products=@($a.id,$b.id)}
$active = Invoke-RestMethod -Uri "$url/rest/v1/current_promotions?id=eq.$id&select=id,original_price" -Headers $anon
if ($active[0].original_price -ne 27) {throw 'No aparece el combo en la vista pública.'}
$paused = (Change Patch "promotions?id=eq.$id" @{active=$false})[0]
if ($paused.active) {throw 'No se pausó el combo.'}
$inactive = Invoke-RestMethod -Uri "$url/rest/v1/current_promotions?id=eq.$id&select=id" -Headers $anon
if ($inactive.Count -ne 0) {throw 'Una promoción pausada sigue visible.'}
'Edición de producto, creación y pausa de promoción comprobadas.'
$settings = (Change Patch 'settings?id=eq.1' @{response_time='Respuesta de QA'})[0]
if ($settings.response_time -ne 'Respuesta de QA') {throw 'Ajustes no se guardaron.'}
$null = Change Delete "promotions?id=eq.$id" $null
$null = Change Delete "products?id=in.($($a.id),$($b.id))" $null
'Ajustes y eliminación de productos y promoción comprobados.'
