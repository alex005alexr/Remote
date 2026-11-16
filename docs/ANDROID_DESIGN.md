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

## Módulos previstos

- core: ciclo de vida, estado general, registro y coordinación.
- telemetry: ubicación, sensores y batería.
- usb: detección USB host, permisos, serial, heartbeat y reconexión.
- camera: CameraX/Camera2, selección frontal/trasera y transmisión.
- network: canal LAN inicial y transporte remoto seguro futuro.

Cada módulo debe aislar sus errores y exponer estado/diagnóstico sin detener los demás.

## Estado actual

Se añadió un esqueleto mínimo de proyecto Android y un servicio en primer plano. Aún no están implementados telemetría, cámara, USB serial, estación base ni protocolo de control.
