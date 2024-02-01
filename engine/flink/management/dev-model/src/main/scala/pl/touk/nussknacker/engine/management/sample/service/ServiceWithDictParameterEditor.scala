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
        dictId = "RGB"
      )
      rgb: String,
      @ParamName("TestDict")
      @SimpleEditor(
        `type` = SimpleEditorType.DICT_EDITOR,
        dictId = "DICT"
      )
      testDictValue: String,
      @ParamName("BusinessConfig")
      @SimpleEditor(
        `type` = SimpleEditorType.DICT_EDITOR,
        dictId = "BusinessConfig"
      )
      businessConfig: String
  ): Future[String] = {
    Future.successful(s"""RGBDict: $rgb
         |TestDict: $testDictValue
         |BusinessConfig: $businessConfig""".stripMargin)
  }

}
