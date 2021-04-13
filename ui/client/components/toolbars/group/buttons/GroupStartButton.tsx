import React from "react"
import {RootState} from "../../../../reducers/index"
import {connect} from "react-redux"
import {startGrouping} from "../../../../actions/nk/groups"
import {CapabilitiesToolbarButton} from "../../../toolbarComponents/CapabilitiesToolbarButton"
import {getGroupingState} from "../../../../reducers/selectors/graph"
import {useTranslation} from "react-i18next"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/group-start.svg"

type Props = StateProps

function GroupStartButton(props: Props) {
  const {groupingState, startGrouping} = props
  const {t} = useTranslation()

  return (
    <CapabilitiesToolbarButton
      write
      name={t("panels.actions.group-start.button", "start")}
      icon={<Icon/>}
      disabled={groupingState != null}
      onClick={startGrouping}
    />
  )
}

const mapState = (state: RootState) => ({
  groupingState: getGroupingState(state),
})

const mapDispatch = {
  startGrouping,
}

type StateProps = typeof mapDispatch & ReturnType<typeof mapState>

export default connect(mapState, mapDispatch)(GroupStartButton)
