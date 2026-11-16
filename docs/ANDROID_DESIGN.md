# Diseño inicial Android

## Plataforma

- Dispositivo de prueba: TECNO SPARK 10C KL5K.
- Android confirmado: 12 (API 31).
- minSdk inicial: 26.
- targetSdk inicial: 32 mientras se valida el comportamiento en el dispositivo real.
- Java/Kotlin target: 17.

El targetSdk se mantiene temporalmente en 32 para esta primera compilación de laboratorio. Antes de distribuir o publicar la app se revisarán los requisitos vigentes de Google Play y las restricciones de versiones Android más nuevas.

## Servicio persistente

El servicio se inicia desde una actividad visible, mediante una acción explícita del usuario. Android 12 restringe el inicio de servicios en primer plano cuando la aplicación ya está en segundo plano; no se debe diseñar suponiendo que un servicio puede arrancar libremente desde cualquier estado.

La operación con pantalla apagada debe probarse en el TECNO porque HiOS puede aplicar restricciones adicionales de batería y procesos en segundo plano.

## Módulos

- core: ciclo de vida, estado general, registro y coordinación.
- telemetry: ubicación, sensores y batería.
- usb: detección USB host, permisos, serial, heartbeat y reconexión.
- camera: CameraX/Camera2, selección frontal/trasera y transmisión.
- network: canal LAN inicial y transporte remoto seguro futuro.

Cada módulo debe aislar sus errores y exponer estado/diagnóstico sin detener los demás.

## Telemetría implementada

La primera versión del módulo `telemetry` incluye:

- Ubicación mediante `LocationManager`, última ubicación conocida y actualizaciones periódicas.
- Acelerómetro, giroscopio y vector de rotación cuando el hardware los expone.
- Orientación derivada: azimut, inclinación y alabeo, expresados en grados.
- Nivel de batería y estado de carga.
- Modelo `TelemetrySnapshot` con campos opcionales: los sensores o datos no disponibles permanecen como `null`.
- Servicio en primer plano que inicia/detiene el recolector.
- Solicitud de permisos de ubicación desde la actividad visible. Los sensores y batería funcionan aunque el usuario no conceda GPS.

## Diagnóstico local implementado

Como todavía no hay estación base, la pantalla principal de Android funciona temporalmente como panel de pruebas:

- Muestra en vivo GPS, altitud, velocidad, precisión, orientación, acelerómetro, giroscopio y batería.
- Los valores no disponibles se muestran como guiones, para distinguirlos de lecturas válidas.
- Permite iniciar y detener el servicio desde la pantalla.
- Muestra los dispositivos USB conectados, fabricante/producto cuando el dispositivo los informa, VID/PID y clases de interfaces.
- Actualiza la lista USB al conectar o desconectar un dispositivo y ofrece actualización manual.

Esta pantalla es de diagnóstico local; no reemplaza la futura estación base ni transmite telemetría por Wi-Fi.

## USB: alcance actual y siguiente trabajo

Está declarada la capacidad USB host y el panel ya enumera dispositivos conectados. Todavía no se abre el puerto serial, no se solicitan permisos USB, no se intercambian mensajes con el ESP32 y no existe reconexión serial. Esos pasos se implementarán y probarán cuando se conecte una placa compatible.

## Pruebas pendientes en el TECNO KL5K

1. Instalar el APK y conceder permiso de ubicación precisa o aproximada.
2. Iniciar Remote y comprobar qué sensores producen valores.
3. Mover e inclinar el teléfono para comprobar cambios de acelerómetro, giroscopio y orientación.
4. Probar GPS en exterior y verificar estabilidad/precisión.
5. Conectar un pendrive OTG y confirmar que aparece en la sección USB.
6. Dejar el servicio activo con pantalla apagada y comprobar restricciones de HiOS, consumo y temperatura.

## Estado actual

Existe un servicio Android, primer corte del recolector de telemetría y una pantalla de diagnóstico local. La enumeración USB está preparada para pruebas sin placa serial. Cámara, USB serial, estación base, transporte de red y protocolo de control todavía no están implementados.

## Permisos de ubicación y firma de actualizaciones

La pantalla de diagnóstico muestra el estado del permiso (preciso/aproximado/pendiente) y si los servicios de ubicación del teléfono están activados. Al iniciar:

1. Si falta el permiso, Android muestra la solicitud de ubicación.
2. Si el permiso existe pero la ubicación está apagada, Remote ofrece abrir los ajustes del sistema.
3. El usuario puede continuar sin GPS; los demás sensores siguen funcionando.

Android no permite que una aplicación active GPS silenciosamente. El usuario debe confirmarlo desde los ajustes del sistema.

Para permitir instalar nuevas versiones encima de la anterior, todas las APK distribuidas deben firmarse con la misma clave. El workflow usa una clave de carga persistente guardada como secretos del repositorio:

- `REMOTE_KEYSTORE_BASE64`: archivo JKS codificado en Base64.
- `REMOTE_STORE_PASSWORD`: contraseña del almacén.
- `REMOTE_KEY_ALIAS`: alias de la clave.
- `REMOTE_KEY_PASSWORD`: contraseña de la clave.

Cuando estos cuatro secretos están configurados, Actions genera `Remote-update-apk` (release firmado). Mientras no estén configurados, genera `Remote-debug-apk`, que es temporal y puede no permitir actualizar instalaciones hechas con otra clave. No subir nunca el archivo JKS ni sus contraseñas al repositorio.

El `versionCode` de cada ejecución se deriva del número de ejecución de GitHub Actions para que cada APK nueva tenga una versión superior.