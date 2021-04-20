import React from "react"
import {useTranslation} from "react-i18next"
import {useDispatch, useSelector} from "react-redux"
import {toggleModalDialog} from "../../../../actions/nk/modal"
import {isSubprocess} from "../../../../reducers/selectors/graph"
import {getFeatureSettings} from "../../../../reducers/selectors/settings"
import Dialogs from "../../../modals/Dialogs"
import ToolbarButton from "../../../toolbarComponents/ToolbarButton"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/counts.svg"

// TODO: counts and metrics should not be visible in archived process
function CountsButton() {
  const {t} = useTranslation()
  const dispatch = useDispatch()
  const featuresSettings = useSelector(getFeatureSettings)
  const subprocess = useSelector(isSubprocess)

  return featuresSettings?.counts && !subprocess ? (
    <ToolbarButton
      name={t("panels.actions.test-counts.button", "counts")}
      icon={<Icon/>}
      onClick={() => dispatch(toggleModalDialog(Dialogs.types.calculateCounts))}
    />
  ) : null
}

export default CountsButton
