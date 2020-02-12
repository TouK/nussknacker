import {ThunkAction} from "../reduxTypes"

export type EventInfo = {
  category: string;
  action: string;
  name: string;
}

export type ReportEventAction = {
  type: "USER_TRACKING";
  tracking: {
    event: {
      e_c: string;
      e_a: string;
      e_n: string;
    };
  };
}

export function reportEvent(eventInfo: EventInfo): ThunkAction {
  return (dispatch) => dispatch({
    type: "USER_TRACKING",
    tracking: {
      event: {
        // eslint-disable-next-line @typescript-eslint/camelcase
        e_c: eventInfo.category,
        // eslint-disable-next-line @typescript-eslint/camelcase
        e_a: eventInfo.action,
        // eslint-disable-next-line @typescript-eslint/camelcase
        e_n: eventInfo.name,
      },
    },
  })
}
