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
import {ButtonWithIcon} from "../../../ButtonWithIcon"
import {isRunning, getProcessId} from "../../../selectors/graph"
import {useTranslation} from "react-i18next"

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
  const {t} = useTranslation()

  return (
    <ButtonWithIcon
      name={t("panels.actions.process-archive.button", "archive")}
      icon={"archive.svg"}
      disabled={isRunning}
      onClick={() => !isRunning && toggleConfirmDialog(
        true,
        DialogMessages.archiveProcess(processId),
        () => HttpService.archiveProcess(processId).then(() => history.push(Archive.path)),
        t("panels.actions.process-archive.yes", "Yes"),
        t("panels.actions.process-archive.no", "No"),
        // eslint-disable-next-line i18next/no-literal-string
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
