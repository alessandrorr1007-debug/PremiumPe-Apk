param([switch]$Resume)
. "$PSScriptRoot/android-ui.ps1"
$credentials = Get-Content -Raw -LiteralPath "$PSScriptRoot/test-auth.json" | ConvertFrom-Json
if (!$Resume) {
for ($i = 0; $i -lt 25; $i++) {
    if (((Droid shell getprop sys.boot_completed) | Out-String).Trim() -eq '1') { break }
    Start-Sleep -Seconds 4
}
Droid install --no-streaming -r (Join-Path (Split-Path -Parent $PSScriptRoot) 'app/build/outputs/apk/debug/app-debug.apk')
if ($LASTEXITCODE -ne 0) { throw 'Falló la instalación de QA.' }
Droid shell input keyevent 82
Droid shell am start -W -n 'pe.premiumpe.admin.qa/pe.premiumpe.admin.MainActivity'
}
$ui = Screen
if ($ui.SelectSingleNode('//node[@text="Wait"]')) {
    TapText $ui 'Wait'
    $ui = Screen
}
$inputs = $ui.SelectNodes('//node[@class="android.widget.EditText"]')
if ($inputs.Count -ne 2) { ShowText $ui; throw 'No se abrió el formulario de sesión.' }
TapNode $inputs[0]
Input 'cheridecheros'
TapNode $inputs[1]
Input $credentials.Password
Back
TapText (Screen) 'Entrar'
Start-Sleep -Seconds 3
$ui = Screen
ShowText $ui
if (!$ui.SelectSingleNode('//node[@text="Acciones rápidas"]')) { throw 'No aparece el resumen.' }
