package org.jetbrains.kotlinconf.model

data class Speaker(
    val id: String,
    val name: String,
    val role: String,
    val company: String,
    val bio: String,
    val initials: String,
)

data class Session(
    val id: String,
    val title: String,
    val speaker: Speaker,
    val time: String,
    val room: String,
    val track: String,
    val description: String,
    val isKeynote: Boolean = false,
)

object ConferenceData {
    val speakers = listOf(
        Speaker(
            id = "roman-elizarov",
            name = "Roman Elizarov",
            role = "Team Lead",
            company = "JetBrains",
            bio = "Leads the Kotlin language development and coroutines library.",
            initials = "RE",
        ),
        Speaker(
            id = "sebastian-aigner",
            name = "Sebastian Aigner",
            role = "Developer Advocate",
            company = "JetBrains",
            bio = "Advocates for Compose Multiplatform and multiplatform UI tooling.",
            initials = "SA",
        ),
        Speaker(
            id = "svetlana-isakova",
            name = "Svetlana Isakova",
            role = "Co-author & DevRel",
            company = "JetBrains",
            bio = "Co-author of Kotlin in Action and developer advocate.",
            initials = "SI",
        ),
        Speaker(
            id = "hadi-hariri",
            name = "Hadi Hariri",
            role = "VP Developer Experience",
            company = "JetBrains",
            bio = "Developer, speaker, author, and host of JetBrains podcasts.",
            initials = "HH",
        ),
        Speaker(
            id = "anton-arhipov",
            name = "Anton Arhipov",
            role = "Developer Advocate",
            company = "JetBrains",
            bio = "Focuses on server-side Kotlin, Kotlin notebooks, and cloud tooling.",
            initials = "AA",
        ),
    )

    val sessions = listOf(
        Session(
            id = "session-1",
            title = "Keynote: What's Next in Kotlin & Multiplatform",
            speaker = speakers[0],
            time = "09:00 - 10:00",
            room = "Main Hall",
            track = "Keynote",
            description = "Discover the strategic vision for Kotlin 2.0, the K2 compiler, " +
                "and cross-platform shared UI expansion across desktop and mobile.",
            isKeynote = true,
        ),
        Session(
            id = "session-2",
            title = "Compose Multiplatform for Production Apps",
            speaker = speakers[1],
            time = "10:15 - 11:00",
            room = "Auditorium A",
            track = "Multiplatform",
            description = "Explore real-world architecture patterns for sharing UI components " +
                "fluidly between Android, iOS, and Desktop targets.",
        ),
        Session(
            id = "session-3",
            title = "Compiler Internals: How K2 Powers Modern Tooling",
            speaker = speakers[2],
            time = "11:15 - 12:00",
            room = "Hall B",
            track = "Language",
            description = "An in-depth look into the frontend architecture of the K2 compiler, " +
                "optimizations for symbol resolution, and IDE responsiveness.",
        ),
        Session(
            id = "session-4",
            title = "High-Performance Coroutines in Distributed Systems",
            speaker = speakers[3],
            time = "13:30 - 14:15",
            room = "Hall C",
            track = "Backend",
            description = "Designing fault-tolerant, high-throughput microservices using " +
                "structured concurrency, Flow pipelines, and backpressure handling.",
        ),
        Session(
            id = "session-5",
            title = "Crafting Developer Experience with Kotlin",
            speaker = speakers[4],
            time = "14:30 - 15:15",
            room = "Auditorium A",
            track = "Tools",
            description = "Techniques for building developer-friendly CLI tools, Gradle plugins, " +
                "and custom DSLs that accelerate engineering teams.",
        ),
    )
}
