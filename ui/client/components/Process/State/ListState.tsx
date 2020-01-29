import React from "react"
import {CSSTransition, SwitchTransition} from "react-transition-group"
import {withTranslation} from "react-i18next"
import {WithTranslation} from "react-i18next/src"
import {compose} from "redux"

import ProcessStateUtils from "./ProcessStateUtils"
import {ListProcess, ProcessState} from "../ProcessTypes"
import {absoluteBePath} from "../../../common/UrlUtils"
import {unknownTooltip} from "../ProcessMessages"

type State = {
  animationTimeout: {
    enter: number;
    appear: number;
    exit: number;
  };
}

type OwnProps = {
  processState?: ProcessState;
  isStateLoaded: boolean;
  process: ListProcess;
}

type Props = OwnProps & WithTranslation

class ListState extends React.Component<Props, State> {
  static defaultProps = {
    isStateLoaded: false,
    processState: null,
  }

  state = {
    animationTimeout: {
      enter: 500,
      appear: 500,
      exit: 500,
    },
  }

  animationListener = (node, done) => node.addEventListener("transitionend", done, false)

  getTooltip = (process: ListProcess, processState: ProcessState, isStateLoaded: boolean) => {
    if (isStateLoaded === false ||processState == null) {
      return process.state?.tooltip || unknownTooltip
    }

    return processState?.tooltip || unknownTooltip
  }

  getIcon = (process: ListProcess, processState: ProcessState, isStateLoaded: boolean) => {
    if (isStateLoaded === false || processState == null) {
      return absoluteBePath(process.state?.icon  ||  ProcessStateUtils.UNKNOWN_ICON)
    }

    return absoluteBePath(processState?.icon || ProcessStateUtils.UNKNOWN_ICON)
  }

  render() {
    const {process, processState, isStateLoaded} = this.props
    const icon = this.getIcon(process, processState, isStateLoaded)
    const tooltip = this.getTooltip(process, processState, isStateLoaded)
    // eslint-disable-next-line i18next/no-literal-string
    const iconClass = `state-list${isStateLoaded === false ? " state-pending" : ""}`
    const transitionKey = `${process.id}-${icon}`

    return (
      <SwitchTransition>
        <CSSTransition key={transitionKey} classNames="fade" timeout={this.state.animationTimeout} addEndListener={this.animationListener}>
          <img src={icon} title={tooltip} alt={tooltip} className={iconClass}/>
        </CSSTransition>
      </SwitchTransition>
    )
  }
}

const enhance = compose(
    withTranslation(),
)

export default enhance(ListState)
