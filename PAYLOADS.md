# Especificación de Datos y Payloads de NotifyBridge

Esta documentación describe con exactitud la estructura de los datos que **NotifyBridge** envía a tu servidor o webhook HTTP(S) al interceptar eventos, detallando las variables dinámicas disponibles (incluyendo `{not_type}`) y utilizando como referencia las **4 reglas predeterminadas** con ejemplos y textos reales de **Nequi** y **Bancolombia** (con nombre del remitente, monto y referencia).

---

## 1. Estructura General de la Transmisión HTTP

Cada reenvío ejecutado por NotifyBridge hacia tu endpoint contiene:

1. **Método HTTP:** `POST` (configurable por regla).
2. **Cabeceras HTTP (Headers):**
   - Cabeceras definidas en la regla (por defecto `Content-Type: application/json`).
   - Cabeceras del sistema del dispositivo inyectadas automáticamente.
3. **Cuerpo del Mensaje (Payload Body):** Objeto JSON con los datos del pago y del evento resueltos dinámicamente.

---

## 2. Cabeceras del Sistema Inyectadas

NotifyBridge inyecta automáticamente en cada petición cabeceras de diagnóstico e identificación del dispositivo emisor:

| Cabecera | Ejemplo | Descripción |
| :--- | :--- | :--- |
| `Content-Type` | `application/json` | Formato del payload enviado. |
| `X-NotifyBridge-Version` | `1.0` | Versión de la app NotifyBridge instalada. |
| `X-NotifyBridge-OS` | `Android` | Sistema operativo emisor. |
| `X-NotifyBridge-OS-Version` | `14` | Versión comercial de Android. |
| `X-NotifyBridge-SDK-Version` | `34` | Nivel de API del SDK de Android. |
| `X-NotifyBridge-Kernel` | `5.10.168-android12` | Versión del Kernel del dispositivo. |
| `X-NotifyBridge-Device-Manufacturer` | `Samsung` / `Xiaomi` | Fabricante del teléfono. |
| `X-NotifyBridge-Device-Model` | `SM-S918B` / `Redmi Note 12` | Modelo del teléfono. |
| `X-NotifyBridge-Device-UUID` | `e3b0c442-98fc-1c14-9afb-4c8996fb9242` | UUID persistente y único asignado al dispositivo. |

---

## 3. Variables de Plantilla Disponibles (Placeholders)

Puedes utilizar estas variables dentro de la plantilla JSON del cuerpo (`bodyTemplate`) o en los valores de las cabeceras:

| Variable | Canal | Valor Resuelto |
| :--- | :--- | :--- |
| `{not_type}` | Todos | Canal de origen: `"notification"` (App push), `"sms"` (Mensaje de texto), o `"imap"` (Correo). |
| `{not_title}` | APP / SMS | Título de la notificación push (en Nequi viene vacío `""`, en Bancolombia `"Bancolombia"`). En SMS es el remitente. |
| `{not_text}` | APP / SMS | Contenido íntegro del texto de la notificación (Apps) o cuerpo del SMS. |
| `{sms_sender}` | SMS | Remitente o número corto del SMS (ej. `85954`, `85432`). |
| `{sms_text}` | SMS | Contenido íntegro del mensaje de texto SMS. |
| `{package_name}` | APP | Identificador del paquete Android de la app emisora (ej. `com.nequi.MobileApp`). |
| `{timestamp}` | Todos | Timestamp Epoch en milisegundos del momento de recepción. |
| `{system_time}` | Todos | Timestamp del sistema (milisegundos). |
| `{device_uuid}` | Todos | UUID persistente generado para este dispositivo Android. |
| `{global_NOMBRE}` | Todos | Inyecta el valor de la variable global `NOMBRE` configurada en Ajustes. |

---

## 4. Diccionario de Campos del Payload (JSON)

Los campos enviados en las plantillas predeterminadas están estandarizados en inglés:

| Campo | Tipo | Presente en | Descripción |
| :--- | :--- | :--- | :--- |
| `bank` | String | APP y SMS | Entidad financiera emisora (`"Nequi"` o `"Bancolombia"`). |
| `type` | String | APP y SMS | Tipo de evento resuelto por `{not_type}` (`"notification"` o `"sms"`). |
| `title` | String | Solo APP | Título de la notificación. En Nequi viene como cadena vacía (`""`) porque la app no asigna título en Android; en Bancolombia es `"Bancolombia"`. |
| `sender` | String | Solo SMS | Remitente o código corto originario del SMS (ej. `85954` para Nequi, `85432` para Bancolombia). |
| `message` | String | APP y SMS | Texto completo de la notificación o SMS con el remitente, monto y referencia de la transacción. |
| `package_name` | String | Solo APP | ID del paquete Android de la app que generó la notificación (`com.nequi.MobileApp` o `com.todo1.mobile`). |
| `timestamp` | String (Unix) | APP y SMS | Marca de tiempo en milisegundos (Epoch ms) del momento de captura. |
| `device_uuid` | String (UUID) | APP y SMS | Identificador único persistente del dispositivo Android físico para control y trazabilidad. |

