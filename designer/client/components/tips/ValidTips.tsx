import React from "react"
import InlinedSvgs from "../../assets/icons/InlinedSvgs"
import ValidTip from "./ValidTip"

export default function ValidTips(props: {hasNeitherErrorsNorWarnings?: boolean, testing?: boolean}): JSX.Element {
  const {hasNeitherErrorsNorWarnings, testing} = props

  return (
    <React.Fragment>
      {hasNeitherErrorsNorWarnings && <ValidTip icon={InlinedSvgs.tipsSuccess} message={"Everything seems to be OK"}/>}
      {testing && <ValidTip icon={InlinedSvgs.testingMode} message={"Testing mode enabled"}/>}
    </React.Fragment>
  )
}
