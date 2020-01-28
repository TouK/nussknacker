import React from "react"

abstract class PeriodicallyReloadingComponent<P, S> extends React.Component<P, S> {
  baseIntervalTime = 40000
  intervalId = null

  abstract getIntervalTime(): number

  abstract onMount(): void

  abstract reload(): void

  componentDidMount() {
    this.onMount()
    this.intervalId = setInterval(() => this.reload(), this.getIntervalTime() || this.baseIntervalTime)
  }

  componentWillUnmount() {
    clearInterval(this.intervalId)
  }
}

export default PeriodicallyReloadingComponent
