import MatomoAnalytics from "./MatomoAnalytics";

export default class Analytics {

  constructor(analyticsSettings) {
    if (analyticsSettings.engine === 'Matomo') {
      this.engine = new MatomoAnalytics(analyticsSettings)
    }
  }

  sendEvent = (event) => this.engine.sendEvent(event)
}