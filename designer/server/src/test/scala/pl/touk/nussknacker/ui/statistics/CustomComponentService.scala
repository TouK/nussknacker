package custom.component

import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName, Service}

import scala.concurrent.Future

object CustomComponentService extends Service {

  @MethodToInvoke
  def method(
      @ParamName("paramStringEditor")
      param: String
  ): Future[String] = ???

}
