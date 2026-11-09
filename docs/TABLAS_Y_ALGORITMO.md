# Tablas de datos y algoritmo de coincidencia

Datos de partida para no reconstruirlos a mano. Son datos factuales (nombres de paquetes e identificadores de vistas de otras apps) y una descripción de comportamiento. No hay código de otras apps. El código que los use se escribe desde cero.

Los identificadores cambian cuando las apps se actualizan. Hay que poder reemplazarlos desde el servidor (`GET /selectors`) sin publicar una versión nueva. Para comprobar uno en un teléfono real: `adb shell uiautomator dump` y buscar el `resource-id` del elemento.

## 1. Archivos en `data/seed/`

| Archivo | Contenido |
|---|---|
| `browser_view_ids.json` | 38 navegadores: `packageName`, `viewId` de la barra de direcciones y, en uno, `clearUrl: false` (no limpiar la URL antes de comparar). Los `viewId` empiezan por `:id/`; al buscar se antepone el paquete, por ejemplo `com.android.chrome:id/url_bar` |
| `incognito_view_ids.json` | 5 navegadores con el identificador de la insignia de incógnito (Chrome, Edge, Brave, Ecosia, Puffin) |
| `subapps.json` | secciones dentro de apps (Shorts, Reels…): `id`, `label`, `packages`, y `viewIds` sin el paquete (se antepone `paquete:id/`; los de `browser_view_ids.json` ya llevan `:id/`) o, si la app no los expone, `activity` más `texts`. Los identificadores cambian con las versiones de cada app: verificarlos en un teléfono real |
| `apps_webs_categories.csv` | unas 147 filas `paquete,dominio,categoría`. Categorías: browsers, communication, social, shopping, video, tools, music, travel, photo, education, dating, news, games, none. Sirve para bloquear por categoría y para sugerir la web relacionada de una app |

La app arranca con estos archivos como valores por defecto (en `assets/`) y los sustituye por los del servidor cuando hay versión más reciente.

## 2. Contenido dentro de apps

Se busca el elemento con `findAccessibilityNodeInfosByViewId("<paquete>:id/<id>")` en la ventana activa. Si aparece cualquiera de los de la lista, se considera que el contenido está en pantalla y se hace "atrás".

| Contenido | Paquete | Identificadores (basta uno) |
|---|---|---|
| YouTube Shorts | `com.google.android.youtube` y la variante `app.revanced.android.youtube` | `reel_player_page_container`, `reel_recycler` |
| Stories de Snapchat | `com.snapchat.android` | `df_large_story` |
| Spotlight de Snapchat | `com.snapchat.android` | `spotlight_attribution_title`, `spotlight_container`, `favorite`, `ngs_spotlight_icon_container` |
| Comunidad de Snapchat | `com.snapchat.android` | `ngs_community_icon_container` |
| Reels de Instagram | `com.instagram.android` | `comment_count`, `gesture_manager` |
| Stories de Instagram | `com.instagram.android` | `reel_viewer_timestamp` |
| Búsqueda de Instagram | `com.instagram.android` | `action_bar_search_edit_text`, `search_bar_real_field`, `search_bar_underline` |
| Mensajes directos de Instagram (campo de escritura) | `com.instagram.android` | `row_thread_composer_edittext` |
| Canales de WhatsApp | `com.whatsapp` | `newsletter_quick_forwarding_pill_container_key`, `follow_channel_button` |
| Estados de WhatsApp | `com.whatsapp` | `status_playback_image`, `status_playback_video` |
| Búsqueda de YouTube | `com.google.android.youtube` y ReVanced | `search_query`, `search_edit_text` |
| Reels de Facebook | `com.facebook.katana` | texto "Reels" visible y actividad `com.facebook.katana.immersiveactivity.ImmersiveActivity` |
| Búsqueda de X (Twitter) | `com.twitter.android` | `query`, `query_view` |
| Pantalla de resultados de búsqueda de Google (lente) | `com.google.android.googlequicksearchbox` | `lens_gallery_picker_button_m2_bg` |
| Reddit (títulos y perfil) | `com.reddit.frontpage` | `toolbar_title`, `profile_title`, `profile_name` |
| 9GAG | `com.ninegag.android.app` | `tvPostSectionTitle`, `recycler_view`, `home`, `drawer_handle` |

