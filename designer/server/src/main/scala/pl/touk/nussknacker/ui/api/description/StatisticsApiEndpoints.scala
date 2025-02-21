package pl.touk.nussknacker.ui.api.description

import derevo.circe.{decoder, encoder}
import derevo.derive
import enumeratum.EnumEntry.UpperSnakecase
import enumeratum.{CirceEnum, Enum, EnumEntry}
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions.SecuredEndpoint
import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.ui.api.TapirCodecs
import sttp.model.StatusCode.{InternalServerError, NoContent, Ok}
import sttp.tapir.EndpointIO.Example
import sttp.tapir._
import sttp.tapir.codec.enumeratum._
import sttp.tapir.derevo.schema
import sttp.tapir.json.circe.jsonBody

import java.net.{URI, URL}

class StatisticsApiEndpoints(auth: EndpointInput[AuthCredentials]) extends BaseEndpointDefinitions {

  import StatisticsApiEndpoints.Dtos._

  lazy val statisticUsageEndpoint: SecuredEndpoint[Unit, StatisticError, StatisticUrlResponseDto, Any] =
    baseNuApiEndpoint
      .summary("Statistics URL service")
      .tag("Statistics")
      .get
      .in("statistic" / "usage")
      .out(
        statusCode(Ok).and(
          jsonBody[StatisticUrlResponseDto]
            .example(
              Example.of(
                summary = Some("List of statistics URLs"),
                value = StatisticUrlResponseDto(
                  List(
                    URI
                      .create(
                        "https://stats.nussknacker.io/?a_n=1&a_t=0&a_v=0&c=3&c_n=82&c_t=0&c_v=0&f_m=0&f_v=0&" +
                          "fingerprint=development&n_m=2&n_ma=0&n_mi=2&n_v=1&s_a=0&s_dm_c=1&s_dm_e=1&s_dm_f=2&" +
                          "s_dm_l=0&s_f=1&s_pm_b=0&s_pm_rr=1&s_pm_s=3&s_s=3&source=sources&u_ma=0&u_mi=0&u_v=0&v_m=2&" +
                          "v_ma=1&v_mi=3&v_v=2&version=1.15.0-SNAPSHOT"
                      )
                      .toURL
                  )
                )
              )
            )
        )
      )
      .errorOut(
        oneOf[StatisticError](
          oneOfVariantFromMatchType(
            InternalServerError,
            plainBody[StatisticError]
              .example(
                Example.of(
                  summary = Some("Statistics generation failed."),
                  value = CannotGenerateStatisticError
                )
              )
          )
        )
      )
      .withSecurity(auth)

  lazy val registerStatisticsEndpoint: SecuredEndpoint[RegisterStatisticsRequestDto, Unit, Unit, Any] =
    baseNuApiEndpoint
      .summary("Register statistics service")
      .tag("Statistics")
      .post
      .in("statistic" and jsonBody[RegisterStatisticsRequestDto])
      .out(statusCode(NoContent))
      .withSecurity(auth)

}

object StatisticsApiEndpoints {

  object Dtos {
    import TapirCodecs.URLCodec._

    @derive(encoder, decoder, schema)
    final case class StatisticUrlResponseDto private (urls: List[URL])

    @derive(encoder, decoder, schema)
    final case class RegisterStatisticsRequestDto private (statistics: List[StatisticDto])

    @derive(encoder, decoder, schema)
    final case class StatisticDto private (name: StatisticName)

    sealed trait StatisticName extends EnumEntry with UpperSnakecase {
      val shortName: String
    }

    object StatisticName extends Enum[StatisticName] with CirceEnum[StatisticName] {

