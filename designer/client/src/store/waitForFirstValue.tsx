import type { Action, Store, UnknownAction, Unsubscribe } from "redux";

// resolve first truthy value selected from store
export const waitForFirstValue = <S = any, A extends Action = UnknownAction, StateExt = unknown, T = unknown>(
    store: Store<S, A, StateExt>,
    select: (state: S) => T,
) => {
    let unsubscribe: Unsubscribe;
    const promise = new Promise<T>((resolve) => {
        unsubscribe = store.subscribe(() => {
            const value = select(store.getState());
            if (value) {
                resolve(value);
            }
        });
    });
    promise.then(unsubscribe);
    return promise;
};
