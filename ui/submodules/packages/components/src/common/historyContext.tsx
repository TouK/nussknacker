import { Action } from "history";
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
