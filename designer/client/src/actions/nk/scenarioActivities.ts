import type { UIActivity } from "../../components/toolbars/activities";
import { extendActivitiesWithUIData } from "../../components/toolbars/activities/helpers/extendActivitiesWithUIData";
import { mergeActivityDataWithMetadata } from "../../components/toolbars/activities/helpers/mergeActivityDataWithMetadata";
import httpService from "../../http/HttpService";
import type { ThunkAction } from "../reduxTypes";

type GetScenarioActivitiesAction = {
    type: "GET_SCENARIO_ACTIVITIES";
    activities: UIActivity[];
};

type UpdateScenarioActivitiesAction = {
    type: "UPDATE_SCENARIO_ACTIVITIES";
    activities: UIActivity[];
};

type UpdateActivitiesSearchResultsAction = {
    type: "UPDATE_ACTIVITIES_SEARCH_RESULTS";
    foundActivities: string[];
    selectedResult: number;
};

export type ScenarioActivitiesActions = GetScenarioActivitiesAction | UpdateScenarioActivitiesAction | UpdateActivitiesSearchResultsAction;

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

export function updateSearchResults(foundActivities: string[], selectedResult: number): ThunkAction {
    return (dispatch) => {
        return dispatch({
            type: "UPDATE_ACTIVITIES_SEARCH_RESULTS",
            foundActivities,
            selectedResult,
        });
    };
}
