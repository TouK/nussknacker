import React from "react"
import {useTranslation} from "react-i18next"
import {RootState} from "../../../../../reducers/index"
import {connect} from "react-redux"
import {events} from "../../../../../analytics/TrackingEvents"
import InlinedSvgs from "../../../../../assets/icons/InlinedSvgs"
import {testProcessFromFile} from "../../../../../actions/nk/process"
import {reportEvent} from "../../../../../actions/nk/reportEvent"
import {ButtonWithIcon} from "../../../ButtonWithIcon"
import {getTestCapabilities, getProcessId, getProcessToDisplay} from "../../../selectors/graph"

type Props = StateProps

function FromFileButton(props: Props) {
  const {processId, processToDisplay, testCapabilities} = props
  const {reportEvent, testProcessFromFile} = props
  const {t} = useTranslation()

  return (
    <ButtonWithIcon
      name={t("panels.test.actions.fromFile.button", "from file")}
      icon={InlinedSvgs.buttonFromFile}
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
