import React from "react";


class PeriodicallyReloadingComponent extends React.Component {

  interval() {
    return 10000;
  }

  reload() {
    //to be overridden
  }

  componentDidMount() {
    const intervalId = setInterval(() => this.reload(), this.interval());
    this.setState({intervalId});
    this.reload();
  }

  componentWillUnmount() {
    if (this.state.intervalId) {
      clearInterval(this.state.intervalId)
    }
  }

}

export default PeriodicallyReloadingComponent;
