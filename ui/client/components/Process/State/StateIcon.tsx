import React from "react"
import {CSSTransition, SwitchTransition} from "react-transition-group"
import {withTranslation} from "react-i18next"
import {WithTranslation} from "react-i18next/src"
import {compose} from "redux"

import ProcessStateUtils from "./ProcessStateUtils"
import {ProcessStateType, ProcessType} from "../ProcessTypes"
import {absoluteBePath} from "../../../common/UrlUtils"
import {unknownName, unknownTooltip} from "../ProcessMessages"

import {Popover} from "react-bootstrap"
import {OverlayTrigger} from "react-bootstrap/lib"

type State = {
  animationTimeout: {
    enter: number;
    appear: number;
    exit: number;
  };
}

type OwnProps = {
  processState?: ProcessStateType;
  isStateLoaded: boolean;
  process: ProcessType;
  animation: boolean;
  height: number;
  width: number;
}

type Props = OwnProps & WithTranslation

class StateIcon extends React.Component<Props, State> {
  static defaultProps = {
    isStateLoaded: false,
    processState: null,
    animation: true,
    height: 24,
    width: 24,
  }

  state = {
    animationTimeout: {
      enter: 500,
      appear: 500,
      exit: 500,
    },
  }

  animationListener = (node, done) => node.addEventListener("transitionend", done, false)

  getTooltip = (process: ProcessType, processState: ProcessStateType, isStateLoaded: boolean) => {
    if (isStateLoaded === false) {
      return process.state?.tooltip || unknownTooltip()
    }

    return processState?.tooltip || unknownTooltip()
  }

  getIcon = (process: ProcessType, processState: ProcessStateType, isStateLoaded: boolean) => {
    if (isStateLoaded === false) {
      return absoluteBePath(process.state?.icon || ProcessStateUtils.UNKNOWN_ICON)
    }

    return absoluteBePath(processState?.icon || ProcessStateUtils.UNKNOWN_ICON)
  }

  render() {
    const {t, animation, process, processState, isStateLoaded, height, width} = this.props
    const icon = this.getIcon(process, processState, isStateLoaded)
    const tooltip = this.getTooltip(process, processState, isStateLoaded)
    const name = (isStateLoaded ? processState?.name : process?.state?.name) || unknownName()
    const errors = (isStateLoaded ? processState?.errors : process?.state?.errors) || []

    // eslint-disable-next-line i18next/no-literal-string
    const iconClass = `state-list${isStateLoaded === false ? " state-pending" : ""}`
    const transitionKey = `${process.id}-${icon}`

    const popoverHoverFocus = (
      // eslint-disable-next-line i18next/no-literal-string
      <Popover id="popover-trigger-focus"  title={name}>
        <strong>{tooltip}</strong>
        { errors.length !== 0 ?
          <div>
            <span>{t("stateIcon.errors", "Errors:")}</span>
            <ul>
              {errors.map((error, key) =>
                <li key={key}>{error}</li>,
              )}
            </ul>
          </div>
          : null
        }
      </Popover>
    )

    const image = (
      <OverlayTrigger
        // eslint-disable-next-line i18next/no-literal-string
        trigger={["hover", "focus"]}
        // eslint-disable-next-line i18next/no-literal-string
        placement="bottom"
        overlay={popoverHoverFocus}
      >
        <img src={icon} className={iconClass} height={height} width={width}/>
      </OverlayTrigger>
    )

    if (animation === false) {
      return (
        image
      )
    }

    return (
      <SwitchTransition>
        <CSSTransition key={transitionKey} classNames="fade" timeout={this.state.animationTimeout} addEndListener={this.animationListener}>
          {image}
        </CSSTransition>
      </SwitchTransition>
    )
  }
}

const enhance = compose(
    withTranslation(),
)

export default enhance(StateIcon)
