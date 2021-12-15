import { Action } from "history";
import { isEqual } from "lodash";
import React, { PropsWithChildren, useContext, useEffect, useState } from "react";
import { Location, useLocation, useNavigationType } from "react-router-dom";

const HistoryContext = React.createContext<Location[]>([]);

export function HistoryProvider({ children }: PropsWithChildren<unknown>): JSX.Element {
    const location = useLocation();
    const type = useNavigationType();
    const [locations, storeLocations] = useState([location]);
    useEffect(() => {
        switch (type) {
            case Action.Push:
                storeLocations((l) => [...l, location]);
                break;
            case Action.Pop:
                storeLocations((l = []) => {
                    return [...l.slice(0, l.length - 2), location];
                });
                break;
            case Action.Replace:
                storeLocations((l) => {
                    l.pop();
                    return [...l, location];
                });
                break;
        }
    }, [location, type]);
    return <HistoryContext.Provider value={locations}>{children}</HistoryContext.Provider>;
}

export function useHistory(): Location[] {
    const context = useContext(HistoryContext);
    if (!context) {
        throw "HistoryContext not initialized!";
    }
    return context;
}

export function useBackHref(fallback: Partial<Location> = { pathname: "/" }): Location {
    const history = useHistory();
    const location = useLocation();
    const back = history[history.length - 2] || history[0];
    return isEqual(location, back) ? { ...location, search: null, hash: null, ...fallback } : back;
}