---

## 5. Ejemplos Reales de las 4 Reglas Predeterminadas

### Regla 1: Nequi — Notificación Push (APP)

- **Origen:** Notificación Push emitida por la app oficial de Nequi (`com.nequi.MobileApp`).
- **Comportamiento del Título en Android:** En las notificaciones push de Nequi, el sistema de Android no recibe título en `Notification.EXTRA_TITLE` (se resuelve como cadena vacía `""`). Todo el detalle de la transacción se envía en el cuerpo del texto (`EXTRA_TEXT`), conteniendo siempre el **remitente**, el **monto** y la **referencia (código M...)**.
- **Filtro RegEx configurado:** `(?i)(te envi[oó]|te enviaron|te acaba(n)? de enviar|recibiste un pago|te transfirieron|pago recibido)`
- **Campos evaluados:** `title,text`

#### Plantilla Configurada:
```json
{
  "bank": "Nequi",
  "type": "{not_type}",
  "title": "{not_title}",
  "message": "{not_text}",
  "package_name": "{package_name}",
  "timestamp": "{timestamp}",
  "device_uuid": "{device_uuid}"
}
```

#### Ejemplo Real 1 (Formato estándar con Referencia M...):
> **Notificación en Android:**
> - **Título:** `""` *(Sin título en la notificación push de Nequi)*
> - **Texto:** `Carlos Mendoza te envió $50.000. Referencia: M12948201`

```json
{
  "bank": "Nequi",
  "type": "notification",
  "title": "",
  "message": "Carlos Mendoza te envió $50.000. Referencia: M12948201",
  "package_name": "com.nequi.MobileApp",
  "timestamp": "1726671982000",
  "device_uuid": "4c94bc12-70b1-49b8-a664-4b5b7b138612"
}
```

#### Ejemplo Real 2 (Variante con "a tu Nequi" y "Ref:"):
> **Notificación en Android:**
> - **Título:** `""`
> - **Texto:** `Carlos Mendoza te envió $ 50.000 a tu Nequi. Ref: M12948201`

```json
{
  "bank": "Nequi",
  "type": "notification",
  "title": "",
  "message": "Carlos Mendoza te envió $ 50.000 a tu Nequi. Ref: M12948201",
  "package_name": "com.nequi.MobileApp",
  "timestamp": "1726671982000",
  "device_uuid": "4c94bc12-70b1-49b8-a664-4b5b7b138612"
}
```

#### Ejemplo Real 3 (Variante con prefijo de exclamación en el cuerpo):
> **Notificación en Android:**
> - **Título:** `""`
> - **Texto:** `¡Te enviaron plata! Carlos Mendoza te envió $50.000 a tu Nequi. Ref: M12948201`

```json
{
  "bank": "Nequi",
  "type": "notification",
  "title": "",
  "message": "¡Te enviaron plata! Carlos Mendoza te envió $50.000 a tu Nequi. Ref: M12948201",
  "package_name": "com.nequi.MobileApp",
  "timestamp": "1726671982000",
  "device_uuid": "4c94bc12-70b1-49b8-a664-4b5b7b138612"
}
```

---

### Regla 2: Bancolombia — Notificación Push (APP)

- **Origen:** Notificación Push generada por Mi Bancolombia (`com.todo1.mobile`) o Bancolombia A la mano (`co.com.tcs.bancolombia.bancaalamano`).
- **Filtro RegEx:** `(?i)(transferencia recibida|recepci[oó]n de transferencia|le informamos transferencia|recibiste una transferencia|pago recibido|abono)`
- **Campos evaluados:** `title,text`

#### Plantilla Configurada:
```json
{
  "bank": "Bancolombia",
  "type": "{not_type}",
  "title": "{not_title}",
  "message": "{not_text}",
  "package_name": "{package_name}",
  "timestamp": "{timestamp}",
  "device_uuid": "{device_uuid}"
}
```

#### Ejemplo Real (Mi Bancolombia Personas):
> **Notificación en Android:**
> - **Título:** `Bancolombia`
> - **Texto:** `Bancolombia le informa recepción de transferencia por $ 150.000 de ANDRES PEREZ en cta *1234 el 18/09/2026 12:35. Ref: 123456.`

```json
{
  "bank": "Bancolombia",
  "type": "notification",
  "title": "Bancolombia",
  "message": "Bancolombia le informa recepción de transferencia por $ 150.000 de ANDRES PEREZ en cta *1234 el 18/09/2026 12:35. Ref: 123456.",
  "package_name": "com.todo1.mobile",
  "timestamp": "1726672535000",
  "device_uuid": "4c94bc12-70b1-49b8-a664-4b5b7b138612"
}
```

---

### Regla 3: Nequi — Mensaje de Texto (SMS)

