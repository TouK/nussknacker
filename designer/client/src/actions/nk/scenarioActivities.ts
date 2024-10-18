import { ThunkAction } from "../reduxTypes";
import httpService from "../../http/HttpService";
import { mergeActivityDataWithMetadata } from "../../components/toolbars/activities/helpers/mergeActivityDataWithMetadata";
import { extendActivitiesWithUIData } from "../../components/toolbars/activities/helpers/extendActivitiesWithUIData";
import { UIActivity } from "../../components/toolbars/activities";

export type GetScenarioActivitiesAction = {
    type: "GET_SCENARIO_ACTIVITIES";
    activities: UIActivity[];
};

export type UpdateScenarioActivitiesAction = {
    type: "UPDATE_SCENARIO_ACTIVITIES";
    activities: UIActivity[];
};

export function getScenarioActivities(scenarioName: string): ThunkAction {
    return (dispatch) => {
        return Promise.all([httpService.fetchActivitiesMetadata(scenarioName), httpService.fetchActivities(scenarioName)]).then(
            ([
                { data: activitiesMetadata },
                {
                    data: { activities },
                },
            ]) => {
                const mergedActivitiesDataWithMetadata = mergeActivityDataWithMetadata(activities, activitiesMetadata);
                return dispatch({
                    type: "GET_SCENARIO_ACTIVITIES",
                    activities: extendActivitiesWithUIData(mergedActivitiesDataWithMetadata),
                });
            },
        );
    };
}

export function updateScenarioActivities(activities: (activities: UIActivity[]) => UIActivity[]): ThunkAction {
    return (dispatch, getState) => {
        return dispatch({
            type: "UPDATE_SCENARIO_ACTIVITIES",
            activities: activities(getState().processActivity.activities),
        });
    };
}
