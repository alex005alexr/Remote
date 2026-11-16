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


## Estado de comunicación USB serial en la app

La rama `feature/usb-serial` incorpora una primera capa de comunicación serial para adaptadores habituales:

- CDC/ACM (incluido el USB Serial/JTAG nativo de ESP32-S3, sujeto a la interfaz que exponga el firmware).
- FTDI, CP210x, CH34x y Prolific, mediante la librería usb-serial-for-android.
- Solicitud del permiso USB de Android antes de abrir el puerto.
- Apertura inicial a 115200 baudios, 8 bits, sin paridad, 1 bit de parada (8N1).
- Lectura de datos entrantes y visualización de los primeros bytes en hexadecimal.
- Cierre del puerto al desconectar físicamente el dispositivo o cerrar la pantalla de la app.

### Prueba cuando tengas el ESP32

1. Conecta el ESP32 al teléfono con adaptador OTG USB-C y cable USB de datos.
2. Abre Remote y pulsa **Actualizar dispositivos USB**.
3. Pulsa **Conectar puerto serial USB** y acepta el permiso de Android.
4. Comprueba que aparezca el estado de puerto conectado.
5. Para verificar recepción, usa un firmware de prueba que envíe texto periódico por serial a 115200 baudios.
6. Desconecta el cable y comprueba que Remote indique la desconexión.

Esta fase solo abre el puerto y recibe bytes. Todavía no transmite comandos, no define el protocolo de control y no acciona motores. No se debe conectar hardware de potencia para estas pruebas. El siguiente paso, una vez validado con la placa real, será acordar un protocolo de mensajes y añadir validación, latido de conexión y parada segura en el controlador local.


## Interfaz de transmisión

Se añadió una primera pantalla horizontal accesible desde **Abrir interfaz de transmisión** en el diagnóstico.

- Área principal reservada para la imagen de cámara.
- Indicador visible de transmisión desconectada.
- Espacio para datos breves de GPS y enlace.
- Panel lateral con selección de cámara frontal o trasera.
- Estado independiente de cámara, red y USB/ESP32.
- Mantiene la pantalla encendida mientras está abierta.

La pantalla ahora integra CameraX y un servidor HTTP/MJPEG local para la primera prueba de video:
- Solicita permiso de cámara al entrar.
- Permite seleccionar cámara trasera o frontal (una a la vez).
- Captura y reduce los fotogramas a 640×480 para la prueba inicial.
- Publica el video en el puerto TCP 8080, dentro de la red local.
- Muestra la dirección que debe abrirse desde el navegador de la PC y cuántos clientes están conectados.
- El visor web está servido por el propio teléfono; la PC no necesita instalar un programa.

### Primera prueba desde la PC
1. Conecta el TECNO y la PC a la misma red Wi-Fi (no uses una red de invitados que aísle los dispositivos).
2. Instala en el TECNO el APK de la rama de desarrollo que incluya esta integración.
3. Abre Remote → **Abrir interfaz de transmisión** y concede el permiso de cámara.
4. Espera a que la pantalla muestre una dirección similar a `http://192.168.1.25:8080`.
5. Escribe esa dirección en Firefox, Chrome o Edge en la PC.
6. Debe aparecer el video. En el teléfono, el contador de PC conectadas debe cambiar al abrir el visor.

El servidor se detiene al cerrar la pantalla de transmisión. Esta primera versión es solo para una LAN de confianza: no tiene contraseña ni cifrado y no debe exponerse a Internet ni redirigirse el puerto del router.

### Alcance y limitaciones de esta primera prueba
- MJPEG es deliberadamente simple y compatible con navegadores, pero consume más ancho de banda que H.264/WebRTC.
- La dirección IP puede cambiar al reconectar el teléfono a otra Wi-Fi.
- La app debe permanecer en la pantalla de transmisión para mantener la cámara y el servidor activos.
- La siguiente fase, después de validar la red local, será mejorar eficiencia/latencia y preparar el acceso remoto por 4G con autenticación y transporte seguro.
