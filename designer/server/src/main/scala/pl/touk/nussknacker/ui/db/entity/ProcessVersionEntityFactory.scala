package pl.touk.nussknacker.ui.db.entity

import io.circe.generic.JsonCodec
import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.restmodel.component.NodeId
import pl.touk.nussknacker.ui.process.repository.{ComponentIdParts, ScenarioComponentsUsages}
import slick.lifted.{ForeignKeyQuery, ProvenShape, TableQuery => LTableQuery}
import slick.sql.SqlProfile.ColumnOption.NotNull

import java.sql.Timestamp

trait ProcessVersionEntityFactory extends BaseEntityFactory {

  import ScenarioComponentsUsagesJsonCodec._
  import profile.api._

  val processesTable: LTableQuery[ProcessEntityFactory#ProcessEntity]

  class ProcessVersionEntity(tag: Tag) extends BaseProcessVersionEntity(tag) {

    def json: Rep[Option[String]] = column[Option[String]]("json", O.Length(100 * 1000))

    def componentsUsages: Rep[Option[String]] = column[Option[String]]("components_usages", NotNull)

    def * : ProvenShape[ProcessVersionEntityData] = (id, processId, json, createDate, user, modelVersion, componentsUsages) <> ( {
      case (versionId: VersionId, processId: ProcessId, jsonStringOpt: Option[String], createDate: Timestamp, user: String, modelVersion: Option[Int], componentsUsagesOpt: Option[String]) =>
        ProcessVersionEntityData(versionId, processId, jsonStringOpt.map(ProcessMarshaller.fromJsonUnsafe), createDate, user, modelVersion, componentsUsagesOpt.map(CirceUtil.decodeJsonUnsafe[ScenarioComponentsUsages](_)))
    },
      (e: ProcessVersionEntityData) => ProcessVersionEntityData.unapply(e).map { t => (t._1, t._2, t._3.map(_.asJson.noSpaces), t._4, t._5, t._6, t._7.map(_.asJson.noSpaces)) }
    )

  }

  class ProcessVersionEntityNoJson(tag: Tag) extends BaseProcessVersionEntity(tag) {

    override def * : ProvenShape[ProcessVersionEntityData] = (id, processId, createDate, user, modelVersion) <> (
      (ProcessVersionEntityData.apply(_: VersionId, _: ProcessId, None, _: Timestamp, _: String, _: Option[Int], None)).tupled,
      (e: ProcessVersionEntityData) => ProcessVersionEntityData.unapply(e).map { t => (t._1, t._2, t._4, t._5, t._6) }
    )

  }

  abstract class BaseProcessVersionEntity(tag: Tag) extends Table[ProcessVersionEntityData](tag, "process_versions") {

    def id: Rep[VersionId] = column[VersionId]("id", NotNull)

    def createDate: Rep[Timestamp] = column[Timestamp]("create_date", NotNull)

    def user: Rep[String] = column[String]("user", NotNull)

    def processId: Rep[ProcessId] = column[ProcessId]("process_id", NotNull)

    def modelVersion: Rep[Option[Int]] = column[Option[Int]]("model_version", NotNull)

    def pk = primaryKey("pk_process_version", (processId, id))

    private def process: ForeignKeyQuery[ProcessEntityFactory#ProcessEntity, ProcessEntityData] = foreignKey("process-version-process-fk", processId, processesTable)(
      _.id,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.Cascade
    )
  }

  val processVersionsTable: TableQuery[ProcessVersionEntityFactory#ProcessVersionEntity] =
    LTableQuery(new ProcessVersionEntity(_))

  val processVersionsTableNoJson: TableQuery[ProcessVersionEntityFactory#ProcessVersionEntityNoJson] =
    LTableQuery(new ProcessVersionEntityNoJson(_))
}

case class ProcessVersionEntityData(id: VersionId,
                                    processId: ProcessId,
                                    json: Option[CanonicalProcess],
                                    createDate: Timestamp,
                                    user: String,
                                    modelVersion: Option[Int],
                                    componentsUsages: Option[ScenarioComponentsUsages],
                                   )

@JsonCodec
private[entity] case class ComponentUsages(componentName: Option[String], componentType: ComponentType, nodeIds: List[NodeId])

private object ScenarioComponentsUsagesJsonCodec {

  implicit val decoder: Decoder[ScenarioComponentsUsages] = implicitly[Decoder[List[ComponentUsages]]].map { componentUsagesList =>
    val componentUsagesMap = componentUsagesList.map { componentUsages =>
      val componentIdParts = ComponentIdParts(componentUsages.componentName, componentUsages.componentType)
      componentIdParts -> componentUsages.nodeIds
    }.toMap
    ScenarioComponentsUsages(componentUsagesMap)
  }

  implicit val encoder: Encoder[ScenarioComponentsUsages] = implicitly[Encoder[List[ComponentUsages]]].contramap[ScenarioComponentsUsages](_.value.toList.map {
    case (ComponentIdParts(componentName, componentType), nodeIds) => ComponentUsages(componentName, componentType, nodeIds)
  })

}
