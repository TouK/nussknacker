import Analytics from "./Analytics";

export const analyticsMiddleware = (store) => next => action => {
  const state = !_.isEmpty(store) ? store.getState() : null
  const analyticsSettings = !_.isEmpty(state) ? state.settings.analyticsSettings : null

  if (actionTracked(action) && !_.isEmpty(analyticsSettings)) {
    analytics(analyticsSettings).sendEvent(action.tracking)
  }

  return next(action)
};

function actionTracked(action) {
  return !_.isEmpty(action.tracking);
}

const analytics = (analyticsSettings) => new Analytics(analyticsSettings);