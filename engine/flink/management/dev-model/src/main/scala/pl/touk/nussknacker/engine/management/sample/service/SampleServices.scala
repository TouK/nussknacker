package pl.touk.nussknacker.engine.management.sample.service

import java.util
import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName, Service}
import pl.touk.nussknacker.engine.api.editor.{LabeledExpression, SimpleEditor, SimpleEditorType}
import pl.touk.nussknacker.engine.management.sample.dto.{ComplexObject, RichObject}
import pl.touk.nussknacker.engine.management.sample.TariffType
import pl.touk.sample.JavaSampleEnum

import java.util.Optional
import scala.collection.JavaConverters._
import scala.concurrent.Future

case object EmptyService extends Service {
  @MethodToInvoke
  def invoke(): Future[Unit.type] = Future.successful(Unit)
}

case object OneParamService extends Service {
  @MethodToInvoke
  def invoke(@SimpleEditor(
    `type` = SimpleEditorType.FIXED_VALUES_EDITOR,
    possibleValues = Array(
      new LabeledExpression(expression = "'a'", label = "a"),
      new LabeledExpression(expression = "'b'", label = "b"),
      new LabeledExpression(expression = "'c'", label = "c")
    )
  )
             @ParamName("param") param: String): Future[String] =
    Future.successful(param)
}

case object ComplexReturnObjectService extends Service {
  @MethodToInvoke
  def invoke(): Future[ComplexObject] = {
    Future.successful(ComplexObject(Map("foo" -> 1, "bar" -> "baz").asJava))
  }
}

case object Enricher extends Service {
  @MethodToInvoke
  def invoke(@ParamName("param") param: String, @ParamName("tariffType") tariffType: TariffType): Future[RichObject] =
    Future.successful(RichObject(param, 123L, Optional.of("rrrr")))
}

case object ListReturnObjectService extends Service {

  @MethodToInvoke
  def invoke() : Future[java.util.List[RichObject]] = {
    Future.successful(util.Arrays.asList(RichObject("abcd1", 1234L, Optional.of("defg"))))
  }

}

object EchoEnumService extends Service {

  @MethodToInvoke
  def invoke(@ParamName("id") id: JavaSampleEnum): Future[JavaSampleEnum] = Future.successful(id)

}
