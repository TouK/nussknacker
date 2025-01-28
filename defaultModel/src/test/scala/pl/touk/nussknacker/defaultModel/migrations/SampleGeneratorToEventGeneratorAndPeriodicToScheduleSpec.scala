package pl.touk.nussknacker.defaultModel.migrations

import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.defaultmodel.migrations.SampleGeneratorToEventGeneratorAndPeriodToScheduleParameter
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.graph.node.Source
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.spel.SpelExtension._

class SampleGeneratorToEventGeneratorAndPeriodicToScheduleSpec extends AnyFreeSpecLike with Matchers {

  "SampleGeneratorToEventGeneratorAndPeriodicToScheduleSpec should be applied" in {
    val metaData = MetaData("test", StreamMetaData(Some(1)))
    val beforeMigration = Source(
      id = "sample-generator",
      ref = SourceRef(
        typ = "sample-generator",
        parameters = List(
          Parameter(ParameterName("period"), "T(java.time.Duration).parse('PT1M')".spel),
          Parameter(ParameterName("count"), "1".spel),
          Parameter(ParameterName("value"), "1".spel),
        )
      )
    )
    val expectedAfterMigration = Source(
      id = "sample-generator",
      ref = SourceRef(
        typ = "event-generator",
        parameters = List(
          Parameter(ParameterName("schedule"), "T(java.time.Duration).parse('PT1M')".spel),
          Parameter(ParameterName("count"), "1".spel),
          Parameter(ParameterName("value"), "1".spel),
        )
      )
    )

    val migrated = SampleGeneratorToEventGeneratorAndPeriodToScheduleParameter.migrateNode(metaData)(beforeMigration)

    migrated shouldBe expectedAfterMigration
  }

}
