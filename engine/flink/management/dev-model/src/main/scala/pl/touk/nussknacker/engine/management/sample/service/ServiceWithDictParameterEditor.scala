package pl.touk.nussknacker.engine.management.sample.service

import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName, Service}
import pl.touk.nussknacker.engine.api.editor.{SimpleEditor, SimpleEditorType, SpelEditor}
import pl.touk.nussknacker.engine.util.service.TimeMeasuringService

import scala.concurrent.Future

class ServiceWithDictParameterEditor extends Service with Serializable with TimeMeasuringService {

  override protected def serviceName: String = "serviceWithDictParameterEditor"

  @MethodToInvoke
  def invoke(
      @ParamName("RGBDict")
      @SimpleEditor(`type` = SimpleEditorType.DICT_EDITOR, dictId = "rgb")
      rgb: String,
      @ParamName("BooleanDict")
      @SimpleEditor(`type` = SimpleEditorType.DICT_EDITOR, dictId = "boolean_dict")
      @SpelEditor
      booleanDict: Option[java.lang.Boolean],
      @ParamName("LongDict")
      @SimpleEditor(`type` = SimpleEditorType.DICT_EDITOR, dictId = "long_dict")
      longDict: Option[java.lang.Long],
      @ParamName("RGBDictRAW")
      @SimpleEditor(`type` = SimpleEditorType.DICT_EDITOR, dictId = "rgb")
      @SpelEditor
      rgbRaw: Option[String]
  ): Future[String] = {
    Future.successful(s"""RGBDict value to lowercase: ${rgb.toLowerCase}
         |LongDict value + 1: ${longDict.map(_ + 1)}
         |BooleanDict value negation: ${booleanDict.map(!_)}
         |RGBDictRAW value to lowercase: ${rgbRaw.map(_.toLowerCase)}""".stripMargin)
  }

}
