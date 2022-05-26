import React from "react"
import {useAddProcessButtonProps} from "../components/table/AddProcessButton"
import {CustomTabPage} from "./CustomTabPage"

export function ScenariosTab(): JSX.Element {
  const {action: addScenario} = useAddProcessButtonProps()
  const {action: addFragment} = useAddProcessButtonProps(true)

  return (
    <CustomTabPage id={"scenarios"} {...{addScenario, addFragment}}/>
  )
}

export default ScenariosTab
