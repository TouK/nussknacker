import React from "react"
import {useTranslation} from "react-i18next"
import {useSelector} from "react-redux"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/counts.svg"
import {isSubprocess} from "../../../../reducers/selectors/graph"
import {getFeatureSettings} from "../../../../reducers/selectors/settings"
import {useWindows} from "../../../../windowManager"
import {WindowKind} from "../../../../windowManager/WindowKind"
import ToolbarButton from "../../../toolbarComponents/ToolbarButton"
import {ToolbarButtonProps} from "../../types"

// TODO: counts and metrics should not be visible in archived process
function CountsButton(props: ToolbarButtonProps) {
  const {t} = useTranslation()
  const featuresSettings = useSelector(getFeatureSettings)
  const subprocess = useSelector(isSubprocess)
  const {open} = useWindows()
  const {disabled} = props

  return featuresSettings?.counts && !subprocess ? (
    <ToolbarButton
      name={t("panels.actions.test-counts.button", "counts")}
      icon={<Icon />}
      disabled={disabled}
      onClick={() => open({
        kind: WindowKind.calculateCounts,
      })}
    />
  ) : null
}

export default CountsButton
