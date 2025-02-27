package pl.touk.nussknacker.engine.management.sample.service

import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName, Service}

import java.time.LocalDateTime
import java.util.Optional
import javax.annotation.Nullable
import scala.concurrent.Future

class OptionalTypesService extends Service with Serializable {

  @MethodToInvoke
  def invoke(
      @ParamName("scalaOptionParam") scalaOptionParam: Option[
        Integer
      ], // must be boxed type because primitive types are not recognized correctly
      @ParamName("javaOptionalParam") javaOptionalParam: Optional[Integer],
      @ParamName("nullableParam") @Nullable nullableParam: Integer,
      @ParamName("dateTimeParam") @Nullable dateTimeParam: LocalDateTime,
      @ParamName("overriddenByDevConfigParam") overriddenByDevConfigParam: Option[String],
      @ParamName("overriddenByFileConfigParam") overriddenByFileConfigParam: Option[String]
  ): Future[Unit] = {
    ???
  }

}
