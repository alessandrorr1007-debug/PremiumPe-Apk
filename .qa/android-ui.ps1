$ErrorActionPreference = 'Stop'
$script:adb = 'C:/Users/aless/AppData/Local/Android/Sdk/platform-tools/adb.exe'
function Droid { & $script:adb -s emulator-5570 @args }
function Screen {
    $null = Droid shell uiautomator dump /sdcard/premiumpe-window.xml
    [xml] (Droid shell cat /sdcard/premiumpe-window.xml | Out-String)
}
function TapNode($node) {
    if (!$node) { throw 'No se encontró el control.' }
    $p = [regex]::Matches($node.bounds, '\d+') | ForEach-Object { [int]$_.Value }
    Droid shell input tap ([int](($p[0]+$p[2])/2)) ([int](($p[1]+$p[3])/2))
}
function TapText($ui, $value) {
    TapNode ($ui.SelectSingleNode("//node[@text='$value']"))
}
function Input($value) { Droid shell input text $value }
function Back { Droid shell input keyevent 4 }
function ScrollForm { Droid shell input swipe 260 725 260 320 450 }
function ShowText($ui) { $ui.SelectNodes('//node[@text!=""]') | ForEach-Object { $_.text } }
