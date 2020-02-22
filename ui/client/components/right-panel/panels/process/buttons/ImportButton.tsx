/* eslint-disable i18next/no-literal-string */
import React from "react"
import {RootState} from "../../../../../reducers/index"
import {connect} from "react-redux"
import {events} from "../../../../../analytics/TrackingEvents"
import InlinedSvgs from "../../../../../assets/icons/InlinedSvgs"
import {importFiles} from "../../../../../actions/nk/importExport"
import {reportEvent} from "../../../../../actions/nk/reportEvent"
import {bindActionCreators} from "redux"
import {getProcessId} from "../../../selectors"
import {ButtonWithIcon} from "../../../ButtonWithIcon"

type Props = StateProps

function ImportButton(props: Props) {
  const {
    processId, importFiles, reportEvent,

  } = props

  return (
    <ButtonWithIcon
      name={"import"}
      icon={InlinedSvgs.buttonImport}
      disabled={false}
      onClick={() => reportEvent({
        category: events.categories.rightPanel,
        action: events.actions.buttonClick,
        name: "import",
      })}
      onDrop={(files) => importFiles(files, processId)}
    />
  )
}

const mapState = (state: RootState) => ({
  processId: getProcessId(state),
})

const mapDispatch = (dispatch) => bindActionCreators({
  importFiles,
  reportEvent,
}, dispatch)

type StateProps = ReturnType<typeof mapDispatch> & ReturnType<typeof mapState>

export default connect(mapState, mapDispatch)(ImportButton)
