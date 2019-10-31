package scalikejdbc
package warts

import org.wartremover.test.WartTestTraverser
import org.junit.Test
import java.time._

class ScalikejdbcDefaultZoneIdTest {
  @Test def javaTimeZonedDateTimeDefault = {
    val result = WartTestTraverser(ScalikejdbcDefaultZoneId) {
      TypeBinder.of[ZonedDateTime]
    }
    assert(result.errors.size == 1, result)
  }

  @Test def javaTimeOffsetDateTimeDefault = {
    val result = WartTestTraverser(ScalikejdbcDefaultZoneId) {
      TypeBinder.of[OffsetDateTime]
    }
    assert(result.errors.size == 1, result)
  }

  @Test def javaTimeLocalDateDefault = {
    val result = WartTestTraverser(ScalikejdbcDefaultZoneId) {
      TypeBinder.of[LocalDate]
    }
    assert(result.errors.size == 1, result)
  }

  @Test def javaTimeLocalTimeDefault = {
    val result = WartTestTraverser(ScalikejdbcDefaultZoneId) {
      TypeBinder.of[LocalTime]
    }
    assert(result.errors.size == 1, result)
  }

  @Test def javaTimeLocalDateTimeDefault = {
    val result = WartTestTraverser(ScalikejdbcDefaultZoneId) {
      TypeBinder.of[LocalDateTime]
    }
    assert(result.errors.size == 1, result)
  }

  @Test def int = {
    val result = WartTestTraverser(ScalikejdbcDefaultZoneId) {
      TypeBinder.of[Int]
    }
    assert(result.errors.isEmpty, result)
  }

  @Test def hasOverwrittenZoneId = {
    val result = WartTestTraverser(ScalikejdbcDefaultZoneId) {
      implicit val z: OverwrittenZoneId = OverwrittenZoneId(ZoneId.of("Asia/Tokyo"))
      List(
        TypeBinder.of[ZonedDateTime],
        TypeBinder.of[OffsetDateTime],
        TypeBinder.of[LocalDate],
        TypeBinder.of[LocalTime],
        TypeBinder.of[LocalDateTime]
      )
    }
    assert(result.errors.isEmpty, result)
  }

  @Test def suppress = {
    val result = WartTestTraverser(ScalikejdbcDefaultZoneId) {
      @SuppressWarnings(Array("scalikejdbc.warts.ScalikejdbcDefaultZoneId"))
      val x = List(
        TypeBinder.of[ZonedDateTime],
        TypeBinder.of[OffsetDateTime],
        TypeBinder.of[LocalDate],
        TypeBinder.of[LocalTime],
        TypeBinder.of[LocalDateTime]
      )
      x
    }
    assert(result.errors.isEmpty, result)
  }
}
