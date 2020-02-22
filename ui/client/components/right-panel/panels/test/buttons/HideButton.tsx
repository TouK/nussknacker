/* eslint-disable i18next/no-literal-string */
import React from "react"
import {RootState} from "../../../../../reducers/index"
import {connect} from "react-redux"
import {getShowRunProcessDetails} from "../../../selectors"
import InlinedSvgs from "../../../../../assets/icons/InlinedSvgs"
import {hideRunProcessDetails} from "../../../../../actions/nk/process"
import {ButtonWithIcon} from "../../../ButtonWithIcon"

type Props = StateProps

function HideButton(props: Props) {
  const {showRunProcessDetails, hideRunProcessDetails} = props

  return (
    <ButtonWithIcon
      name={"hide"}
      icon={InlinedSvgs.buttonHide}
      disabled={!showRunProcessDetails}
      onClick={() => hideRunProcessDetails()}
    />
  )
}

const mapState = (state: RootState) => ({
  showRunProcessDetails: getShowRunProcessDetails(state),
})

const mapDispatch = {
  hideRunProcessDetails,
}

export type StateProps = typeof mapDispatch & ReturnType<typeof mapState>

export default connect(mapState, mapDispatch)(HideButton)
