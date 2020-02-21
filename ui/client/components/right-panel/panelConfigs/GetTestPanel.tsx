/* eslint-disable i18next/no-literal-string */
import {PanelConfig} from "../PanelConfig"
import {events} from "../../../analytics/TrackingEvents"
import InlinedSvgs from "../../../assets/icons/InlinedSvgs"
import Dialogs from "../../modals/Dialogs"
import {OwnProps as PanelOwnProps} from "../UserRightPanel"
import {Props} from "../Panels1"

type A = Pick<PanelOwnProps & Props,
  | "capabilities"
  | "showRunProcessDetails"
  | "processIsLatestVersion"
  | "testCapabilities"
  | "actions"
  | "isSubprocess"
  | "featuresSettings"
  | "processId"
  | "processToDisplay">

export function getTestPanel(props: A): PanelConfig {
  const {
    capabilities,
    showRunProcessDetails,
    processIsLatestVersion,
    testCapabilities,
    actions,
    isSubprocess,
    featuresSettings,
    processId,
    processToDisplay,
  } = props

  return {
    //TODO: testing subprocesses should work, but currently we don't know how to pass parameters in sane way...
    panelName: "Test",
    buttons: [
      {
        name: "from file",
        onDrop: (files) => files.forEach((file) => actions.testProcessFromFile(processId, file, processToDisplay)),
        onClick: () => actions.reportEvent({
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
        onClick: () => actions.hideRunProcessDetails(),
        icon: InlinedSvgs.buttonHide,
        disabled: !showRunProcessDetails,
        isHidden: !capabilities.write,
      },
      {
        name: "generate",
        onClick: () => actions.toggleModalDialog(Dialogs.types.generateTestData),
        icon: "generate.svg",
        disabled: !processIsLatestVersion || !testCapabilities.canGenerateTestData,
        isHidden: !capabilities.write,
      },
      //TODO: counts and metrics should not be visible in archived process
      {
        name: "counts",
        onClick: () => actions.toggleModalDialog(Dialogs.types.calculateCounts),
        icon: "counts.svg",
        isHidden: !featuresSettings?.counts || isSubprocess,
      },
    ],
    isHidden: isSubprocess,
  }
}
