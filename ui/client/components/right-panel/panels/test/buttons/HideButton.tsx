import React from "react"
import {useTranslation} from "react-i18next"
import {RootState} from "../../../../../reducers/index"
import {connect} from "react-redux"
import InlinedSvgs from "../../../../../assets/icons/InlinedSvgs"
import {hideRunProcessDetails} from "../../../../../actions/nk/process"
import {ButtonWithIcon} from "../../../ButtonWithIcon"
import {getShowRunProcessDetails} from "../../../selectors/graph"

type Props = StateProps

function HideButton(props: Props) {
  const {showRunProcessDetails, hideRunProcessDetails} = props
  const {t} = useTranslation()

  return (
    <ButtonWithIcon
      name={t("panels.test.actions.hide.button", "hide")}
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
