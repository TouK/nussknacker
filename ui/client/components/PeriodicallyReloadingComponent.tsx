import React from "react"

abstract class PeriodicallyReloadingComponent<P, S> extends React.Component<P, S> {
  baseIntervalTime = 40000
  intervalId = null

  abstract getIntervalTime(): number

  abstract reload(): void

  onMount(): void {
    //Default empty implementation onMount
  }

  componentDidMount() {
    this.onMount()
    this.intervalId = setInterval(() => this.reload(), this.getIntervalTime() || this.baseIntervalTime)
  }

  componentWillUnmount() {
    clearInterval(this.intervalId)
  }
}

export default PeriodicallyReloadingComponent
