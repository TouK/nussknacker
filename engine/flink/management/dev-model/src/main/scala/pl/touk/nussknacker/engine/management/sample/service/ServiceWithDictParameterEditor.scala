package pl.touk.nussknacker.engine.management.sample.service

import pl.touk.nussknacker.engine.api.editor.{SimpleEditor, SimpleEditorType}
import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName, Service}
import pl.touk.nussknacker.engine.util.service.TimeMeasuringService

import scala.concurrent.Future

class ServiceWithDictParameterEditor extends Service with Serializable with TimeMeasuringService {

  override protected def serviceName: String = "serviceWithDictParameterEditor"

  @MethodToInvoke
  def invoke(
      @ParamName("RGBDict")
      @SimpleEditor(
        `type` = SimpleEditorType.DICT_EDITOR,
        dictId = "rgb"
      )
      rgb: Option[String],
      @ParamName("BooleanDict")
      @SimpleEditor(
        `type` = SimpleEditorType.DICT_EDITOR,
        dictId = "boolean_dict"
      )
      booleanDict: Option[Boolean],
      @ParamName("LongDict")
      @SimpleEditor(
        `type` = SimpleEditorType.DICT_EDITOR,
        dictId = "long_dict"
      )
      longDict: Option[Long]
  ): Future[String] = {
    Future.successful(s"""RGBDict: $rgb
         |LongDict: $longDict
         |BooleanDict: $booleanDict""".stripMargin)
  }

}