      case object SearchScenariosByName                extends StatisticName { override val shortName = "f_ssbn"  }
      case object FilterScenariosByStatus              extends StatisticName { override val shortName = "f_fsbs"  }
      case object FilterScenariosByProcessingMode      extends StatisticName { override val shortName = "f_fsbpm" }
      case object FilterScenariosByCategory            extends StatisticName { override val shortName = "f_fsbc"  }
      case object FilterScenariosByAuthor              extends StatisticName { override val shortName = "f_fsba"  }
      case object FilterScenariosByOther               extends StatisticName { override val shortName = "f_fsbo"  }
      case object SortScenariosBySortOption            extends StatisticName { override val shortName = "f_ssbso" }
      case object SearchComponentsByName               extends StatisticName { override val shortName = "f_scbn"  }
      case object FilterComponentsByGroup              extends StatisticName { override val shortName = "f_fcbg"  }
      case object FilterComponentsByProcessingMode     extends StatisticName { override val shortName = "f_fcbpm" }
      case object FilterComponentsByCategory           extends StatisticName { override val shortName = "f_fcbc"  }
      case object FilterComponentsByMultipleCategories extends StatisticName { override val shortName = "f_fcbmc" }
      case object FilterComponentsByUsages             extends StatisticName { override val shortName = "f_fcbu"  }
      case object ClickComponentUsages                 extends StatisticName { override val shortName = "f_ccu"   }
      case object SearchComponentUsagesByName          extends StatisticName { override val shortName = "f_scubn" }
      case object FilterComponentUsagesByStatus        extends StatisticName { override val shortName = "f_fcubs" }
      case object FilterComponentUsagesByCategory      extends StatisticName { override val shortName = "f_fcubc" }
      case object FilterComponentUsagesByAuthor        extends StatisticName { override val shortName = "f_fcuba" }
      case object FilterComponentUsagesByOther         extends StatisticName { override val shortName = "f_fcubo" }
      case object ClickScenarioFromComponentUsages     extends StatisticName { override val shortName = "f_csfcu" }
      case object ClickGlobalMetricsTab                extends StatisticName { override val shortName = "f_cgmt"  }
      case object ClickActionDeploy                    extends StatisticName { override val shortName = "f_cad"   }
      case object ClickActionMetrics                   extends StatisticName { override val shortName = "f_cam"   }
      case object ClickViewZoomIn                      extends StatisticName { override val shortName = "f_cvzi"  }
      case object ClickViewZoomOut                     extends StatisticName { override val shortName = "f_cvzo"  }
      case object ClickViewReset                       extends StatisticName { override val shortName = "f_cvr"   }
      case object ClickEditUndo                        extends StatisticName { override val shortName = "f_ceu"   }
      case object ClickEditRedo                        extends StatisticName { override val shortName = "f_cer"   }
      case object ClickEditCopy                        extends StatisticName { override val shortName = "f_cec"   }
      case object ClickEditPaste                       extends StatisticName { override val shortName = "f_cep"   }
      case object ClickEditDelete                      extends StatisticName { override val shortName = "f_ced"   }
      case object ClickEditLayout                      extends StatisticName { override val shortName = "f_cel"   }
      case object ClickScenarioProperties              extends StatisticName { override val shortName = "f_csp"   }
      case object ClickScenarioCompare                 extends StatisticName { override val shortName = "f_csco"  }
      case object ClickScenarioMigrate                 extends StatisticName { override val shortName = "f_csm"   }
      case object ClickScenarioImport                  extends StatisticName { override val shortName = "f_csi"   }
      case object ClickScenarioExport                  extends StatisticName { override val shortName = "f_csj"   }
      case object ClickScenarioPdf                     extends StatisticName { override val shortName = "f_cspd"  }
      case object ClickScenarioArchive                 extends StatisticName { override val shortName = "f_csa"   }
      case object ClickTestGenerated                   extends StatisticName { override val shortName = "f_ctg"   }
      case object ClickTestAdhoc                       extends StatisticName { override val shortName = "f_cta"   }
      case object ClickTestFromFile                    extends StatisticName { override val shortName = "f_ctff"  }
      case object ClickTestGenerateFile                extends StatisticName { override val shortName = "f_ctgt"  }
      case object ClickTestHide                        extends StatisticName { override val shortName = "f_cth"   }
      case object ClickMoreScenarioDetails             extends StatisticName { override val shortName = "f_cmsd"  }
      case object ClickExpandPanel                     extends StatisticName { override val shortName = "f_cexp"  }
      case object ClickCollapsePanel                   extends StatisticName { override val shortName = "f_ccp"   }
      case object ClickScenarioActivitiesAddAttachment extends StatisticName { override val shortName = "f_csaaa" }
      case object ClickScenarioActivitiesAddComment    extends StatisticName { override val shortName = "f_csaac" }

