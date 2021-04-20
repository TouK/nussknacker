import React from "react"
import {RootState} from "../../../../reducers/index"
import {connect} from "react-redux"
import {finishGrouping} from "../../../../actions/nk/groups"
import {CapabilitiesToolbarButton} from "../../../toolbarComponents/CapabilitiesToolbarButton"
import {getGroupingState} from "../../../../reducers/selectors/graph"
import {useTranslation} from "react-i18next"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/group-finish.svg"

type Props = StateProps

function GroupFinishButton(props: Props) {
  const {groupingState, finishGrouping} = props
  const {t} = useTranslation()

  return (
    <CapabilitiesToolbarButton
      write
      name={t("panels.actions.group-finish.button", "finish")}
      icon={<Icon/>}
      disabled={(groupingState || []).length <= 1}
      onClick={finishGrouping}
    />
  )
}

const mapState = (state: RootState) => ({
  groupingState: getGroupingState(state),
})

const mapDispatch = {
  finishGrouping,
}

type StateProps = typeof mapDispatch & ReturnType<typeof mapState>

export default connect(mapState, mapDispatch)(GroupFinishButton)
