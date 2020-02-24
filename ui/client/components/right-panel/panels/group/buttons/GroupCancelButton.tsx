import React from "react"
import {RootState} from "../../../../../reducers/index"
import {connect} from "react-redux"
import InlinedSvgs from "../../../../../assets/icons/InlinedSvgs"
import {cancelGrouping} from "../../../../../actions/nk/groups"
import {ButtonWithIcon} from "../../../ButtonWithIcon"
import {getGroupingState} from "../../../selectors/graph"
import {useTranslation} from "react-i18next"

type Props = StateProps

function GroupFinishButton(props: Props) {
  const {groupingState, cancelGrouping} = props
  const {t} = useTranslation()

  return (
    <ButtonWithIcon
      name={t("panels.actions.group-cancel.button", "cancel")}
      icon={InlinedSvgs.buttonUngroup}
      disabled={!groupingState}
      onClick={cancelGrouping}
    />
  )
}

const mapState = (state: RootState) => ({
  groupingState: getGroupingState(state),
})

const mapDispatch = {
  cancelGrouping,
}

type StateProps = typeof mapDispatch & ReturnType<typeof mapState>

export default connect(mapState, mapDispatch)(GroupFinishButton)
