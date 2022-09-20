package pl.touk.nussknacker.ui.api


import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Decoder
import io.circe.generic.extras.semiauto.deriveConfiguredDecoder
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, OptionValues}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.additionalInfo.{AdditionalInfo, MarkdownAdditionalInfo}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.InvalidPropertyFixedValue
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.api.{ProcessAdditionalFields, StreamMetaData}
import pl.touk.nussknacker.restmodel.definition.UIParameter
import pl.touk.nussknacker.restmodel.displayedgraph.ProcessProperties
import pl.touk.nussknacker.restmodel.validation.PrettyValidationErrors
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.TestFactory.withPermissions
import pl.touk.nussknacker.ui.api.helpers.{EspItTest, ProcessTestData}
import pl.touk.nussknacker.engine.api.CirceUtil._

class PropertiesResourcesSpec extends AnyFunSuite with ScalatestRouteTest with FailFastCirceSupport
  with Matchers with PatientScalaFutures with OptionValues with BeforeAndAfterEach with BeforeAndAfterAll with EspItTest {

  private val propertiesRoute = new PropertiesResources(fetchingProcessRepository, subprocessRepository, typeToConfig.mapValues(_.modelData), typeToConfig.mapValues(_.additionalPropertiesConfig))

  private implicit val typingResultDecoder: Decoder[TypingResult]
    = PropertiesResources.prepareTypingResultDecoder(typeToConfig.all.head._2.modelData)
  private implicit val uiParameterDecoder: Decoder[UIParameter] = deriveConfiguredDecoder[UIParameter]
  private implicit val responseDecoder: Decoder[PropertiesValidationResult] = deriveConfiguredDecoder[PropertiesValidationResult]
  private val processProperties = ProcessProperties(StreamMetaData(), additionalFields = Some(ProcessAdditionalFields(None, Map("numberOfThreads" -> "2", "environment" -> "test"))))

  //see SampleNodeAdditionalInfoProvider
  test("it should return additional info for process") {
    val testProcess = ProcessTestData.sampleDisplayableProcess
    saveProcess(testProcess) {
      import pl.touk.nussknacker.restmodel.displayedgraph.ProcessProperties.encodeProcessProperties

      Post(s"/properties/${testProcess.id}/additionalInfo", toEntity(processProperties)) ~> withPermissions(propertiesRoute, testPermissionRead) ~> check {
        responseAs[AdditionalInfo] should matchPattern {
          case MarkdownAdditionalInfo(content) if content.equals("2 threads will be used on environment 'test'")=>
        }
      }
    }
  }

  test("validate properties") {

    val testProcess = ProcessTestData.sampleDisplayableProcess
    saveProcess(testProcess) {
      val request = PropertiesValidationRequest(ProcessProperties(StreamMetaData(), additionalFields = Some(ProcessAdditionalFields(None, Map("numberOfThreads" -> "a", "environment" -> "test")))))

      Post(s"/properties/${testProcess.id}/validation", toEntity(request)) ~> withPermissions(propertiesRoute, testPermissionRead) ~> check {
        responseAs[PropertiesValidationResult] shouldBe PropertiesValidationResult(
          parameters = None,
          expressionType = None,
          validationErrors = List(PrettyValidationErrors.formatErrorMessage(InvalidPropertyFixedValue("numberOfThreads", Some("Number of threads"), "a", List("1","2"), ""))),
          validationPerformed = true)
      }
    }
  }
}
