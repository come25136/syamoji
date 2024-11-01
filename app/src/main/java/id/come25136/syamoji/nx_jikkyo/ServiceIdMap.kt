package id.come25136.syamoji.nx_jikkyo

val serviceIdToJkIdMap = mapOf(
    "3273601024" to "JK1",  // NHK総合1・東京
    "3273601025" to "JK1",  // NHK総合2・東京
    "3273701032" to "JK2",  // NHK Eテレ1東京
    "3273701033" to "JK2",  // NHK Eテレ2東京
    "3273701034" to "JK2",  // NHK Eテレ3東京
    "3273801040" to "JK3",  // 日テレ1
    "3273801041" to "JK3",  // 日テレ2
    "3274101064" to "JK4",  // テレビ朝日1
    "3274101065" to "JK4",  // テレビ朝日2
    "3274101066" to "JK4",  // テレビ朝日3
    "3274201068" to "JK5",  // TBSテレビ1
    "3274201069" to "JK5",  // TBSテレビ2
    "3274301076" to "JK6",  // テレビ東京1
    "3274301077" to "JK6",  // テレビ東京2
    "3274001056" to "jk8", // フジテレビ
    "3274001057" to "jk8", // フジテレビ
    "3274001058" to "jk8", // フジテレビ
    "3239123608" to "jk9", // TOKYO MX1
    "3239123609" to "jk9", // TOKYO MX1
    "3239123610" to "jk9", // TOKYO MX2
    "3275401098" to "JK9",  // tvk
    "3275501048" to "JK10", // テレ玉
    "3276501032" to "JK11", // チバテレビ
    "3276601058" to "JK12", // サンテレビ
    "3276701036" to "JK13", // KBS京都
    "3275801168" to "JK101",  // NHK BS1
    "3275801169" to "JK101",  // NHK BS1 サブ
    "3275901170" to "JK103",  // NHK BSプレミアム1
    "3275901171" to "JK103",  // NHK BSプレミアム2
    "3276101184" to "JK141",  // BS日テレ
    "3276201188" to "JK151",  // BS朝日
    "3276301192" to "JK161",  // BS-TBS
    "3276401196" to "JK171",  // BSテレ東
    "3276501200" to "JK181",  // BSフジ
    "3276601204" to "JK191",  // WOWOW PRIME
    "3276601205" to "JK191",  // WOWOW PRIME サブ
    "3276601206" to "JK192",  // WOWOW LIVE
    "3276601207" to "JK192",  // WOWOW LIVE サブ
    "3276601208" to "JK193",  // WOWOW CINEMA
    "3276601209" to "JK193",  // WOWOW CINEMA サブ
    "3276701212" to "JK211",  // BS11
    "3276801216" to "JK222",  // BS12
    "3276901224" to "JK236",  // BSアニマックス
    "3277001232" to "JK252",  // WOWOW PLUS
    "3277101240" to "JK260",  // BS松竹東急
    "3277201244" to "JK263",  // BSJapanext
    "3277301248" to "JK265",  // BSよしもと
    "3277401252" to "JK333"   // AT-X
    // 他のチャンネルも必要に応じて追加可能
)

// MirakurunのService IDを入力としてJK IDを取得する関数
fun getJkIdFromChannelId(channelId: String): String {
    val jkId = serviceIdToJkIdMap[channelId]
        ?: throw Error("channelId is not mapping. channelId:${channelId}")

    return jkId
}