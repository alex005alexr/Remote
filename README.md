# Remote — Android Onboard Computer

Proyecto para convertir un teléfono Android en una computadora de abordo para un carro robot y, posteriormente, como computador complementario de un dron.

## Hardware inicial

- Teléfono: TECNO SPARK 10C, modelo KL5K, 8 GB RAM.
- Android: 12 (confirmado por el usuario).\n- USB-C OTG: comprobado con pendrive y acceso a archivos.
- Controlador recomendado: ESP32-S3-DevKitC-1 (pendiente de compra/prueba).
- Conectividad inicial: Wi-Fi en red local.
- Evolución prevista: acceso remoto mediante 4G.

## Arquitectura

### Android onboard
Aplicación nativa Android responsable de:
- Servicio persistente en primer plano para tareas de abordo.
- Captura y transmisión de video con selección de cámara frontal/trasera.
- Telemetría: ubicación, sensores de movimiento y estado de batería/dispositivo.
- Comunicación USB serial con ESP32.
- Estado de enlaces, heartbeat, detección de fallos y reconexión controlada.
- Registro local de eventos y errores.

### Estación base
Interfaz para PC/tablet que permita:
- Ver video en directo.
- Alternar cámara.
- Consultar telemetría y calidad de conexión.
- Enviar comandos al ESP32 y ver respuestas.
- Mostrar claramente pérdida de señal y estado de seguridad.

### ESP32
Controlador de tiempo real para motores, dirección y periféricos. Android no debe sustituir las protecciones locales ni los mecanismos de parada segura.

## Principios de diseño

1. Video, telemetría y control son canales independientes: un fallo en uno no debe derribar los demás.
2. Reconexión automática con límites, backoff y diagnóstico; no depender de reiniciar la app.
3. Heartbeat y detección explícita de desconexión.
4. Protocolo serial versionado, estructurado y documentado.
5. Seguridad por defecto: pérdida de comunicación debe llevar al ESP32 a un estado seguro configurable.
6. Wi-Fi local primero; transporte preparado para incorporar 4G mediante un canal seguro sin rediseñar la aplicación.
7. La estabilización, navegación crítica, retorno y aterrizaje de un dron deben permanecer bajo un controlador de vuelo dedicado.

## Fases

1. Inspección de capacidades Android y estructura del proyecto.
2. App Android: servicio, telemetría y selección de cámaras.
3. USB OTG serial: detección, conexión, protocolo y reconexión.
4. Estación base y video/telemetría/control en LAN.
5. Pruebas de desconexión, recuperación y operación prolongada.
6. Transporte remoto seguro mediante 4G.
7. Adaptación y optimización para plataforma aérea.

## Hardware pendiente de validar

- Versión exacta de Android/HiOS y restricciones de batería en el KL5K.
- Enumeración del ESP32 como dispositivo USB serial y chipset USB-UART.
- Acceso a ambas cámaras con Camera2/CameraX y comportamiento al cambiar.
- Sensores físicos frente a sensores virtuales y disponibilidad real de cada sensor.
- Rendimiento térmico, consumo y estabilidad con pantalla apagada.

## Estado inicial

Repositorio recién creado. No se ha añadido todavía código de aplicación ni se ha modificado hardware.
