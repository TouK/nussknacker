import React from "react"
import {useAddProcessButtonProps} from "../components/table/AddProcessButton"
import {CustomTabPage} from "./CustomTabPage"

const ScenariosTab = () => (
  <CustomTabPage
    id="scenarios"
    addScenario={useAddProcessButtonProps().action}
    addFragment={useAddProcessButtonProps(true).action}
  />
)

export default ScenariosTab
