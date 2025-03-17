package pl.touk.nussknacker.engine.management.sample.service

import com.cronutils.model.Cron
import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName, Service}
import pl.touk.nussknacker.engine.api.editor.{Editor, EditorType}

import java.time.{Duration, LocalDate, LocalDateTime, LocalTime, Period, ZonedDateTime}
import java.time.temporal.ChronoUnit
import scala.concurrent.Future

class DatesTypesService extends Service with Serializable {

  @MethodToInvoke
  def invoke(
      @ParamName("dateTimeParam") dateTimeParam: LocalDateTime,
      @ParamName("dateParam") dateParam: LocalDate,
      @ParamName("timeParam") timeParam: LocalTime,
      @ParamName("zonedDataTimeParam") zonedDataTimeParam: ZonedDateTime,
      @ParamName("durationParam")
      @Editor(
        `type` = EditorType.DURATION_EDITOR,
        timeRangeComponents = Array(ChronoUnit.DAYS, ChronoUnit.HOURS),
        isMainEditor = true
      )
      @Editor(`type` = EditorType.SPEL_EDITOR)
      duration: Duration,
      @ParamName("periodParam")
      @Editor(
        `type` = EditorType.PERIOD_EDITOR,
        timeRangeComponents = Array(ChronoUnit.YEARS, ChronoUnit.MONTHS),
        isMainEditor = true
      )
      @Editor(`type` = EditorType.SPEL_EDITOR)
      period: Period,
      @ParamName("cronScheduleParam")
      @Editor(`type` = EditorType.CRON_EDITOR)
      cronScheduleParam: Cron
  ): Future[Unit] = {
    ???
  }

}
