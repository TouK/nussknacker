import * as queryString from "query-string";
import React, { PropsWithChildren, useContext, useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { defaultArrayFormat, setAndPreserveLocationParams } from "../../common/VisualizationUrl";

const SearchContext = React.createContext(null);

export function SearchContextProvider(props: PropsWithChildren<unknown>) {
    const [, setSearchParams] = useSearchParams();

    const value = useState(() =>
        queryString.parse(window.location.search, {
            arrayFormat: defaultArrayFormat,
            parseBooleans: true,
            parseNumbers: true,
        }),
    );

    const [state] = value;
    useEffect(() => {
        setSearchParams(setAndPreserveLocationParams(state), { replace: true });
    }, [setSearchParams, state]);

    return <SearchContext.Provider {...props} value={value} />;
}

export function useSearchQuery<T>(options): [T, (value: ((prevState: T) => T) | T) => void] {
    const context = useContext(SearchContext);
    if (!context) {
        throw "SearchContext not initialized!";
    }
    return context;
}
