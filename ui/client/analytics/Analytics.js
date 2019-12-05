import {matomoConfigured, track} from "./matomo/MatomoTracker";

export const analytics = (store) => next => action => {
  const state = !_.isEmpty(store) ? store.getState() : null
  const analyticsSettings = !_.isEmpty(state) ? state.settings.analyticsSettings : null

  if (actionTracked(action) && matomoConfigured(analyticsSettings)) {
    track(action.tracking, analyticsSettings)
  }

  return next(action)
};

function actionTracked(action) {
  return !_.isEmpty(action.tracking);
}