import React from "react"
import {SwitchTransition} from "react-transition-group"
import {WithTranslation, withTranslation} from "react-i18next"
import {compose} from "redux"
import {ProcessStateType, ProcessType} from "./types"
import {CssFade} from "../CssFade"

import {Popover} from "react-bootstrap"
import {OverlayTrigger} from "react-bootstrap/lib"
import ProcessStateUtils from "./ProcessStateUtils"
import {css, cx} from "@emotion/css"

type State = {
  animationTimeout: {
    enter: number,
    appear: number,
    exit: number,
  },
}

type OwnProps = {
  processState?: ProcessStateType,
  isStateLoaded: boolean,
  process: ProcessType,
  animation?: boolean,
  height?: number,
  width?: number,
  popover?: boolean,
}

type Props = OwnProps & WithTranslation

class ProcessStateIcon extends React.Component<Props, State> {
  static defaultProps = {
    isStateLoaded: false,
    processState: null,
    animation: true,
    height: 24,
    width: 24,
    popover: false,
  }

  // eslint-disable-next-line i18next/no-literal-string
  static popoverConfigs = {placement: "bottom", triggers: ["click"]}

  imageWithPopover = (image, processName: string, tooltip: string, errors: Array<string>) => {
    const {t} = this.props

    const overlay = (
      <Popover id="state-icon-popover" title={processName}>
        <strong>{tooltip}</strong>
        {errors.length !== 0 ?
          (
            <div>
              <span>{t("stateIcon.errors", "Errors:")}</span>
              <ul>
                {errors.map((error, key) => <li key={key}>{error}</li>)}
              </ul>
            </div>
          ) :
          null
        }
      </Popover>
    )

    return (
      <OverlayTrigger
        trigger={ProcessStateIcon.popoverConfigs.triggers}
        placement={ProcessStateIcon.popoverConfigs.placement}
        overlay={overlay}
      >
        {image}
      </OverlayTrigger>
    )
  }

  render() {
    const {animation, process, processState, isStateLoaded, height, width, popover} = this.props
    const icon = ProcessStateUtils.getStatusIcon(process, processState, isStateLoaded)
    const tooltip = ProcessStateUtils.getStatusTooltip(process, processState, isStateLoaded)
    const errors = (isStateLoaded ? processState?.errors : process?.state?.errors) || []

    const iconClass = cx(
      "state-icon",
      isStateLoaded === false && "state-pending",
      //TODO: normalize colors svg files
      (process.isArchived || process.isSubprocess) && css({
        filter: "invert()",
        opacity: 1,
      })
    )
    const transitionKey = `${process.id}-${icon}`

    const image = (
      <img
        src={icon}
        alt={tooltip}
        title={tooltip}
        className={iconClass}
        height={height}
        width={width}
      />
    )

    return animation === true ?
      (
        <SwitchTransition>
          <CssFade key={transitionKey}>
            {popover === true ? this.imageWithPopover(image, process.name, tooltip, errors) : image}
          </CssFade>
        </SwitchTransition>
      ) :
      image
  }
}

const enhance = compose(
  withTranslation(),
)

export default enhance(ProcessStateIcon)

