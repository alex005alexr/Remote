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

## Telemetría implementada (primer corte)

La primera versión del módulo `telemetry` incluye:

- Ubicación mediante `LocationManager`, priorizando GPS cuando existe permiso preciso y usando proveedor de red cuando corresponde.
- Última ubicación conocida y actualizaciones periódicas.
- Acelerómetro, giroscopio y vector de rotación cuando el hardware los expone.
- Orientación derivada: azimut, inclinación y alabeo, expresados en grados.
- Nivel de batería y estado de carga.
- Modelo `TelemetrySnapshot` con campos opcionales: los sensores o datos que no estén disponibles permanecen como `null`.
- Servicio en primer plano que inicia/detiene el recolector y refleja estado resumido en su notificación.
- Solicitud de permisos de ubicación desde la actividad visible. Si el usuario los deniega, Remote puede seguir recopilando sensores y batería; GPS queda deshabilitado.

La frecuencia de sensores usa el modo normal para reducir consumo. El módulo todavía no transmite datos por red ni los muestra en un panel: esa será la siguiente integración.

## Pendiente de validar en el teléfono real

1. Confirmar qué sensores físicos/virtuales reporta el TECNO KL5K.
2. Verificar precisión y estabilidad del GPS en exterior.
3. Probar operación con pantalla apagada y restricciones de batería de HiOS.
4. Medir consumo y temperatura durante sesiones prolongadas.
5. Comprobar revocación de permisos y recuperación al volver a concederlos.

## Estado actual

Existe un esqueleto Android con servicio en primer plano y primer corte del recolector de telemetría. Cámara, USB serial, estación base, transporte de red y protocolo de control todavía no están implementados.
