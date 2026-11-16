# Hardware base y compatibilidad

## Teléfono confirmado

- TECNO SPARK 10C (KL5K).
- Android 12.
- 8 GB de RAM.
- USB-C OTG probado por el usuario con un pendrive; falta validar comunicación USB serial con una placa.

## Controlador recomendado

Primera opción: **ESP32-S3-DevKitC-1**.

Motivos:
- El ESP32-S3 incorpora USB OTG Full-Speed y USB Serial/JTAG.
- La placa oficial expone un puerto USB nativo además del puerto USB-to-UART.
- Permite probar primero USB CDC/serial y conservar flexibilidad para futuras expansiones.

Importante al comprar:
- Confirmar que sea realmente una ESP32-S3-DevKitC-1 y que exponga el puerto USB nativo.
- No confundirla con una placa ESP32-WROOM clásica ni con clones cuya distribución de puertos varía.
- Usar un cable USB de datos y un adaptador OTG USB-C compatible.
- El teléfono actuará como USB host y la placa como dispositivo USB.

La conexión física y el reconocimiento Android todavía deben probarse con la placa real. La compatibilidad OTG con pendrives no garantiza por sí sola que cualquier placa USB serial funcione.

## Criterios de prueba

1. Detectar conexión y desconexión física.
2. Identificar VID/PID y clase/interfaz USB.
3. Solicitar permiso USB al usuario cuando Android lo requiera.
4. Abrir el puerto serial y negociar velocidad/parámetros.
5. Intercambiar mensajes de prueba y comprobar recuperación tras desconectar.
6. Medir estabilidad durante operación prolongada.

## Límites de seguridad

- El ESP32 ejecutará el control local de motores y periféricos.
- Debe existir parada segura local ante pérdida de comunicación.
- El teléfono no será responsable de estabilización ni de control de tiempo real de un dron.
