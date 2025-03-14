package pl.touk.nussknacker.engine.management.sample.service

import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName, Service}
import pl.touk.nussknacker.engine.api.editor.{ParameterEditor, ParameterEditorType, SpelEditor}
import pl.touk.nussknacker.engine.util.service.TimeMeasuringService

import scala.concurrent.Future

class ServiceWithDictParameterEditor extends Service with Serializable with TimeMeasuringService {

  override protected def serviceName: String = "serviceWithDictParameterEditor"

  @MethodToInvoke
  def invoke(
      @ParamName("RGBDict")
      @ParameterEditor(`type` = ParameterEditorType.DICT_EDITOR, dictId = "rgb")
      rgb: String,
      @ParamName("BooleanDict")
      @ParameterEditor(`type` = ParameterEditorType.DICT_EDITOR, dictId = "boolean_dict")
      @ParameterEditor(`type` = ParameterEditorType.SPEL_EDITOR)
      booleanDict: Option[java.lang.Boolean],
      @ParamName("LongDict")
      @ParameterEditor(`type` = ParameterEditorType.DICT_EDITOR, dictId = "long_dict")
      longDict: Option[java.lang.Long],
      @ParamName("RGBDictRAW")
      @ParameterEditor(`type` = ParameterEditorType.DICT_EDITOR, dictId = "rgb")
      @ParameterEditor(`type` = ParameterEditorType.SPEL_EDITOR)
      rgbRaw: Option[String]
  ): Future[String] = {
    Future.successful(s"""RGBDict value to lowercase: ${rgb.toLowerCase}
         |LongDict value + 1: ${longDict.map(_ + 1)}
         |BooleanDict value negation: ${booleanDict.map(!_)}
         |RGBDictRAW value to lowercase: ${rgbRaw.map(_.toLowerCase)}""".stripMargin)
  }

}