- **Origen:** Mensaje SMS entrante del código oficial de Nequi `85954`.
- **Filtro RegEx:** `(?i)(nequi|85954)?.*?(te envi[oó]|te enviaron|te acaba(n)? de enviar|transfiri[oó]|recibiste|plata)`
- **Campos evaluados:** `sender,body`

#### Plantilla Configurada:
```json
{
  "bank": "Nequi",
  "type": "{not_type}",
  "sender": "{sms_sender}",
  "message": "{sms_text}",
  "timestamp": "{timestamp}",
  "device_uuid": "{device_uuid}"
}
```

#### Ejemplo Real (SMS Oficial de Nequi):
> **Mensaje de texto SMS:**
> - **Remitente:** `85954`
> - **Mensaje:** `Nequi: Te acaban de enviar $50.000 de Carlos Mendoza. Ref: M12948201. Tu nuevo saldo disponible es $120.000.`

```json
{
  "bank": "Nequi",
  "type": "sms",
  "sender": "85954",
  "message": "Nequi: Te acaban de enviar $50.000 de Carlos Mendoza. Ref: M12948201. Tu nuevo saldo disponible es $120.000.",
  "timestamp": "1726672700000",
  "device_uuid": "4c94bc12-70b1-49b8-a664-4b5b7b138612"
}
```

---

### Regla 4: Bancolombia — Mensaje de Texto (SMS)

- **Origen:** Mensaje SMS entrante del código oficial de Bancolombia `85432`.
- **Filtro RegEx:** `(?i)(bancolombia|85432)?.*?(transferencia|recepci[oó]n|abono|recibi[oó]|pago)`
- **Campos evaluados:** `sender,body`

#### Plantilla Configurada:
```json
{
  "bank": "Bancolombia",
  "type": "{not_type}",
  "sender": "{sms_sender}",
  "message": "{sms_text}",
  "timestamp": "{timestamp}",
  "device_uuid": "{device_uuid}"
}
```

#### Ejemplo Real (SMS Oficial de Bancolombia):
> **Mensaje de texto SMS:**
> - **Remitente:** `85432`
> - **Mensaje:** `Bancolombia le informa transferencia por $ 85.000 en su cta *4321 el 18/09/2026 12:40. Ref: 654321. Inquietudes al 6045109000 o 018000931987`

```json
{
  "bank": "Bancolombia",
  "type": "sms",
  "sender": "85432",
  "message": "Bancolombia le informa transferencia por $ 85.000 en su cta *4321 el 18/09/2026 12:40. Ref: 654321. Inquietudes al 6045109000 o 018000931987",
  "timestamp": "1726672800000",
  "device_uuid": "4c94bc12-70b1-49b8-a664-4b5b7b138612"
}
```

---

## 6. Expresiones Regulares para Extracción en Backend

Para extraer automáticamente los datos en tu servidor a partir del campo `message`:

### 1. Extracción del Remitente
- **Nequi (Push):** Extrae el nombre antes de `te envió`:
  ```regex
  ^(?:¡Te enviaron plata!\s*)?(.*?)\s+te envi[oó]
  ```
  *Captura:* `Carlos Mendoza`
- **Nequi (SMS):** Extrae el nombre después de `de`:
  ```regex
  de\s+([A-Za-zÁÉÍÓÚáéíóúñÑ\s]+?)\.\s*Ref
  ```
  *Captura:* `Carlos Mendoza`
- **Bancolombia (Push):** Extrae el nombre después de `de` y antes de `en cta`:
  ```regex
  de\s+([A-ZÁÉÍÓÚÑ\s]+?)\s+en cta
  ```
  *Captura:* `ANDRES PEREZ`

### 2. Extracción del Monto
- **Universal para Nequi y Bancolombia:**
  ```regex
  \$\s*([\d\.]+)
  ```
  *Captura:* `50.000`, `150.000`, `85.000` (puedes remover el punto para convertir a entero: `50000`).

### 3. Extracción de la Referencia de Pago
- **Nequi (Código M...):**
  ```regex
  (?:Ref|Referencia):\s*([A-Za-z0-9]+)
  ```
  *Captura:* `M12948201`
- **Bancolombia:**
  ```regex
  Ref:\s*([0-9]+)
  ```
  *Captura:* `123456` o `654321`

---

## 7. Buenas Prácticas de Integración

1. **Idempotencia (Evitar duplicados):**
   - Utiliza la **referencia extraída** (`M12948201`) como clave única en tu base de datos para garantizar que nunca se procese dos veces el mismo pago.
   - Si no extraes la referencia, combina `device_uuid + timestamp + message`.
2. **Validación de Integridad:**
   - Verifica la cabecera `X-NotifyBridge-Device-UUID` con el UUID registrado en tu panel para asegurarte de que proviene de tu dispositivo físico autorizado.
   - Agrega un token de autorización en la pestaña de Cabeceras de la regla (ej. `Authorization: Bearer mi_token_secreto`).
