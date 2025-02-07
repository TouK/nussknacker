import { Action, Reducer, ThunkAction } from "../actions/reduxTypes";
import api from "../api";

export type CloudDataType = NonNullable<{
    additionalComponents: string[];
    configuredComponents: { name: string; type: string }[];
}>;

export const reducer: Reducer<CloudDataType> = (state = { additionalComponents: [], configuredComponents: [] }, action: Action) => {
    switch (action.type) {
        case "ADDITIONAL_COMPONENTS_FETCHED":
            return { ...state, additionalComponents: action.data };
        case "CONFIGURED_ADDITIONAL_COMPONENTS_FETCHED":
            return { ...state, configuredComponents: action.data };
        default:
            return state;
    }
};

export type CloudDataActions =
    | {
          type: "GET_ADDITIONAL_COMPONENTS";
      }
    | {
          type: "ADDITIONAL_COMPONENTS_FETCHED";
          data: string[];
      }
    | {
          type: "GET_CONFIGURED_ADDITIONAL_COMPONENTS";
      }
    | {
          type: "CONFIGURED_ADDITIONAL_COMPONENTS_FETCHED";
          data: { name: string; type: string }[];
      };

const host = `nu.test.localhost:4000`;
const appId = `staging-tenants-gitlab-adapter`;
const tenantId = `55cf1666-e91e-42cb-80cd-f34f8b08e2b1`;

export function getAdditionalComponents(): ThunkAction {
    return async (dispatch) => {
        dispatch({ type: "GET_ADDITIONAL_COMPONENTS" });

        const { data } = await api.get(`http://tenant-manager-api.staging-cloud.${host}/api/applications/${appId}`);

        dispatch({
            type: "ADDITIONAL_COMPONENTS_FETCHED",
            data: data.plan.allowedEnrichers,
        });
    };
}

export function getConfiguredAdditionalComponents(): ThunkAction {
    return async (dispatch) => {
        dispatch({ type: "GET_CONFIGURED_ADDITIONAL_COMPONENTS" });

        const { data } = await api.get(
            `http://tenant-manager-api.staging-cloud.${host}/api/applications/${appId}/tenants/${tenantId}/enrichers`,
        );

        dispatch({
            type: "CONFIGURED_ADDITIONAL_COMPONENTS_FETCHED",
            data: data.map(({ enricher }) => ({
                name: enricher.name,
                type: enricher.enricher_type,
            })),
        });
    };
}
