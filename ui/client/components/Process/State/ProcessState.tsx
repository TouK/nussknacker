import React from "react"
import {ProcessStateType, ProcessType} from "../ProcessTypes"
import StateIcon from "./StateIcon"
import {CSSTransition, SwitchTransition} from "react-transition-group"
import ProcessStateUtils from "./ProcessStateUtils"
import {unknownDescription, unknownName} from "../ProcessMessages"

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
  iconHeight: number;
  iconWidth: number;
}

class ProcessState extends React.Component<OwnProps, State> {
  static defaultProps = {
    isStateLoaded: false,
    processState: null,
    iconHeight: 30,
    iconWidth: 30,
  }

  state = {
    animationTimeout: {
      enter: 500,
      appear: 500,
      exit: 500,
    },
  }

  animationListener = (node, done) => node.addEventListener("transitionend", done, false)

  render() {
    const {process, processState, isStateLoaded, iconHeight, iconWidth} = this.props
    const transitionKey = `${process.id}-${processState?.icon || process?.state?.icon || ProcessStateUtils.UNKNOWN_ICON}`
    const description = isStateLoaded ? processState?.description : process?.state?.description || unknownDescription()
    const name = isStateLoaded ? processState?.name : process?.state?.name || unknownName()

    return (
        <SwitchTransition>
          <CSSTransition key={transitionKey} classNames="fade" timeout={this.state.animationTimeout} addEndListener={this.animationListener}>
            <div className={"panel-state"}>
              <div className={"state-icon"}>
                <StateIcon
                  animation={false}
                  process={process}
                  processState={processState}
                  isStateLoaded={isStateLoaded}
                  height={iconHeight}
                  width={iconWidth}
                />
              </div>
              <div className={"state-text"}>
                <span className={"state-name"}>{name}</span>
                <br/>
                <span className={"state-description"}>{description}</span>
              </div>
              <div className={"cleared"}/>
          </div>
        </CSSTransition>
      </SwitchTransition>
    )
  }
}

export default ProcessState
