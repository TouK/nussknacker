import type { ListenerEffectAPI } from "@reduxjs/toolkit";
import { addListener } from "@reduxjs/toolkit";
import { useDispatch, useSelector } from "react-redux";

import type { setupStore } from "./configureStore";

export type AppDispatch = ReturnType<typeof setupStore>["store"]["dispatch"];
export type AppState = ReturnType<ReturnType<typeof setupStore>["store"]["getState"]>;

export const useAppDispatch = useDispatch.withTypes<AppDispatch>();
export const useAppSelector = useSelector.withTypes<AppState>();

type ExtractActionTypes<T> = T extends (action: infer A) => any ? A : never;
export type AppAction = ExtractActionTypes<AppDispatch>;
export type ActionOfType<T extends AppAction["type"]> = Extract<AppAction, { type: T }>;

function once<T extends AppAction["type"]>(
    effect: (action: ActionOfType<T>, api: ListenerEffectAPI<AppState, AppDispatch>) => void | Promise<void>,
) {
    return async (action, api) => {
        api.unsubscribe();
        return await effect(action as ActionOfType<T>, api as ListenerEffectAPI<AppState, AppDispatch>);
    };
}

export const _addListenerTyped = addListener.withTypes<AppState, AppDispatch>();

export const addListenerTyped = <T extends AppAction["type"]>(
    type: T,
    effect: (action: Extract<AppAction, { type: T }>, api: ListenerEffectAPI<AppState, AppDispatch>) => void | Promise<void>,
) => _addListenerTyped({ type, effect });

export const addOnceListenerTyped = <T extends AppAction["type"]>(
    type: T,
    effect: (action: Extract<AppAction, { type: T }>, api: ListenerEffectAPI<AppState, AppDispatch>) => void | Promise<void>,
) =>
    addListener({
        type,
        effect: once(effect),
    });
