import React from "react";
import ConfirmDialog from "./ConfirmDialog"
import SaveProcessDialog from "./SaveProcessDialog"
import GenerateTestDataDialog from "./GenerateTestDataDialog"
import CalculateCountsDialog from "./CalculateCountsDialog"
import CompareVersionsDialog from "./CompareVersionsDialog"

export default {

  types: {
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
          <SaveProcessDialog/>
          <GenerateTestDataDialog/>
          <CalculateCountsDialog/>
          <CompareVersionsDialog/>

        </div>
      );

    }
  })

}