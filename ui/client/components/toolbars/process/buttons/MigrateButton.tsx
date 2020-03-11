import React from "react"
import {RootState} from "../../../../reducers/index"
import {connect} from "react-redux"
import * as DialogMessages from "../../../../common/DialogMessages"
import HttpService from "../../../../http/HttpService"
import {events} from "../../../../analytics/TrackingEvents"
import {toggleConfirmDialog} from "../../../../actions/nk/ui/toggleConfirmDialog"
import ToolbarButton from "../../../toolbarComponents/ToolbarButton"
import {getFeatureSettings} from "../../../../reducers/selectors/settings"
import {isDeployPossible, getProcessVersionId, getProcessId} from "../../../../reducers/selectors/graph"
import {useTranslation} from "react-i18next"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/migrate.svg"

function MigrateButton(props: StateProps) {
  const {
    processId, deployPossible, featuresSettings,
    versionId, toggleConfirmDialog,
  } = props
  const {t} = useTranslation()

  return (
    <ToolbarButton
      name={t("panels.actions.process-migrate.button", "migrate")}
      icon={<Icon/>}
      disabled={!deployPossible}
      onClick={() => toggleConfirmDialog(
        true,
        DialogMessages.migrate(processId, featuresSettings.remoteEnvironment.targetEnvironmentId),
        () => HttpService.migrateProcess(processId, versionId),
        t("panels.actions.process-migrate.yes", "Yes"),
        t("panels.actions.process-migrate.no", "No"),
        {
          category: events.categories.rightPanel,
          action: events.actions.buttonClick,
          name: "migrate", // eslint-disable-line i18next/no-literal-string
        },
      )}
    />
  )
}

const mapState = (state: RootState) => ({
  processId: getProcessId(state),
  versionId: getProcessVersionId(state),
  featuresSettings: getFeatureSettings(state),
  deployPossible: isDeployPossible(state),
})

const mapDispatch = {
  toggleConfirmDialog,
}
type StateProps = typeof mapDispatch & ReturnType<typeof mapState>

export default connect(mapState, mapDispatch)(MigrateButton)
