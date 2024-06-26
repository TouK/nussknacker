import { combineReducers } from "redux";
import { persistReducer } from "redux-persist";
import storage from "redux-persist/lib/storage";
import { Reducer } from "../actions/reduxTypes";
import { Panels, panels } from "./panel";

export enum ToolbarsSide {
    TopRight = "topRight",
    BottomRight = "bottomRight",
    TopLeft = "topLeft",
    BottomLeft = "bottomLeft",
}

type ComponentGroupToolbox = {
    closed: Record<string, boolean>;
};

type InitData = Array<[string, ToolbarsSide]>;

type Positions = {
    [side in ToolbarsSide]?: string[];
};

type Collapsed = Record<string, boolean>;

export type ToolbarsState = {
    positions: Positions;
    initData: InitData;
    collapsed: Collapsed;
    componentGroupToolbox: ComponentGroupToolbox;
    panels: Panels;
};

type Id = `#${string}`;
export type ToolbarsStates = { currentConfigId?: string } & { [id in Id]: ToolbarsState };

const componentGroupToolbox: Reducer<ComponentGroupToolbox> = (state = { closed: {} }, action) => {
    switch (action.type) {
        case "TOGGLE_COMPONENT_GROUP_TOOLBOX":
            return {
                closed: {
                    ...state.closed,
                    [action.componentGroup]: !state.closed[action.componentGroup],
                },
            };

        case "RESET_TOOLBARS":
            return {
                closed: {},
            };

        default:
            return state;
    }
};

function setupPositions(positions: Positions, toolbars: Array<[string, ToolbarsSide]>): Positions {
    const groups = Object.values(positions);
    const newToolbars = toolbars.filter(([id]) => !groups.some((g) => g.includes(id)));
    return newToolbars.reduce((nextState, [id, side]) => {
        const currentValues = nextState[side || ToolbarsSide.TopRight] || [];
        return { ...nextState, [side || ToolbarsSide.TopRight]: [...currentValues, id] };
    }, positions);
}

function insertItemOnTargetPlaceOnList(
    state: Positions,
    fromToolbar: ToolbarsSide | string,
    toToolbar: ToolbarsSide | string,
    fromIndex: number,
    toIndex: number,
): [string[], string[]] {
    const isSameToolbar = fromToolbar === toToolbar;

    const src = [].concat(state[fromToolbar]);
    const [item] = src.splice(fromIndex, 1);

    const dst = isSameToolbar ? src : [].concat(state[toToolbar]);
    dst.splice(toIndex, 0, item);
    return [src, dst];
}

const positions: Reducer<Positions> = (state = {}, action) => {
    switch (action.type) {
        case "MOVE_TOOLBAR": {
            const [fromToolbar, fromIndex] = action.from;
            const [toToolbar, toIndex] = action.to;
            const [src, dst] = insertItemOnTargetPlaceOnList(state, fromToolbar, toToolbar, fromIndex, toIndex);
            return {
                ...state,
                [fromToolbar]: src,
                [toToolbar]: dst,
            };
        }

        case "REGISTER_TOOLBARS":
        case "RESET_TOOLBARS":
            return setupPositions(state, action.toolbars);

        default:
            return state;
    }
};

const collapsed: Reducer<Collapsed> = (state = {}, action) => {
    switch (action.type) {
        case "TOGGLE_ALL_TOOLBARS":
            // eslint-disable-next-line i18next/no-literal-string
            throw "not implemented";
            return state;

        case "TOGGLE_TOOLBAR":
            return { ...state, [action.id]: action.isCollapsed };

        default:
            return state;
    }
};

const initData: Reducer<InitData> = (state = [], action) => {
    return action.type === "REGISTER_TOOLBARS" ? action.toolbars : state;
};

const resetReducer: Reducer<ToolbarsState> = (state, action) => {
    return action.type === "RESET_TOOLBARS" ? { ...state, collapsed: {}, positions: {} } : state;
};

const combinedReducers = combineReducers<ToolbarsState>({
    collapsed,
    positions,
    componentGroupToolbox,
    initData,
    panels,
});

const reducer: Reducer<ToolbarsStates> = (state = {}, action) => {
    switch (action.type) {
        case "REGISTER_TOOLBARS":
        case "RESET_TOOLBARS":
        case "MOVE_TOOLBAR":
        case "TOGGLE_TOOLBAR":
        case "TOGGLE_ALL_TOOLBARS":
        case "TOGGLE_PANEL":
        case "TOGGLE_COMPONENT_GROUP_TOOLBOX": {
            const withReset = resetReducer(state[`#${action.configId}`], action);
            return {
                ...state,
                currentConfigId: action.configId,
                [`#${action.configId}`]: combinedReducers(withReset, action),
            };
        }
        default:
            return state;
    }
};

export const toolbars = persistReducer(
    {
        key: `toolbars`,
        blacklist: [`currentConfigId`],
        storage,
    },
    reducer,
);
