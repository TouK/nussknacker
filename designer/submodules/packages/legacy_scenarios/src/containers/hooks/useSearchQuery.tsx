import * as queryString from "query-string";
import React, { Dispatch, PropsWithChildren, SetStateAction, useContext, useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { defaultArrayFormat, setAndPreserveLocationParams } from "../../common/VisualizationUrl";

const SearchContext = React.createContext(null);

export function SearchContextProvider(props: PropsWithChildren<unknown>) {
    const [, setSearchParams] = useSearchParams();

    const [state, setState] = useState(() =>
        queryString.parse(window.location.search, {
            arrayFormat: defaultArrayFormat,
            parseBooleans: true,
        }),
    );

    const params = useMemo(() => {
        return setAndPreserveLocationParams(state);
    }, [state]);

    useEffect(() => {
        setSearchParams(params);
    }, [params, setSearchParams]);

    const value = useMemo(() => [state, setState], [state]);
    return <SearchContext.Provider {...props} value={value} />;
}

export function useSearchQuery<T>(): [T, Dispatch<SetStateAction<T>>] {
    const context = useContext(SearchContext);
    if (!context) {
        throw "SearchContext not initialized!";
    }
    return context;
}
