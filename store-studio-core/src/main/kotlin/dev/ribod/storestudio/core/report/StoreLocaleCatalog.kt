package dev.ribod.storestudio.core.report

/**
 * Multi-locale UI chrome string dictionary for store simulations.
 */
internal object StoreLocaleCatalog {

    /**
     * Serialized JSON dictionary injected into store preview and gallery reports.
     */
    fun toJson(): String = """
        {
          "en-US": {
            "play": {
              "nav": ["Games", "Apps", "Movies", "Books", "Children"],
              "tags": "Contains ads · In-app purchases",
              "reviews": "124K reviews",
              "downloads": "10B+ Downloads",
              "contentRating": "Rated for 3+",
              "install": "Install",
              "share": "Share",
              "deviceHint": "This app is available on all your devices",
              "about": "About this app",
              "safety": "Data safety",
              "safetyIntro": "Safety starts with understanding how developers collect your data.",
              "safety1": "No data shared with third parties",
              "safety2": "Data encrypted in transit",
              "safety3": "You can request that data be deleted",
              "chip1": "#1 Top Free",
              "chip3": "Productivity & Tools",
              "allDevices": "All devices"
            },
            "apple": {
              "nav": ["Store", "Mac", "iPad", "iPhone", "Watch", "Support"],
              "pricing": "Free · Offers In-App Purchases",
              "get": "GET",
              "ratingsTerm": "2.1M RATINGS",
              "ratingsDef": "4.8 ★★★★★",
              "ratingsSub": "out of 5 stars",
              "ageTerm": "AGE",
              "ageDef": "4+",
              "ageSub": "Years Old",
              "chartTerm": "CHART",
              "chartDef": "No. 1",
              "devTerm": "DEVELOPER",
              "devSub": "Developer",
              "langTerm": "LANGUAGE",
              "langDef": "EN",
              "langSub": "+ 12 More",
              "sizeTerm": "SIZE",
              "sizeDef": "48 MB",
              "sizeSub": "Universal",
              "whatsNew": "What's New",
              "version": "Version 1.0.0 · 2d ago",
              "info": "Information",
              "seller": "Provider",
              "size": "Size",
              "category": "Category",
              "compatibility": "Compatibility",
              "compatibilityVal": "Requires iOS 16.0 or later",
              "languages": "Languages",
              "languagesVal": "English, Spanish",
              "ageRating": "Age Rating",
              "copyright": "Copyright",
              "price": "Price",
              "freeVal": "Free",
              "allDevices": "All"
            }
          },
          "es-MX": {
            "play": {
              "nav": ["Juegos", "Apps", "Películas", "Libros", "Niños"],
              "tags": "Contiene anuncios · Compras directas desde la app",
              "reviews": "124K opiniones",
              "downloads": "10 mil M+ Descargas",
              "contentRating": "Para mayores de 3 años",
              "install": "Instalar",
              "share": "Compartir",
              "deviceHint": "Esta app está disponible para todos tus dispositivos",
              "about": "Acerca de esta app",
              "safety": "Seguridad de los datos",
              "safetyIntro": "La seguridad empieza con saber cómo recopilan tus datos.",
              "safety1": "No se comparten datos con terceros",
              "safety2": "Datos encriptados en tránsito",
              "safety3": "Puedes solicitar que se borren los datos",
              "chip1": "#1 más exitoso",
              "chip3": "Herramientas y productividad",
              "allDevices": "Todos los dispositivos"
            },
            "apple": {
              "nav": ["Store", "Mac", "iPad", "iPhone", "Watch", "Soporte"],
              "pricing": "Gratis · Compras dentro de la app",
              "get": "OBTENER",
              "ratingsTerm": "2,1 M VALORACIONES",
              "ratingsDef": "4,8 ★★★★★",
              "ratingsSub": "de 5 estrellas",
              "ageTerm": "EDAD",
              "ageDef": "4+",
              "ageSub": "Años",
              "chartTerm": "CLASIFICACIÓN",
              "chartDef": "N.º 1",
              "devTerm": "DESARROLLADOR",
              "devSub": "Creador",
              "langTerm": "IDIOMA",
              "langDef": "ES",
              "langSub": "+ 12 más",
              "sizeTerm": "TAMAÑO",
              "sizeDef": "48 MB",
              "sizeSub": "Universal",
              "whatsNew": "Novedades",
              "version": "Versión 1.0.0 · Hace 2 días",
              "info": "Información",
              "seller": "Vendedor",
              "size": "Tamaño",
              "category": "Categoría",
              "compatibility": "Compatibilidad",
              "compatibilityVal": "Requiere iOS 16.0 o posterior",
              "languages": "Idiomas",
              "languagesVal": "Español, Inglés",
              "ageRating": "Edad",
              "copyright": "Derechos de autor",
              "price": "Precio",
              "freeVal": "Gratis",
              "allDevices": "Todos"
            }
          }
        }
    """.trimIndent()
}
