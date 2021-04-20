import React from "react"
import {useTranslation} from "react-i18next"
import {RootState} from "../../../../reducers/index"
import {connect} from "react-redux"
import {events} from "../../../../analytics/TrackingEvents"
import {testProcessFromFile} from "../../../../actions/nk/process"
import {reportEvent} from "../../../../actions/nk/reportEvent"
import {CapabilitiesToolbarButton} from "../../../toolbarComponents/CapabilitiesToolbarButton"
import {getTestCapabilities, getProcessId, getProcessToDisplay} from "../../../../reducers/selectors/graph"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/from-file.svg"

type Props = StateProps

function FromFileButton(props: Props) {
  const {processId, processToDisplay, testCapabilities} = props
  const {reportEvent, testProcessFromFile} = props
  const {t} = useTranslation()

  return (
    <CapabilitiesToolbarButton write
      name={t("panels.actions.test-fromFile.button", "from file")}
      icon={<Icon/>}
      disabled={!testCapabilities.canBeTested}
      onDrop={(files) => files.forEach((file) => testProcessFromFile(processId, file, processToDisplay))}
      onClick={() => reportEvent({
        category: events.categories.rightPanel,
        action: events.actions.buttonClick,
        name: "from file",
      })}
    />
  )
}

const mapState = (state: RootState) => ({
  testCapabilities: getTestCapabilities(state),
  processId: getProcessId(state),
  processToDisplay: getProcessToDisplay(state),
})

const mapDispatch = {
  reportEvent,
  testProcessFromFile,
}

export type StateProps = typeof mapDispatch & ReturnType<typeof mapState>

export default connect(mapState, mapDispatch)(FromFileButton)
