import React from "react"
import {useTranslation} from "react-i18next"
import {useDispatch, useSelector} from "react-redux"
import {toggleModalDialog} from "../../../../actions/nk/modal"
import {isSubprocess} from "../../../../reducers/selectors/graph"
import {getFeatureSettings} from "../../../../reducers/selectors/settings"
import Dialogs from "../../../modals/Dialogs"
import ToolbarButton from "../../../toolbarComponents/ToolbarButton"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/counts.svg"
import {ToolbarButtonProps} from "../../types"

// TODO: counts and metrics should not be visible in archived process
function CountsButton(props: ToolbarButtonProps) {
  const {t} = useTranslation()
  const dispatch = useDispatch()
  const featuresSettings = useSelector(getFeatureSettings)
  const subprocess = useSelector(isSubprocess)
  const {disabled} = props

  return featuresSettings?.counts && !subprocess ? (
    <ToolbarButton
      name={t("panels.actions.test-counts.button", "counts")}
      icon={<Icon/>}
      disabled={disabled}
      onClick={() => dispatch(toggleModalDialog(Dialogs.types.calculateCounts))}
    />
  ) : null
}

export default CountsButton
