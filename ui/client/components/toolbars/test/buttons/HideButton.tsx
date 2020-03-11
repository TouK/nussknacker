import React from "react"
import {useTranslation} from "react-i18next"
import {RootState} from "../../../../reducers/index"
import {connect} from "react-redux"
import {hideRunProcessDetails} from "../../../../actions/nk/process"
import ToolbarButton from "../../../toolbarComponents/ToolbarButton"
import {getShowRunProcessDetails} from "../../../../reducers/selectors/graph"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/hide.svg"

type Props = StateProps

function HideButton(props: Props) {
  const {showRunProcessDetails, hideRunProcessDetails} = props
  const {t} = useTranslation()

  return (
    <>
      <ToolbarButton
        name={t("panels.actions.test-hide.button", "hide")}
        icon={<Icon/>}
        disabled={!showRunProcessDetails}
        onClick={() => hideRunProcessDetails()}
      />
    </>
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