Texto de secciones de 9GAG que se bloquea cuando el contenido adulto está activo: `girl`, `nsfw`, `girlcelebrity`, `guy`.

## 3. Navegadores dentro de otras apps

Se consideran navegador integrado:
- La clase de vista `android.webkit.WebView` en las apps elegidas (o en todas).
- Facebook Lite: actividad `com.facebook.browser.lite.BrowserLiteInMainProcessBottomSheetActivity`.
- Line: actividad `com.linecorp.browser.OpenUriActivity` (paquete `jp.naver.line.android`).
- Outlook: `com.microsoft.office.outlook:id/browser_top_address`.
- Chrome en modo hoja inferior: `com.android.chrome:id/bottom_sheet`.

En Firefox Rocket (`org.mozilla.rocket`) se ignoran las pantallas de bandeja de pestañas (`tab_tray_recycler_view`) y de historial (`browsing_history_recycler_view`). En Chrome se ignoran los mosaicos de nueva pestaña (`tile_view_title`).

## 4. Pantallas del sistema que se protegen

Identificadores usados para detectar pantallas sensibles. Los textos varían con el idioma y el fabricante, así que conviene combinarlos con comprobación de la clase de la actividad y con una tabla de títulos por idioma.

| Pantalla | Identificadores |
|---|---|
| Panel de notificaciones y ajustes rápidos | `com.android.systemui:id/notification_panel`, `tiles_edit`, `tile_label`, `edit_button`, `more_button`; `android:id/edit` |
| Detalle de un ajuste rápido | `com.android.systemui:id/qs_detail_header` |
| Cuadro de diálogo del sistema (título) | `android:id/alertTitle` |
| Instalador de paquetes (nombre de la app a desinstalar) | `com.android.packageinstaller:id/app_label` |
| Apps recientes | `:id/recents_container`, `:id/overview_panel`, `:id/taskView` (el paquete depende del lanzador) |
| Menú de apagado y reinicio | diálogo `globalactionsdialog` (y uno propio de Xiaomi) |
| Plugin de la interfaz de Xiaomi | `miui.systemui.plugin:id/touch_container` |

Pantallas que se protegen con el modo estricto: ajustes de accesibilidad, administradores de dispositivo, información de la propia app, desinstalar, restablecer de fábrica, ahorro extremo de batería. El ahorro extremo se reconoce por el título de la pantalla. La app original solo cubre el modo de ahorro de Samsung y similares, con estas 17 cadenas (se comparan sin espacios y en minúsculas, por subcadena): `Ultra battery saver`, `초절전모드`, `초절전`, `סוללה חסכ'`, `חיסכון גבוה במיוחד`, `ウルトラ 省電力`, `ウルトラ省電力の`, `Ultrabatería`, `ahorro de energía ultra`, `Ultra-Akku`, `Akkuopti`, `Energiesparmodus`, `Ultra-Stromsparen`, `Gestion d'alim. Ultra`, `gestion d'alimentation Ultra`, `Режим Ультра`, `ultra power saving`. Cubre inglés, coreano, hebreo, japonés, español, alemán, francés y ruso. Otros fabricantes y idiomas hay que añadirlos probando en el dispositivo.

Cambio de usuario (se bloquea para impedir saltarse el bloqueo con otro perfil): títulos `Add user` y `Add guest`, comparados igual.

Dominios de buscadores que se tratan como búsqueda: `duckduckgo.`, `google.`, `yandex.com`, `bing.`, `yahoo.`.

## 5. Listas fijas por categoría

La app original lleva listas internas por categoría de contenido (redes sociales, tiendas, citas, apuestas, juegos y variantes de dominios de Facebook). Aquí se sustituyen por el CSV de categorías y por una lista de dominios que mantiene el servidor (`GET /blocklists`). No se copian listas largas de dominios al repositorio: se descargan de fuentes públicas de listas de bloqueo (por ejemplo StevenBlack) y se complementan con la lista propia.

## 6. Algoritmo de coincidencia de palabras

Entrada: el texto de un nodo de pantalla o de la barra de direcciones, el paquete, y tres banderas (lista de permitidos "solo usuario", el texto viene de la barra de direcciones, el navegador es compatible). Pasos, en orden:

