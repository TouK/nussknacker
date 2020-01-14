import React from "react"
import CalculateCountsDialog from "./CalculateCountsDialog"
import CompareVersionsDialog from "./CompareVersionsDialog"
import ConfirmDialog from "./ConfirmDialog"
import GenerateTestDataDialog from "./GenerateTestDataDialog"
import InfoModal from "./InfoModal"
import ProcessActionDialog from "./ProcessActionDialog"
import SaveProcessDialog from "./SaveProcessDialog"

export class AllDialogs extends React.Component {
  render() {
    return (
        <div>
          <ConfirmDialog/>
          <ProcessActionDialog/>
          <InfoModal/>
          <SaveProcessDialog/>
          <GenerateTestDataDialog/>
          <CalculateCountsDialog/>
          <CompareVersionsDialog/>
        </div>
    )
  }
}

export type DialogType = "INFO_MODAL"
    | "PROCESS_ACTION"
    | "SAVE_PROCESS"
    | "GENERATE_TEST_DATA"
    | "CALCULATE_COUNTS"
    | "COMPARE_VERSIONS"

export const types: { [k: string]: DialogType } = {
  infoModal: "INFO_MODAL",
  processAction: "PROCESS_ACTION",
  saveProcess: "SAVE_PROCESS",
  generateTestData: "GENERATE_TEST_DATA",
  calculateCounts: "CALCULATE_COUNTS",
  compareVersions: "COMPARE_VERSIONS",
}

export default {types, AllDialogs}

