/* eslint-disable i18next/no-literal-string */
import React from "react"
import {OwnProps as PanelOwnProps} from "../../../UserRightPanel"
import {RootState} from "../../../../../reducers/index"
import {connect} from "react-redux"
import * as DialogMessages from "../../../../../common/DialogMessages"
import HttpService from "../../../../../http/HttpService"
import {events} from "../../../../../analytics/TrackingEvents"
import InlinedSvgs from "../../../../../assets/icons/InlinedSvgs"
import {toggleConfirmDialog} from "../../../../../actions/nk/ui/toggleConfirmDialog"
import {ButtonWithIcon} from "../../../ButtonWithIcon"
import {getFeatureSettings} from "../../../selectors/settings"
import {isDeployPossible, getProcessVersionId, getProcessId} from "../../../selectors/graph"

type OwnPropsPick = Pick<PanelOwnProps,
  | "isStateLoaded"
  | "processState">

type OwnProps = OwnPropsPick
type Props = OwnProps & StateProps

function MigrateButton(props: Props) {
  const {
    processId, deployPossible, featuresSettings,
    versionId, toggleConfirmDialog,
  } = props

  return (
    <ButtonWithIcon
      name={"migrate"}
      icon={InlinedSvgs.buttonMigrate}
      disabled={!deployPossible}
      onClick={() => toggleConfirmDialog(
        true,
        DialogMessages.migrate(processId, featuresSettings.remoteEnvironment.targetEnvironmentId),
        () => HttpService.migrateProcess(processId, versionId),
        "Yes",
        "No",
        {category: events.categories.rightPanel, action: events.actions.buttonClick, name: "migrate"},
      )}
    />
  )
}

const mapState = (state: RootState, props: OwnProps) => ({
  processId: getProcessId(state),
  versionId: getProcessVersionId(state),
  featuresSettings: getFeatureSettings(state),
  deployPossible: isDeployPossible(state, props),
})

const mapDispatch = {
  toggleConfirmDialog,
}
type StateProps = typeof mapDispatch & ReturnType<typeof mapState>

export default connect(mapState, mapDispatch)(MigrateButton)
