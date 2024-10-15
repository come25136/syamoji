package id.come25136.syamoji.iptv

import org.simpleframework.xml.transform.Transform
import org.threeten.bp.ZonedDateTime
import org.threeten.bp.format.DateTimeFormatter

class ZonedDateTimeConverter : Transform<ZonedDateTime> {
    private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z")

    override fun read(value: String): ZonedDateTime {
        return ZonedDateTime.parse(value, formatter)
    }

    override fun write(value: ZonedDateTime): String {
        return value.format(formatter)
    }
}
