import React from "react";
import ConfirmDialog from "./ConfirmDialog"
import DeployProcessDialog from "./DeployProcessDialog"
import SaveProcessDialog from "./SaveProcessDialog"
import GenerateTestDataDialog from "./GenerateTestDataDialog"
import CalculateCountsDialog from "./CalculateCountsDialog"
import CompareVersionsDialog from "./CompareVersionsDialog"
import InfoModal from "./InfoModal";

export default {

  types: {
    infoModal: "INFO_MODAL",
    deployProcess: "DEPLOY_PROCESS",
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
          <DeployProcessDialog />
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