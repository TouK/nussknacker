import React from "react";
import ConfirmDialog from "./ConfirmDialog"
import ProcessActionDialog from "./ProcessActionDialog"
import SaveProcessDialog from "./SaveProcessDialog"
import GenerateTestDataDialog from "./GenerateTestDataDialog"
import CalculateCountsDialog from "./CalculateCountsDialog"
import CompareVersionsDialog from "./CompareVersionsDialog"
import InfoModal from "./InfoModal";

export default {

  types: {
    infoModal: "INFO_MODAL",
    processAction: "PROCESS_ACTION",
    saveProcess: "SAVE_PROCESS",
    generateTestData: "GENERATE_TEST_DATA",
    calculateCounts: "CALCULATE_COUNTS",
    compareVersions: "COMPARE_VERSIONS"

  },

  AllDialogs: React.createClass({
    render() {
      return (
        <div>
          <ConfirmDialog/>
          <ProcessActionDialog />
          <InfoModal/>
          <SaveProcessDialog/>
          <GenerateTestDataDialog/>
          <CalculateCountsDialog/>
          <CompareVersionsDialog/>
        </div>
      );

    }
  })

}