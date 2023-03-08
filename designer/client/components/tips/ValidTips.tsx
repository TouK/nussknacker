import React from "react"
import {ReactComponent as TestingMode} from "../../assets/img/icons/testingMode.svg"
import {ReactComponent as TipsSuccess} from "../../assets/img/icons/tipsSuccess.svg"

import ValidTip from "./ValidTip"

export default function ValidTips(props: {hasNeitherErrorsNorWarnings?: boolean, testing?: boolean}): JSX.Element {
  const {hasNeitherErrorsNorWarnings, testing} = props

  return (
    <React.Fragment>
      {hasNeitherErrorsNorWarnings && <ValidTip icon={TipsSuccess} message={"Everything seems to be OK"}/>}
      {testing && <ValidTip icon={TestingMode} message={"Testing mode enabled"}/>}
    </React.Fragment>
  )
}
