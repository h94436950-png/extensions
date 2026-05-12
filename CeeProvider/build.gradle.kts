// Use an integer for version numbers
version = 1

cloudstream {
    description = "Watch content from Cee (cee.buzz)"
    authors = listOf("Cee")

    /**
     * Status int as one of the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta-only
     */
    status = 1

    tvTypes = listOf("Movie", "TvSeries")
    iconUrl = "https://www.google.com/s2/favicons?domain=cee.buzz&sz=%size%"
    language = "ar"

    isCrossPlatform = true
}

android {
    namespace = "com.cee"
}