1. **Bloqueos previos de URL.**
   - Si la URL contiene `instagram.com/explore` con el interruptor de búsqueda de Instagram, o `youtube.com/shorts` con el de Shorts, y no está en la lista de permitidos → bloquear.
   - Parámetros de búsqueda de imágenes y vídeos: Google `tbm=isch`, `udm=7`, `udm=2`, `tbm=vid`; Bing `form=hdrsc2` y `hdrsc3`; Yahoo.
2. **Idioma.** Si cambió el idioma del teléfono desde la última vez, recargar las palabras del servidor para ese idioma. Si alguna lista está vacía, recargarla.
3. **Decodificar** el texto como URL (UTF-8).
4. **Dos versiones normalizadas, ambas en minúsculas:**
   - *A:* sin símbolos ni puntuación, conservando los espacios y el `+` solo si forma "18+".
   - *B:* igual que A pero sin espacios ni separadores. En portugués y español no se quitan los espacios.
5. **Lista de permitidos.** Si A o B contienen una entrada permitida, o una etiqueta de dominio permitida → se registra y no se bloquea.
6. **Regla fija "porn".** Con el bloqueo de contenido adulto activo y un navegador compatible, quitar todo lo que no sea letra; si queda la subcadena `porn` → bloquear.
7. **Palabras y dominios del servidor.** Con el contenido adulto activo, buscar en A (subcadena) la lista de palabras; si el texto viene de la barra de direcciones y se cumplen las condiciones de VPN o búsqueda segura, usar la lista de dominios. Si coincide → bloquear.
8. **Lista de bloqueo del usuario.** Con el interruptor general de listas, buscar en A y luego en B sus palabras y webs.
9. **9GAG.** Si el paquete es `com.ninegag.android.app` y A contiene alguna de las secciones del apartado 2 → bloquear.
10. Si nada coincide, no bloquear.

La coincidencia es siempre por subcadena sobre texto ya limpiado; no hay separación en palabras ni distancia de edición. El motivo y las palabras que coincidieron se pasan a la pantalla de bloqueo.

Valoración: es simple y propensa a falsos positivos (por ejemplo "pornography" en un artículo de noticias). Para la versión propia conviene añadir una lista de excepciones por dominio y un umbral: más de una coincidencia, o coincidencia en la barra de direcciones, antes de bloquear por texto de pantalla.

### Búsqueda segura sin VPN
Reconocer si la URL ya lleva el parámetro seguro: Google `safe=active`, Bing `adlt=strict`, Yahoo `vm=r`, Yandex `family=yes`, DuckDuckGo en `safe.duckduckgo.com`. Si no lo lleva, reescribir la dirección con el parámetro.

### Validación de una web añadida por el usuario
Quitar espacios y pasar a minúsculas; colapsar puntos repetidos; exigir al menos un punto; longitud mínima 2 si el idioma del teléfono es coreano, chino o japonés y 3 en cualquier otro; quitar `http://`, `https://` y `www.`; rechazar si contiene una palabra de la lista de contenido adulto (no tiene sentido añadirla, ya está bloqueada).

## 7. Aprobaciones: identificador de acción

Cada solicitud de aprobación lleva el identificador de la protección que se debilita. Valores iniciales para el servidor y el teléfono:

| Id | Protección |
|---|---|
| 1 | Modo estricto de webs y bloqueo adulto principal |
| 2 | Evitar desinstalación |
| 4 | Compañero de responsabilidad |
| 6 | Búsqueda de imágenes y vídeo |
| 9 | Apps recién instaladas |
| 11 | VPN / DNS |
| 12 | Búsqueda segura sin VPN |
| 13 | Envío de informe |
| 14 | Panel de notificaciones |
| 15 | Redirección de URL |
| 16 | Evitar desinstalación un día |
| 20 | Navegador integrado |
| 21 | Todos los navegadores |
| 23 | YouTube Shorts |
| 24 | Reels de Instagram |
| 25 | Búsqueda de Instagram |
| 26 | Búsqueda de Telegram |
| 27 | Stories de Snapchat |
| 28 | Horario personalizado |
| 29 | Compras en apps y web |
| 30 | Reinicio del teléfono |
| 31 | Ahorro extremo de batería |
| 1001 a 1006 | Quitar un elemento (palabra, web o app) de la lista de bloqueo o de permitidos |

Se puede renumerar; lo importante es que el teléfono y el servidor compartan la tabla.
