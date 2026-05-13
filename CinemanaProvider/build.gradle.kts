// Use an integer for version numbers
version = 1

cloudstream {
    description = "لا يعمل خارج العراق أو أي شبكة غير إيرثلنك"
    authors = listOf("muxt0")

    /**
     * Status int as one of the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta-only
     */
    status = 1

    tvTypes = listOf("Movie", "TvSeries")
    iconUrl = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTZlAGH56cEnNEL93W3QqZWpUe8XR8i90olTA&s"
    language = "ar"

    isCrossPlatform = true
}

android {
    namespace = "cc.shabakaty.cinemana"
}
