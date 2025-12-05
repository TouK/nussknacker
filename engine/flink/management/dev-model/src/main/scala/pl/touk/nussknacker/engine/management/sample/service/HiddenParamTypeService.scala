package pl.touk.nussknacker.engine.management.sample.service

import pl.touk.nussknacker.engine.api.{HideType, MethodToInvoke, ParamName, Service}

import scala.concurrent.Future

case object HiddenParamTypeService extends Service {

  @MethodToInvoke
  def invoke(
      @ParamName("foo") @HideType foo: String,
  ) = Future.successful(())

}
