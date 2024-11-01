package id.come25136.syamoji.nx_jikkyo

val serviceIdToJkIdMap = mapOf(
    "3239123608" to "jk9",
    "3239123609" to "jk9",
    "3239123610" to "jk9",
    "3274001056" to "jk8",
    "3274001057" to "jk8",
    "3274001058" to "jk8",
    "3274001440" to "jk8",
    "3273901048" to "jk6",
    "3273901049" to "jk6",
    "3273901432" to "jk6",
    "3274201072" to "jk7",
    "3274201073" to "jk7",
    "3274201074" to "jk7",
    "3274201456" to "jk7",
    "3274101064" to "jk5",
    "3274101065" to "jk5",
    "3274101066" to "jk5",
    "3274101448" to "jk5",
    "3273801040" to "jk4",
    "3273801041" to "jk4",
    "3273701032" to "jk2",
    "3273701033" to "jk2",
    "3273701034" to "jk2",
    "3273601024" to "jk1",
    "3273601025" to "jk1",
    "400151" to "jk151",
    "400161" to "jk161",
    "400171" to "jk171",
    "400211" to "jk211",
    "400141" to "jk141",
    "400181" to "jk181",
    "400101" to "jk101",
)

// MirakurunのService IDを入力としてJK IDを取得する関数
fun getJkIdFromChannelId(channelId: String): String {
    val jkId = serviceIdToJkIdMap[channelId]
        ?: throw Error("channelId is not mapping. channelId:${channelId}")

    return jkId
}