      case object ClickScenarioActivitiesAddCommentToActivity extends StatisticName {
        override val shortName = "f_csaacta"
      }

      case object ClickScenarioActivitiesCompare            extends StatisticName { override val shortName = "f_csac"  }
      case object ClickScenarioActivitiesDownloadAttachment extends StatisticName { override val shortName = "f_csada" }
      case object ClickScenarioActivitiesDeleteAttachment extends StatisticName { override val shortName = "f_csadat" }
      case object ClickScenarioActivitiesDeleteComment    extends StatisticName { override val shortName = "f_csadc"  }
      case object ClickScenarioActivitiesEditComment      extends StatisticName { override val shortName = "f_csaec"  }
      case object ClickScenarioActivitiesOpenVersion      extends StatisticName { override val shortName = "f_csaov"  }
      case object MoveToolbarPanel                        extends StatisticName { override val shortName = "f_mtp"    }
      case object SearchNodesInScenario                   extends StatisticName { override val shortName = "f_snis"   }
      case object SearchComponentsInScenario              extends StatisticName { override val shortName = "f_scis"   }
      case object ClickOlderVersion                       extends StatisticName { override val shortName = "f_cov"    }
      case object ClickNewerVersion                       extends StatisticName { override val shortName = "f_cnv"    }
      case object ClickNodeDocumentation                  extends StatisticName { override val shortName = "f_cnd"    }
      case object ClickComponentsTab                      extends StatisticName { override val shortName = "f_cct"    }
      case object ClickScenarioSave                       extends StatisticName { override val shortName = "f_css"    }
      case object ClickTestCounts                         extends StatisticName { override val shortName = "f_ctc"    }
      case object ClickScenarioCancel                     extends StatisticName { override val shortName = "f_csc"    }
      case object ClickScenarioArchiveToggle              extends StatisticName { override val shortName = "f_csat"   }
      case object ClickScenarioUnarchive                  extends StatisticName { override val shortName = "f_csu"    }
      case object ClickScenarioCustomAction               extends StatisticName { override val shortName = "f_csca"   }
      case object ClickScenarioCustomLink                 extends StatisticName { override val shortName = "f_cscl"   }
      case object DoubleClickRangeSelectNodes             extends StatisticName { override val shortName = "f_dcrsn"  }
      case object KeyboardAndClickRangeSelectNodes        extends StatisticName { override val shortName = "f_kacrsn" }
      case object KeyboardCopyNode                        extends StatisticName { override val shortName = "f_kcon"   }
      case object KeyboardPasteNode                       extends StatisticName { override val shortName = "f_kpn"    }
      case object KeyboardCutNode                         extends StatisticName { override val shortName = "f_kcn"    }
      case object KeyboardSelectAllNodes                  extends StatisticName { override val shortName = "f_ksan"   }
      case object KeyboardRedoScenarioChanges             extends StatisticName { override val shortName = "f_krsc"   }
      case object KeyboardUndoScenarioChanges             extends StatisticName { override val shortName = "f_kusc"   }
      case object KeyboardDeleteNodes                     extends StatisticName { override val shortName = "f_kdn"    }
      case object KeyboardDeselectAllNodes                extends StatisticName { override val shortName = "f_kdan"   }
      case object KeyboardFocusSearchNodeField            extends StatisticName { override val shortName = "f_kfsnf"  }

      override def values = findValues
    }

    sealed trait StatisticError
    final case object CannotGenerateStatisticError extends StatisticError

    object StatisticError {

      implicit val errorCodec: Codec[String, StatisticError, CodecFormat.TextPlain] =
        BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[StatisticError](_ => "Statistics generation failed.")

    }

  }

}
