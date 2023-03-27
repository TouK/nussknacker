import {css} from "@emotion/css"
import React from "react"
import {useSelector} from "react-redux"
import {ReactComponent as TipsWarning} from "../../assets/img/icons/tipsWarning.svg"
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
            icon={TipsWarning}
            message={"Warnings found - please look at left panel to see details. Proceed with caution"}
          />
        </h5>
      ) :
      null
  )
}

export default ProcessDialogWarnings
