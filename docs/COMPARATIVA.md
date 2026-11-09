# FocusGuard frente a otras apps

Comparación hecha en noviembre de 2026 a partir de lo que cuentan las propias webs de las apps y reseñas de terceros. **Los precios y funciones de los demás son orientativos**: salen de comparativas, muchas con enlaces de afiliado, y no se comprobaron en las páginas oficiales ni probando las apps. Lo que se dice de FocusGuard sale del código y de las pruebas de este repositorio. Donde no hay dato fiable, la tabla dice «sin verificar».

## En una frase

FocusGuard es gratuita y de código abierto, se instala en un servidor propio y reúne en una sola app lo que normalmente hay que repartir entre un bloqueador de apps, un filtro de contenido adulto, una app de rendición de cuentas y un control parental. Lo que no tiene: versión para iPhone, y la solidez de una empresa con soporte.

## Las que se parecen más (rendición de cuentas y contenido adulto)

| | FocusGuard | Covenant Eyes | Accountable2You | EverAccountable | Canopy |
|---|---|---|---|---|---|
| Precio | Gratis (tu servidor) | Unos 18 USD al mes; 198 al año; hay plan de por vida (fuentes de terceros) | Unos 9,99 USD al mes (varios dispositivos) | Unos 9,99 USD al mes | Sin verificar |
| Código abierto | Sí (AGPL-3.0) | No | No | No | No |
| Dónde corren los datos | En tu teléfono y tu servidor | En el servicio de la empresa | En el servicio de la empresa | En el servicio de la empresa | En el servicio de la empresa |
| Informe a un compañero | Sí, por correo, hasta 3 | Sí | Sí | Sí | Sí |
| El compañero aprueba los cambios | Sí: bloquea hasta que apruebe, con modo parental | Sin verificar | Sin verificar | Sin verificar | Sin verificar |
| Capturas de pantalla al compañero | No (por diseño) | Sí, borrosas (según la ficha de la tienda) | Sin verificar | Sin verificar | Sin verificar |
| Detección de imágenes | Sí, en el teléfono; bloquea | Sí, informa al compañero | Sin verificar | Sin verificar | Sin verificar |
| Filtro DNS propio | Sí | Sin verificar | Sin verificar | Sin verificar | Sin verificar |
| iPhone | No | Sí (con restricciones) | Sí, pero con navegador propio | Sí | Sí |

Dos diferencias de fondo. **La privacidad:** en FocusGuard el compañero recibe qué ocurrió y cuándo, nunca lo que ves; en las demás hay servicios que revisan tu actividad en sus servidores. **El control:** en FocusGuard el compañero no solo mira, también decide cada cambio que debilita la protección.

## Control parental y familia

| | FocusGuard | Qustodio | Bark | Net Nanny |
|---|---|---|---|---|
| Precio | Gratis (tu servidor) | Unos 55 USD al año (5 dispositivos), 110 sin límite | Unos 99 USD al año (hijos y dispositivos sin límite) | Desde unos 50 USD al año (5 dispositivos) |
| Gratis con límites | Todo | Sí (según reseñas) | No | No |
| Bloquea apps | Sí | Sí | Según una reseña, bloquea internet y no apps | Sin verificar |
| Vigila mensajes y redes | No | Mensajes de texto (según reseñas) | Sí, es su punto fuerte | Filtra redes, no conversaciones |
| Pensada para | Adultos que se autocontrolan y familias | Familias | Familias | Familias |
| iPhone | No | Sí | Sí | Sí |

FocusGuard tiene modo parental (un compañero aprueba los cambios del teléfono de un hijo), pero **no vigila conversaciones**: no es su propósito.

## Bloqueadores de apps y webs para concentrarse

| | FocusGuard | Freedom | Opal | AppBlock | BlockSite | Cold Turkey |
|---|---|---|---|---|---|---|
| Precio | Gratis | Unos 40 USD al año (o 199 de por vida) | Unos 100 USD al año (una fuente) | Gratis o unos 30 a 36 USD al año | Gratis o unos 3 USD al mes | Gratis; 39 a 45 USD de pago único |
| Plataformas | Android | Varias | iPhone y iPad | Android, iPhone y extensiones | Extensiones y móvil | Windows y macOS |
| Horarios y límites | Sí | Sí | Sí | Sí | Básicos | Sí |
| Bloqueo por ubicación, Wi-Fi, cargador, Bluetooth | Sí | Sin verificar | Sin verificar | Sin verificar | No | No |
| Impide desactivarlo sin otra persona | Sí (compañero, modo estricto) | No | No | No | No | Modo bloqueado, saltable con permisos de administrador |
| Contenido adulto y DNS | Sí | No | No | No | Por lista | No |

## Lo que trae el teléfono

Android lleva **Bienestar digital** y **Family Link**; iPhone, **Tiempo de uso**. Son gratuitos y están integrados, pero se pueden apagar desde el propio teléfono, no tienen detección de imágenes ni compañero que apruebe cambios, y su filtro de webs adultas es limitado (sin verificar punto por punto).

## Dónde FocusGuard gana, dónde pierde

**Gana**
- Es gratis, abierto y con datos propios: nadie más que tú y tus compañeros ve nada.
- Reúne bloqueo, filtro adulto, DNS, imágenes y compañeros en una app.
- El compañero decide, no solo mira; con varios compañeros basta con que uno apruebe.
- Detecta imágenes sin sacarlas del teléfono.

**Pierde**
- **No hay versión para iPhone**, y varias funciones no se pueden hacer allí (ver el resumen de iOS más abajo).
- Hay que montar y mantener un servidor, un dominio y un correo.
- Una sola persona la prueba, en un solo teléfono. Las demás marcas llevan años de pruebas en miles de modelos.
- No vigila mensajes ni redes sociales, que es lo que buscan muchas familias.
- Los falsos avisos de la detección de imágenes con la sensibilidad alta son frecuentes.
- Un usuario con conocimientos puede saltarse la protección con el modo seguro, adb o un restablecimiento de fábrica.

## iPhone

iOS no permite leer ni capturar la pantalla de otras apps, ni cerrarlas, ni impedir que se desinstale una app. Se podría hacer una versión reducida con las APIs de Tiempo de uso y un filtro DNS por extensión de red, que piden permisos especiales a Apple. La detección de imágenes no se puede portar.

## Fuentes

Comparativas y fichas consultadas en noviembre de 2026: [Canopy, alternativas a Covenant Eyes](https://canopy.us/blog/covenant-eyes-alternatives/), [ficha de Covenant Eyes en la App Store](https://apps.apple.com/gb/app/covenant-eyes/id335318146), [AirDroid: Bark, Qustodio y Net Nanny](https://www.airdroid.com/parental-insights/bark-vs-qustodio-vs-net-nanny/), [All About Cookies: precio de Bark](https://www.allaboutcookies.org/bark-price), [Content Mavericks: software de rendición de cuentas](https://contentmavericks.com/best-accountability-software/), [TechCrunch: bloqueadores de distracciones](https://techcrunch.com/2025/12/25/the-best-distraction-blockers-to-jumpstart-your-focus-in-the-new-year), [Freedom: Freedom, Opal y BlockSite](https://freedom.to/blog/freedom-vs-opal-vs-blocksite-which-one-should-you-choose/). Comprueba los precios en las webs oficiales antes de decidir.
