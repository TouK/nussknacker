import {css} from "emotion"
import React from "react"
import {useSelector} from "react-redux"
import InlinedSvgs from "../../assets/icons/InlinedSvgs"
import {useNkTheme} from "../../containers/theme"
import {hasWarnings} from "../../reducers/selectors/graph"
import {IconWithLabel} from "../tips/IconWithLabel"

function ProcessDialogWarnings(): JSX.Element {
  const processHasWarnings = useSelector(hasWarnings)
  const {theme} = useNkTheme()
  return (
    processHasWarnings ?
      (
        <h5 className={css({color: theme.colors.warning})}>
          <IconWithLabel
            icon={InlinedSvgs.tipsWarning}
            message={"Warnings found - please look at left panel to see details. Proceed with caution"}
          />
        </h5>
      ) :
      null
  )
}

export default ProcessDialogWarnings
