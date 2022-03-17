import React from "react"
import {useAddProcessButtonProps} from "../components/table/AddProcessButton"
import {CustomTab} from "./CustomTab"

export const SCENARIOS_TAB_ID = "scenarios_2"

export function ScenariosTab(): JSX.Element {
  const {action: addScenario} = useAddProcessButtonProps()
  const {action: addFragment} = useAddProcessButtonProps(true)

  return (
    <CustomTab id={SCENARIOS_TAB_ID} {...{addScenario, addFragment}}/>
  )
}

