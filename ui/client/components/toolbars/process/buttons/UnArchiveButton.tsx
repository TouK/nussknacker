import React from "react"
import {RootState} from "../../../../reducers/index"
import {connect} from "react-redux"
import * as DialogMessages from "../../../../common/DialogMessages"
import HttpService from "../../../../http/HttpService"
import history from "../../../../history"
import {events} from "../../../../analytics/TrackingEvents"
import {toggleConfirmDialog} from "../../../../actions/nk/ui/toggleConfirmDialog"
import {bindActionCreators} from "redux"
import ToolbarButton from "../../../toolbarComponents/ToolbarButton"
import {isArchived, getProcessId, isSubprocess} from "../../../../reducers/selectors/graph"
import {useTranslation} from "react-i18next"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/unarchive.svg"
import {ProcessesTabData} from "../../../../containers/Processes"
import {SubProcessesTabData} from "../../../../containers/SubProcesses"

function UnArchiveButton(props: StateProps) {
  const {
    processId, isArchived,
    toggleConfirmDialog,
  } = props
  const redirectPath = isSubprocess ? ProcessesTabData.path : SubProcessesTabData.path
  const {t} = useTranslation()

  return (
    <ToolbarButton
      name={t("panels.actions.process-unarchive.button", "unarchive")}
      icon={<Icon/>}
      disabled={!isArchived}
      onClick={() => isArchived && toggleConfirmDialog(
        true,
        DialogMessages.unArchiveProcess(processId),
        () => HttpService.unArchiveProcess(processId).then(() => history.push(redirectPath)),
        t("panels.actions.process-unarchive.yes", "Yes"),
        t("panels.actions.process-unarchive.no", "No"),
        // eslint-disable-next-line i18next/no-literal-string
        {category: events.categories.rightPanel, action: events.actions.buttonClick, name: "unarchive"},
      )}
    />
  )
}

const mapState = (state: RootState) => {
  return {
    processId: getProcessId(state),
    isArchived: isArchived(state),
  }
}

const mapDispatch = (dispatch) => bindActionCreators({
  toggleConfirmDialog,
}, dispatch)

type StateProps = ReturnType<typeof mapDispatch> & ReturnType<typeof mapState>

export default connect(mapState, mapDispatch)(UnArchiveButton)
