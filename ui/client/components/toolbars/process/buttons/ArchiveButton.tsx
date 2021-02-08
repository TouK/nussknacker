import React from "react"
import {RootState} from "../../../../reducers/index"
import {connect} from "react-redux"
import * as DialogMessages from "../../../../common/DialogMessages"
import HttpService from "../../../../http/HttpService"
import history from "../../../../history"
import {ArchiveTabData} from "../../../../containers/Archive"
import {events} from "../../../../analytics/TrackingEvents"
import {toggleConfirmDialog} from "../../../../actions/nk/ui/toggleConfirmDialog"
import {bindActionCreators} from "redux"
import ToolbarButton from "../../../toolbarComponents/ToolbarButton"
import {isArchivePossible, isSubprocess, getProcessId} from "../../../../reducers/selectors/graph"
import {useTranslation} from "react-i18next"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/archive.svg"

function ArchiveButton(props: StateProps) {
  const {
    processId, canArchive,
    toggleConfirmDialog,
  } = props
  const {t} = useTranslation()

  return (
    <ToolbarButton
      name={t("panels.actions.process-archive.button", "archive")}
      icon={<Icon/>}
      disabled={!canArchive}
      onClick={() => canArchive && toggleConfirmDialog(
        true,
        DialogMessages.archiveProcess(processId),
        () => HttpService.archiveProcess(processId).then(() => history.push(ArchiveTabData.path)),
        t("panels.actions.process-archive.yes", "Yes"),
        t("panels.actions.process-archive.no", "No"),
        // eslint-disable-next-line i18next/no-literal-string
        {category: events.categories.rightPanel, action: events.actions.buttonClick, name: "archive"},
      )}
    />
  )
}

const mapState = (state: RootState) => {
  return {
    processId: getProcessId(state),
    canArchive: isSubprocess(state) || isArchivePossible(state),
  }
}

const mapDispatch = (dispatch) => bindActionCreators({
  toggleConfirmDialog,
}, dispatch)

type StateProps = ReturnType<typeof mapDispatch> & ReturnType<typeof mapState>

export default connect(mapState, mapDispatch)(ArchiveButton)
