import SystemUtils from "../../common/SystemUtils";
import * as MatomoTracker from "matomo-tracker";

export function matomoConfigured(analyticsSettings) {
  return !_.isEmpty(analyticsSettings)
    && analyticsSettings.engine === 'Matomo'
    && !_.isEmpty(analyticsSettings.url)
    && !_.isEmpty(analyticsSettings.siteId)
}

export function track(event, analyticsSettings) {
  new MatomoTracker(Number(analyticsSettings.siteId), analyticsSettings.url).track({
    _id: SystemUtils.getUserId(),
    url: window.location.href,
    ...event.event
  });
}