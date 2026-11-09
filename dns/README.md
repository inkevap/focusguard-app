# Resolvedor DNS (AdGuard Home)

Los teléfonos mandan sus consultas DNS cifradas (DNS sobre HTTPS) a `https://dns.<tu dominio>/dns-query/<id del dispositivo>`.
Caddy pone el HTTPS y reenvía solo esa ruta a AdGuard Home. El resolvedor rechaza los dominios de las listas del servidor,
fuerza la búsqueda segura (B9) y reenvía el resto a resolvedores públicos cifrados.

- **Una sola lista:** AdGuard Home descarga `GET /blocklists/dns-rules.txt` del servidor (red interna de Docker). Es la misma
  lista que usa la app; las excepciones del servidor se permiten también aquí. No descarga nada de GitHub por su cuenta.
- **Identificador de dispositivo:** la app pide a `GET /dns/config?deviceId=…` su URL con un identificador firmado, que sirve para
  contar consultas bloqueadas por dispositivo. No se guarda historial de consultas ni la IP del cliente. El historial que ve el usuario (D8) lo lleva su propio teléfono.
- **Si el resolvedor no responde:** la app usa el DNS del sistema y avisa (decisión «fallar abierto»), para no dejar al teléfono
  sin internet.
- **No es un resolvedor privado:** cualquiera que escriba una ruta `/dns-query/<24 cifras>` puede usarlo; Caddy solo comprueba la
  forma. Restringirlo a los identificadores registrados exige darle a AdGuard Home la lista (`allowed_clients`) desde el servidor.
- **Panel:** solo en `127.0.0.1:3001` del VPS. Para abrirlo: `ssh -L 3001:localhost:3001 usuario@vps` y `http://localhost:3001`.
  No lleva contraseña porque no es alcanzable desde fuera; si lo expones, añade un usuario con hash bcrypt en el volumen
  (nunca en este repositorio).
- **Configuración:** `AdGuardHome.yaml` es la plantilla de la primera vez. Después manda el volumen `dns-conf`.

## Variables (`.env`)

`DNS_DOMAIN` es el nombre público del resolvedor (por ejemplo `dns.midominio.com`, con su registro DNS apuntando al VPS).
Compose lo usa para Caddy (certificado automático) y para `DNS_DOH_URL` del servidor.

## Lo que no hace

- No bloquea apps que llevan su propio DNS cifrado a otro servidor; eso se tapa con la lista de dominios de DoH conocidos y
  con la VPN de la app, que solo captura el tráfico DNS.
- No ofrece DNS privado de Android (DoT, puerto 853): la app usa su propia VPN local.
