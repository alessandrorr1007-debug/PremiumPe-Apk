. "$PSScriptRoot/android-ui.ps1"
$ui = Screen
TapText $ui 'Nuevo producto'
$ui = Screen
$inputs = $ui.SelectNodes('//node[@class="android.widget.EditText"]')
TapNode $inputs[0]
Input 'QA_Movil'
$ui = Screen
if (!$ui.SelectSingleNode('//node[@text="Guardar"]') -or !$ui.SelectSingleNode('//node[@text="Cancelar"]')) {
    throw 'El teclado oculta las acciones del editor.'
}
TapText $ui 'Guardar'
$ui = Screen
if (!$ui.SelectSingleNode('//node[@text="QA_Movil"]') -or !$ui.SelectSingleNode('//node[contains(@text,"Ingresa un precio")]')) {
    ShowText $ui
    throw 'No se conservó el nombre con el error de precio.'
}
'Pantalla pequeña con teclado, acciones visibles y recuperación del formulario comprobadas.'
ShowText $ui
