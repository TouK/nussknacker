import React from "react"
import {useAddProcessButtonProps} from "../components/table/AddProcessButton"
import {CustomTabPage} from "./CustomTabPage"

function ProcessesTab(): JSX.Element {
  const {action: addScenario} = useAddProcessButtonProps()
  const {action: addFragment} = useAddProcessButtonProps(true)

  return (
    <CustomTabPage id={"legacy_scenarios"} {...{addScenario, addFragment}}/>
  )
}

export default ProcessesTab
