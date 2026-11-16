$ErrorActionPreference = 'Stop'
$qa = $PSScriptRoot
$cli = 'D:/Proyectos/lavanderia/node_modules/.bin/supabase.cmd'
Push-Location $qa
try {
    $prevEAP = $ErrorActionPreference
    $ErrorActionPreference = 'SilentlyContinue'
    $raw = (cmd /c "`"$cli`" status -o json 2>nul" | Out-String)
    $ErrorActionPreference = $prevEAP
    if ($raw -match '(?s)(\{.*\})') {
        $stack = $Matches[1] | ConvertFrom-Json
    } else {
        throw "No se pudo obtener el JSON de Supabase: $raw"
    }
} finally { Pop-Location }

$url = 'http://127.0.0.1:55421'
if ($stack.API_URL -notin @($url, 'http://localhost:55421') -or !$stack.ANON_KEY) {
    throw 'Se esperaba el Supabase aislado en el puerto 55421.'
}
$fixture = Join-Path $qa 'test-auth.json'
$headers = @{apikey = $stack.ANON_KEY; Authorization = "Bearer $($stack.ANON_KEY)"}
$password = if (Test-Path $fixture) {
    (Get-Content -LiteralPath $fixture -Raw | ConvertFrom-Json).Password
} else {
    [guid]::NewGuid().ToString('N') + 'mQ9'
}
@{Key = $stack.ANON_KEY; Password = $password} | ConvertTo-Json | Set-Content -LiteralPath $fixture
function Request-Safe {
    param($Method, $Uri, $Headers, $ContentType, $Body)
    try {
        $p = @{ Method = $Method; Uri = $Uri; Headers = $Headers }
        if ($ContentType) { $p.ContentType = $ContentType }
        if ($Body) { $p.Body = $Body }
        return Invoke-WebRequest @p
    } catch [System.Net.WebException] {
        return $_.Exception.Response
    }
}
foreach ($email in @('admin@premiumpe.com', 'invitado@premiumpe.test')) {
    $credentials = @{email = $email; password = $password} | ConvertTo-Json
    $null = Request-Safe -Method Post -Uri "$url/auth/v1/signup" -Headers $headers -ContentType 'application/json' -Body $credentials
}
$visitor = Invoke-RestMethod -Method Post -Uri "$url/auth/v1/token?grant_type=password" -Headers $headers -ContentType 'application/json' -Body $credentials
$denied = @{apikey = $stack.ANON_KEY; Authorization = "Bearer $($visitor.access_token)"}
foreach ($auth in @($headers, $denied)) {
    $response = Request-Safe -Method Post -Uri "$url/rest/v1/products" -Headers $auth -ContentType 'application/json' -Body '{"name":"QA acceso denegado","category":"IA","price":9}'
    $code = [int]$response.StatusCode
    if ($code -notin @(401, 403)) { throw "Se esperaba rechazo, HTTP $code." }
    "Escritura sin permiso rechazada con HTTP $code."
}

@("VITE_SUPABASE_URL=$url", "VITE_SUPABASE_ANON_KEY=$($stack.ANON_KEY)") | Set-Content -LiteralPath "$qa/store/.env.local"
Start-Process -FilePath 'C:/Program Files/nodejs/node.exe' -ArgumentList @('node_modules/vite/bin/vite.js', '--host', '127.0.0.1', '--port', '5573', '--strictPort') -WorkingDirectory "$qa/store" -WindowStyle Hidden -RedirectStandardOutput "$qa/vite.stdout.log" -RedirectStandardError "$qa/vite.stderr.log"
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
$env:ANDROID_HOME = 'C:/Users/aless/AppData/Local/Android/Sdk'
$env:SUPABASE_URL = 'http://10.0.2.2:55421'
$env:SUPABASE_PUBLISHABLE_KEY = $stack.ANON_KEY
$env:STORE_URL = 'http://10.0.2.2:5573/'
Push-Location (Split-Path -Parent $qa)
try {
    & .\gradlew.bat :app:assembleDebug --offline
    if ($LASTEXITCODE -ne 0) { throw 'Falló la compilación de QA.' }
} finally { Pop-Location }
