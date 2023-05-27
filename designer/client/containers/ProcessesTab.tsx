import React from "react"
import {useAddProcessButtonProps} from "../components/table/AddProcessButton"
import {CustomTabPage} from "./CustomTabPage"

const ProcessesTab = () => (
  <CustomTabPage
    id="processes"
    addScenario={useAddProcessButtonProps().action}
    addFragment={useAddProcessButtonProps(true).action}
  />
)

export default ProcessesTab
