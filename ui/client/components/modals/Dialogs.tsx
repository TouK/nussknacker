import React from "react"
import CalculateCountsDialog from "./CalculateCounts"
import CompareVersionsDialog from "./CompareVersionsDialog"
import ConfirmDialog from "./ConfirmDialog"
import {dialogTypesMap} from "./DialogsTypes"
import GenerateTestDataDialog from "./GenerateTestDataDialog"
import InfoModal from "./InfoModal"
import ProcessActionDialog from "./ProcessActionDialog"
import SaveProcessDialog from "./SaveProcessDialog"
import CustomActionDialog from "./CustomActionDialog"

export function AllDialogs(): JSX.Element {
  return (
    <>
      <ConfirmDialog/>
      <ProcessActionDialog/>
      <InfoModal/>
      <SaveProcessDialog/>
      <GenerateTestDataDialog/>
      <CalculateCountsDialog/>
      <CompareVersionsDialog/>
      <CustomActionDialog/>
    </>
  )
}

export default {types: dialogTypesMap, AllDialogs}

