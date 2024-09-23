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
      processVersion = ProcessVersion(
        VersionId.initialVersionId,
        ProcessName("aProcessName"),
        ProcessId("1"),
        "aUser",
        Some(1)
      ),
      configParameters = Some(ConfigGlobalParameters(Some(true), Some(true), Some(true))),
      namespaceParameters = Some(NamespaceMetricsTags(Map("metricTag" -> "metricVal"))),
      additionalInformation = Map("additionalInfoKey" -> "additionalInfoVal")
    )

    val globalParamsWithNoOptionalValues = NkGlobalParameters(
      buildInfo = "aBuildInfo",
      processVersion = ProcessVersion(
        VersionId.initialVersionId,
        ProcessName("aProcessName"),
        ProcessId("1"),
        "aUser",
        None
      ),
      configParameters = None,
      namespaceParameters = None,
      additionalInformation = Map.empty
    )

    List(globalParamsWithAllOptionalValues, globalParamsWithNoOptionalValues).foreach { params =>
      val ec = new ExecutionConfig()
      ec.setGlobalJobParameters(params)
      val globalParamsFromEc = NkGlobalParameters.readFromContext(ec).get

      params.buildInfo shouldBe globalParamsFromEc.buildInfo
      params.processVersion shouldBe globalParamsFromEc.processVersion
      params.configParameters shouldBe globalParamsFromEc.configParameters
      params.namespaceParameters shouldBe globalParamsFromEc.namespaceParameters
      params.additionalInformation shouldBe globalParamsFromEc.additionalInformation
    }
  }

  test("returns None when context doesnt have required parameters") {
    NkGlobalParameters.readFromContext(new ExecutionConfig()) shouldBe None
  }

}
