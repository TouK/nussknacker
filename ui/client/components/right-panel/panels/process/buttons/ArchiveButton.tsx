/* eslint-disable i18next/no-literal-string */
import React from "react"
import {OwnProps as PanelOwnProps} from "../../../UserRightPanel"
import {RootState} from "../../../../../reducers/index"
import {connect} from "react-redux"
import * as DialogMessages from "../../../../../common/DialogMessages"
import HttpService from "../../../../../http/HttpService"
import history from "../../../../../history"
import {Archive} from "../../../../../containers/Archive"
import {events} from "../../../../../analytics/TrackingEvents"
import {toggleConfirmDialog} from "../../../../../actions/nk/ui/toggleConfirmDialog"
import {bindActionCreators} from "redux"
import {getProcessId, isRunning} from "../../../selectors"
import {ButtonWithIcon} from "../../../ButtonWithIcon"

type OwnPropsPick = Pick<PanelOwnProps,
  | "isStateLoaded"
  | "processState">

type OwnProps = OwnPropsPick
type Props = OwnProps & StateProps

function ArchiveButton(props: Props) {
  const {
    processId, isRunning,
    toggleConfirmDialog,
  } = props

  return (
    <ButtonWithIcon
      name={"archive"}
      icon={"archive.svg"}
      disabled={isRunning}
      onClick={() => !isRunning && toggleConfirmDialog(
        true,
        DialogMessages.archiveProcess(processId),
        () => HttpService.archiveProcess(processId).then(() => history.push(Archive.path)),
        "Yes",
        "No",
        {category: events.categories.rightPanel, action: events.actions.buttonClick, name: "archive"},
      )}
    />
  )
}

const mapState = (state: RootState, props: OwnProps) => {
  return {
    processId: getProcessId(state),
    isRunning: isRunning(state, props),
  }
}

const mapDispatch = (dispatch) => bindActionCreators({
  toggleConfirmDialog,
}, dispatch)

type StateProps = ReturnType<typeof mapDispatch> & ReturnType<typeof mapState>

export default connect(mapState, mapDispatch)(ArchiveButton)
