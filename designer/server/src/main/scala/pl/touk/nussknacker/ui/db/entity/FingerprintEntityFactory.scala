package pl.touk.nussknacker.ui.db.entity

import slick.lifted.{ProvenShape, TableQuery => LTableQuery}

trait FingerprintEntityFactory extends BaseEntityFactory {
  import profile.api._

  class FingerprintEntity(tag: Tag) extends Table[FingerprintEntityData](tag, "fingerprints") {
    def value: Rep[String] = column[String]("value", O.PrimaryKey)

    def * : ProvenShape[FingerprintEntityData] = value <> (FingerprintEntityData.apply, FingerprintEntityData.unapply)
  }

  val fingerprintsTable: LTableQuery[FingerprintEntityFactory#FingerprintEntity] = LTableQuery(new FingerprintEntity(_))
}

final case class FingerprintEntityData(value: String)
