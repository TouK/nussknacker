import React from "react"
import {RootState} from "../../../../../reducers/index"
import {connect} from "react-redux"
import InlinedSvgs from "../../../../../assets/icons/InlinedSvgs"
import {finishGrouping} from "../../../../../actions/nk/groups"
import {ButtonWithIcon} from "../../../ButtonWithIcon"
import {getGroupingState} from "../../../selectors/graph"
import {useTranslation} from "react-i18next"

type Props = StateProps

function GroupFinishButton(props: Props) {
  const {groupingState, finishGrouping} = props
  const {t} = useTranslation()

  return (
    <ButtonWithIcon
      name={t("panels.group.actions.finish.button", "finish")}
      icon={InlinedSvgs.buttonGroup}
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
