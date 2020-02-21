/* eslint-disable i18next/no-literal-string */
import React from "react"
import {ExtractedPanel} from "../ExtractedPanel"
import {CapabilitiesType} from "../UserRightPanel"
import {RootState} from "../../../reducers/index"
import {connect} from "react-redux"
import {
  isLatestProcessVersion,
  getProcessToDisplay,
  getProcessId,
  getFeatureSettings,
  isSubprocess,
  getShowRunProcessDetails,
  getTestCapabilities,
} from "../selectors"
import {events} from "../../../analytics/TrackingEvents"
import InlinedSvgs from "../../../assets/icons/InlinedSvgs"
import Dialogs from "../../modals/Dialogs"
import {hideRunProcessDetails, testProcessFromFile} from "../../../actions/nk/process"
import {reportEvent} from "../../../actions/nk/reportEvent"
import {toggleModalDialog} from "../../../actions/nk/modal"

type OwnProps = {
  capabilities: CapabilitiesType,
}
type Props = OwnProps & StateProps

export function TestPanel(props: Props) {
  const {capabilities} = props
  const {processId, isSubprocess, featuresSettings, processToDisplay, showRunProcessDetails, processIsLatestVersion, testCapabilities} = props
  const {hideRunProcessDetails, reportEvent, testProcessFromFile, toggleModalDialog} = props

  const buttons = [
    {
      name: "from file",
      onDrop: (files) => files.forEach((file) => testProcessFromFile(processId, file, processToDisplay)),
      onClick: () => reportEvent({
        category: events.categories.rightPanel,
        action: events.actions.buttonClick,
        name: "from file",
      }),
      icon: InlinedSvgs.buttonFromFile,
      disabled: !testCapabilities.canBeTested,
      isHidden: !capabilities.write,
    },
    {
      name: "hide",
      onClick: () => hideRunProcessDetails(),
      icon: InlinedSvgs.buttonHide,
      disabled: !showRunProcessDetails,
      isHidden: !capabilities.write,
    },
    {
      name: "generate",
      onClick: () => toggleModalDialog(Dialogs.types.generateTestData),
      icon: "generate.svg",
      disabled: !processIsLatestVersion || !testCapabilities.canGenerateTestData,
      isHidden: !capabilities.write,
    },
    //TODO: counts and metrics should not be visible in archived process
    {
      name: "counts",
      onClick: () => toggleModalDialog(Dialogs.types.calculateCounts),
      icon: "counts.svg",
      isHidden: !featuresSettings?.counts || isSubprocess,
    },
  ]
  return (
    <ExtractedPanel panelName={"Test"} buttons={buttons} isHidden={isSubprocess}/>
  )
}

const mapState = (state: RootState) => ({
  testCapabilities: getTestCapabilities(state),
  showRunProcessDetails: getShowRunProcessDetails(state),
  processId: getProcessId(state),
  processToDisplay: getProcessToDisplay(state),
  processIsLatestVersion: isLatestProcessVersion(state),
  featuresSettings: getFeatureSettings(state),
  isSubprocess: isSubprocess(state),
})

const mapDispatch = {
  hideRunProcessDetails,
  reportEvent,
  testProcessFromFile,
  toggleModalDialog,
}

export type StateProps = typeof mapDispatch & ReturnType<typeof mapState>

export default connect(mapState, mapDispatch)(TestPanel)
