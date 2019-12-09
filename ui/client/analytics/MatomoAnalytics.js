import SystemUtils from "../common/SystemUtils";
import * as MatomoTracker from "matomo-tracker";
import AnalyticsEngine from "./AnalyticsEngine";

export default class MatomoAnalytics extends AnalyticsEngine {

  constructor(analyticsSettings) {
    super();
    this.siteId = analyticsSettings.siteId
    this.url = analyticsSettings.url
  }

  sendEvent = (event) => {
    new MatomoTracker(Number(this.siteId), this.url).track({
      _id: SystemUtils.getUserId(),
      url: window.location.href,
      ...event.event
    });
  }
}
