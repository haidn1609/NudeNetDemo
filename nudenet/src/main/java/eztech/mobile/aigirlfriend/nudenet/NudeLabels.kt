package eztech.mobile.aigirlfriend.nudenet

/** 18 nhãn của NudeNet detector — THỨ TỰ index phải khớp output model, không đổi. */
object NudeLabels {
    val LABELS = arrayOf(
        "FEMALE_GENITALIA_COVERED", // 0
        "FACE_FEMALE",              // 1
        "BUTTOCKS_EXPOSED",         // 2
        "FEMALE_BREAST_EXPOSED",    // 3
        "FEMALE_GENITALIA_EXPOSED", // 4
        "MALE_BREAST_EXPOSED",      // 5
        "ANUS_EXPOSED",             // 6
        "FEET_EXPOSED",             // 7
        "BELLY_COVERED",            // 8
        "FEET_COVERED",             // 9
        "ARMPITS_COVERED",          // 10
        "ARMPITS_EXPOSED",          // 11
        "FACE_MALE",                // 12
        "BELLY_EXPOSED",            // 13
        "MALE_GENITALIA_EXPOSED",   // 14
        "ANUS_COVERED",             // 15
        "FEMALE_BREAST_COVERED",    // 16
        "BUTTOCKS_COVERED",         // 17
    )

    /** Nhóm nhãn "phơi bày" mặc định coi là NSFW cho [NudeNetDetector.isNsfw]. */
    val NSFW_EXPOSED: Set<String> = setOf(
        "FEMALE_BREAST_EXPOSED",
        "FEMALE_GENITALIA_EXPOSED",
        "MALE_GENITALIA_EXPOSED",
        "BUTTOCKS_EXPOSED",
        "ANUS_EXPOSED",
    )
}
