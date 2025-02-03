package pl.touk.nussknacker.defaultmodel

import org.apache.flink.api.common.ExecutionConfig
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.flink.api.{ConfigGlobalParameters, NamespaceMetricsTags, NkGlobalParameters}

class NkGlobalParametersEncoderTest extends AnyFunSuite with Matchers {

  test("global parameters set and read from context are equal") {
    val globalParamsWithAllOptionalValues = NkGlobalParameters(
      buildInfo = "aBuildInfo",
      deploymentId = "1",
      processVersion = ProcessVersion(
        VersionId.initialVersionId,
        ProcessName("aProcessName"),
        ProcessId("1"),
        List("aLabel"),
        "aUser",
        Some(1)
      ),
      configParameters = Some(ConfigGlobalParameters(Some(true), Some(true), Some(true))),
      namespaceParameters = Some(NamespaceMetricsTags(Map("metricTag" -> "metricVal"))),
      additionalInformation = Map("additionalInfoKey" -> "additionalInfoVal")
    )

    val globalParamsWithNoOptionalValues = NkGlobalParameters(
      buildInfo = "aBuildInfo",
      deploymentId = "1",
      processVersion = ProcessVersion(
        VersionId.initialVersionId,
        ProcessName("aProcessName"),
        ProcessId("1"),
        List("aLabel"),
        "aUser",
        None
      ),
      configParameters = None,
      namespaceParameters = None,
      additionalInformation = Map.empty
    )

    List(globalParamsWithAllOptionalValues, globalParamsWithNoOptionalValues).foreach { params =>
      val decodedParams = NkGlobalParameters.fromMap(params.toMap).get

      decodedParams.buildInfo shouldBe params.buildInfo
      decodedParams.deploymentId shouldBe params.deploymentId
      decodedParams.processVersion shouldBe params.processVersion
      decodedParams.configParameters shouldBe params.configParameters
      decodedParams.namespaceParameters shouldBe params.namespaceParameters
      decodedParams.additionalInformation shouldBe params.additionalInformation
    }
  }

  test("returns None when context doesnt have required parameters") {
    NkGlobalParameters.fromMap(new ExecutionConfig.GlobalJobParameters().toMap) shouldBe None
  }

}
