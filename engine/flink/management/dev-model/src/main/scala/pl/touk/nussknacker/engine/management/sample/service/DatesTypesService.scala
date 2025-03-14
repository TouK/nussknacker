package pl.touk.nussknacker.engine.management.sample.service

import com.cronutils.model.Cron
import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName, Service}
import pl.touk.nussknacker.engine.api.editor.{ParameterEditor, ParameterEditorType, SpelEditor}

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
      @ParameterEditor(
        `type` = ParameterEditorType.DURATION_EDITOR,
        timeRangeComponents = Array(ChronoUnit.DAYS, ChronoUnit.HOURS)
      )
      @ParameterEditor(`type` = ParameterEditorType.SPEL_EDITOR)
      duration: Duration,
      @ParamName("periodParam")
      @ParameterEditor(
        `type` = ParameterEditorType.PERIOD_EDITOR,
        timeRangeComponents = Array(ChronoUnit.YEARS, ChronoUnit.MONTHS)
      )
      @ParameterEditor(`type` = ParameterEditorType.SPEL_EDITOR)
      period: Period,
      @ParamName("cronScheduleParam")
      @ParameterEditor(`type` = ParameterEditorType.CRON_EDITOR)
      cronScheduleParam: Cron
  ): Future[Unit] = {
    ???
  }

}
