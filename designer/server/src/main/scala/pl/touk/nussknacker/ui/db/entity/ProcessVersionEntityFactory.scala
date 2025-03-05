package pl.touk.nussknacker.ui.db.entity

import io.circe.{Decoder, Encoder}
import io.circe.generic.JsonCodec
import io.circe.syntax._
import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.engine.api.component.ComponentId
import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.restmodel.component.{NodeId, ScenarioComponentsUsages}
import slick.lifted.{ForeignKeyQuery, ProvenShape, TableQuery => LTableQuery}
import slick.sql.SqlProfile.ColumnOption.NotNull

import java.sql.Timestamp

trait ProcessVersionEntityFactory extends BaseEntityFactory {

  import profile.api._

  import ScenarioComponentsUsagesJsonCodec._

  val processesTable: LTableQuery[ProcessEntityFactory#ProcessEntity]

  class ProcessVersionEntity(tag: Tag) extends BaseProcessVersionEntity(tag) {

    def json: Rep[String] = column[String]("json", NotNull)

    def componentsUsages: Rep[String] = column[String]("components_usages", NotNull)

    def * : ProvenShape[ProcessVersionEntityData] =
      (id, processId, json, createDate, user, modelVersion, componentsUsages) <> (
        {
          case (
                versionId: VersionId,
                processId: ProcessId,
                jsonString: String,
                createDate: Timestamp,
                user: String,
                modelVersion: Option[Int],
                componentsUsages: String
              ) =>
            ProcessVersionEntityData(
              versionId,
              processId,
              Some(ProcessMarshaller.fromJsonUnsafe(jsonString)),
              createDate,
              user,
              modelVersion,
              Some(CirceUtil.decodeJsonUnsafe[ScenarioComponentsUsages](componentsUsages))
            )
        },
        (e: ProcessVersionEntityData) =>
          ProcessVersionEntityData.unapply(e).map { t =>
            (t._1, t._2, t._3.get.asJson.noSpaces, t._4, t._5, t._6, t._7.get.asJson.noSpaces)
          }
      )

  }

  class ProcessVersionEntityWithScenarioJson(tag: Tag) extends BaseProcessVersionEntity(tag) {

    def json: Rep[String] = column[String]("json", NotNull)

    def * : ProvenShape[ProcessVersionEntityData] = (id, processId, json, createDate, user, modelVersion) <> (
      {
        case (
              versionId: VersionId,
              processId: ProcessId,
              jsonString: String,
              createDate: Timestamp,
              user: String,
              modelVersion: Option[Int]
            ) =>
          ProcessVersionEntityData(
            versionId,
            processId,
            Some(ProcessMarshaller.fromJsonUnsafe(jsonString)),
            createDate,
            user,
            modelVersion,
            None
          )
      },
      (e: ProcessVersionEntityData) =>
        ProcessVersionEntityData.unapply(e).map { t => (t._1, t._2, t._3.get.asJson.noSpaces, t._4, t._5, t._6) }
    )

  }

  class ProcessVersionEntityWithComponentsUsages(tag: Tag) extends BaseProcessVersionEntity(tag) {

    def componentsUsages: Rep[String] = column[String]("components_usages", NotNull)

    def * : ProvenShape[ProcessVersionEntityData] =
      (id, processId, createDate, user, modelVersion, componentsUsages) <> (
        {
          case (
                versionId: VersionId,
                processId: ProcessId,
                createDate: Timestamp,
                user: String,
                modelVersion: Option[Int],
                componentsUsages: String
              ) =>
            ProcessVersionEntityData(
              versionId,
              processId,
              None,
              createDate,
              user,
              modelVersion,
              Some(CirceUtil.decodeJsonUnsafe[ScenarioComponentsUsages](componentsUsages))
            )
        },
        (e: ProcessVersionEntityData) =>
          ProcessVersionEntityData.unapply(e).map { t => (t._1, t._2, t._4, t._5, t._6, t._7.get.asJson.noSpaces) }
      )

  }

  class ProcessVersionEntityWithUnit(tag: Tag) extends BaseProcessVersionEntity(tag) {

    override def * : ProvenShape[ProcessVersionEntityData] = (id, processId, createDate, user, modelVersion) <> (
      (ProcessVersionEntityData
        .apply(_: VersionId, _: ProcessId, None, _: Timestamp, _: String, _: Option[Int], None))
        .tupled,
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

    private def process: ForeignKeyQuery[ProcessEntityFactory#ProcessEntity, ProcessEntityData] =
      foreignKey("process-version-process-fk", processId, processesTable)(
        _.id,
        onUpdate = ForeignKeyAction.Cascade,
        onDelete = ForeignKeyAction.Cascade
      )

  }

  val processVersionsTable: TableQuery[ProcessVersionEntityFactory#ProcessVersionEntity] =
    LTableQuery(new ProcessVersionEntity(_))

  val processVersionsTableWithScenarioJson
      : TableQuery[ProcessVersionEntityFactory#ProcessVersionEntityWithScenarioJson] =
    LTableQuery(new ProcessVersionEntityWithScenarioJson(_))

  val processVersionsTableWithComponentsUsages
      : TableQuery[ProcessVersionEntityFactory#ProcessVersionEntityWithComponentsUsages] =
    LTableQuery(new ProcessVersionEntityWithComponentsUsages(_))

  val processVersionsTableWithUnit: TableQuery[ProcessVersionEntityFactory#ProcessVersionEntityWithUnit] =
    LTableQuery(new ProcessVersionEntityWithUnit(_))

}

final case class ProcessVersionEntityData(
    id: VersionId,
    processId: ProcessId,
    json: Option[CanonicalProcess],
    createDate: Timestamp,
    user: String,
    modelVersion: Option[Int],
    componentsUsages: Option[ScenarioComponentsUsages],
) {
  def jsonUnsafe: CanonicalProcess =
    json.getOrElse(throw new IllegalStateException("Accessing scenario graph json which is not fetched"))
}

// TODO: Remove this codec and just serialize Map[ComponentId, List[NodeId]]
@JsonCodec
private[entity] final case class ComponentUsages(
    componentName: String,
    componentType: ComponentType,
    nodeIds: List[NodeId]
)

object ScenarioComponentsUsagesJsonCodec {

  implicit val decoder: Decoder[ScenarioComponentsUsages] = implicitly[Decoder[List[ComponentUsages]]].map {
    componentUsagesList =>
      val componentUsagesMap = componentUsagesList.map { componentUsages =>
        val componentId = ComponentId(componentUsages.componentType, componentUsages.componentName)
        componentId -> componentUsages.nodeIds
      }.toMap
      ScenarioComponentsUsages(componentUsagesMap)
  }

  implicit val encoder: Encoder[ScenarioComponentsUsages] =
    implicitly[Encoder[List[ComponentUsages]]].contramap[ScenarioComponentsUsages](_.value.toList.map {
      case (ComponentId(componentType, componentName), nodeIds) =>
        ComponentUsages(componentName, componentType, nodeIds)
    })

}
