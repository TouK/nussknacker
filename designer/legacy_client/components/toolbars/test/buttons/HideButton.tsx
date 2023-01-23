import React from "react"
import {useTranslation} from "react-i18next"
import {RootState} from "../../../../reducers/index"
import {connect} from "react-redux"
import {hideRunProcessDetails} from "../../../../actions/nk/process"
import {getShowRunProcessDetails} from "../../../../reducers/selectors/graph"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/hide.svg"
import ToolbarButton from "../../../toolbarComponents/ToolbarButton"
import {ToolbarButtonProps} from "../../types"

type Props = StateProps & ToolbarButtonProps

function HideButton(props: Props) {
  const {showRunProcessDetails, hideRunProcessDetails, disabled} = props
  const available = !disabled && showRunProcessDetails
  const {t} = useTranslation()

  return (
    <ToolbarButton
      name={t("panels.actions.test-hide.button", "hide")}
      icon={<Icon/>}
      disabled={!available}
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
