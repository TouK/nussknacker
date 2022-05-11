import React from "react"
import {useAddProcessButtonProps} from "../components/table/AddProcessButton"
import {CustomTab} from "./CustomTab"

export function ScenariosTab(): JSX.Element {
  const {action: addScenario} = useAddProcessButtonProps()
  const {action: addFragment} = useAddProcessButtonProps(true)

  return (
    <CustomTab id={"scenarios_2"} {...{addScenario, addFragment}}/>
  )
}

export default ScenariosTab
