# PremiumPe Admin

Aplicación privada para Android, escrita en Java con componentes nativos del SDK. Abre la carpeta `PremiumPe APK` como proyecto en Android Studio. Requiere Android 9 o posterior en el teléfono.

## Instalar el APK

1. Transfiere `PremiumPe-Admin.apk` al teléfono y ábrelo desde Archivos.
2. Si Android lo solicita, permite instalar aplicaciones desde la aplicación que abrió el archivo y confirma la instalación.
3. Abre **PremiumPe Admin** desde su icono. Entra con `cheridecheros` o el correo administrativo existente y tu contraseña de Supabase.

El APK corresponde a una distribución privada firmada con la clave de desarrollo de esta PC. Conserva la misma clave para instalar actualizaciones encima de esta versión. Cerrar sesión afecta a la sesión guardada en el teléfono.

## Configurar y compilar

1. Abre Android Studio con el JDK integrado y selecciona esta carpeta.
2. Ten instalado Android SDK Platform 37.0 y Build Tools 36.0.0. Sincroniza Gradle con el wrapper incluido.
3. Copia `.env.example` a `.env`. Define `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY` y `STORE_URL`. Se admite la clave publicable y la clave JWT heredada con rol `anon`. Usa la cuenta de Supabase Auth que ya administra PremiumPe.
4. Ejecuta `.\gradlew.bat :app:assembleRelease` desde la terminal de Android Studio. El archivo sale en `app/build/outputs/apk/release/app-release.apk`. La variante `release` requiere HTTPS.
5. Para depurar, elige la variante `debug` y pulsa **Run** con un teléfono USB o un emulador. El paquete de depuración se instala por separado.

La aplicación usa las tablas `products`, `settings` y `promotions`, el bucket `product-images` y la operación `save_promotion`. Los JWT de sesión se cifran con AES-GCM y una clave de Android Keystore. La app advierte a los 28 minutos de inactividad y cierra la sesión a los 30 minutos. El rol efectivo se aplica en las políticas RLS y en Storage.

## Cobertura

- **Resumen** muestra totales, alertas de configuración, reposición y accesos directos al producto, promoción, WhatsApp y tienda.
- **Productos** incluye búsqueda, filtros, orden, duplicación como oculto, cambios rápidos, acciones en lote y reordenamiento con Subir/Bajar. El editor cubre los campos del catálogo, toma o selecciona una foto, permite quitarla y muestra la vista previa.
- **Promociones** permite crear y editar combos con productos existentes, fechas de Lima, validación de precio y vista previa del ahorro. También muestra el estado y permite pausar o eliminar.
- **Ajustes** cubre los datos de la tienda, la portada, atención y pagos, preguntas frecuentes y redes sociales.
- **Ver tienda** abre el sitio público en el navegador. La app comparte los registros del entorno configurado; un producto visible guardado allí se publica en la tienda de ese entorno.

## Prueba aislada

1. Levanta un Supabase local separado y aplica una copia de `schema.sql`, la migración de promociones y, si quieres ejemplos, `seed.sql` de PremiumPe. Crea en ese entorno un administrador con el mismo correo y una contraseña de prueba.
2. Configura la variante `debug` con la URL, clave pública y tienda de ese entorno. Desde el emulador Android, usa `10.0.2.2` para acceder al host Windows. Usa una copia de la tienda de referencia configurada con el mismo Supabase.
3. Crea un producto visible con foto y comprueba que la tienda de prueba muestre ambos. Edita un producto existente, crea y pausa un combo, guarda Ajustes y comprueba el rechazo de una escritura con un usuario ajeno al administrador.
4. En una pantalla pequeña, abre el teclado en el editor, comprueba que Cancelar y Guardar estén accesibles y fuerza un error de guardado. Corrige el dato o restablece la conexión y vuelve a guardar desde el mismo formulario.

En esta entrega se comprobó la instalación en el emulador, el inicio de sesión contra Supabase local y la recuperación del formulario de producto tras un error de precio con el teclado abierto. En el entorno aislado se verificaron también el alta con portada, la aparición del producto en una copia de la tienda, la edición y eliminación, la creación y pausa de combos, el guardado de Ajustes y el rechazo de escrituras sin permiso.

Pedidos y renovaciones quedan como ideas para una etapa posterior.
