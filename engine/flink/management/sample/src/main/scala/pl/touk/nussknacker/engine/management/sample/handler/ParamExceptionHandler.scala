package pl.touk.nussknacker.engine.management.sample.handler

import pl.touk.nussknacker.engine.api.exception.{EspExceptionHandler, ExceptionHandlerFactory}
import pl.touk.nussknacker.engine.api.{MetaData, MethodToInvoke, ParamName}
import pl.touk.nussknacker.engine.flink.util.exception.BrieflyLoggingExceptionHandler

case object ParamExceptionHandler extends ExceptionHandlerFactory {

  @MethodToInvoke
  def create(@ParamName("param1") param: String, metaData: MetaData): EspExceptionHandler =
    BrieflyLoggingExceptionHandler(metaData)

}
