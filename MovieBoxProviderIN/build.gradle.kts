// use an integer for version numbers
version = 2

android {
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        // Allow secret keys to be injected via project properties or environment
        buildConfigField("String", "MOVIEBOX_SECRET_DEFAULT", "\"${project.findProperty("MOVIEBOX_SECRET_DEFAULT") ?: ""}\"")
        buildConfigField("String", "MOVIEBOX_SECRET_ALT", "\"${project.findProperty("MOVIEBOX_SECRET_ALT") ?: ""}\"")
    }
}

cloudstream {
    language = "ta"
    // All of these properties are optional, you can safely remove them

    description = "Multi Language Movies and Series Provider (IN)"
    authors = listOf("NivinCNC")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf(
        "Movie",
        "TvSeries"
    )

    iconUrl = "https://github.com/NivinCNC/CNCVerse-Cloud-Stream-Extension/raw/refs/heads/master/MovieBoxProvider/icon.png"
}
