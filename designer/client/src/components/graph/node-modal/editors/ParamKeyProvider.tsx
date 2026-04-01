import type { PropsWithChildren } from "react";
import React, { useContext, useMemo } from "react";

import { isDev } from "../../../../devHelpers";
import type { NodeType, Parameter } from "../../../../types/node";
import type { ParamKeys } from "../parameterHelpers";
import { determineParameterKey } from "../parameterHelpers";

const ParamKey = React.createContext<ParamKeys>(null);

export const ParamKeyProvider = ({
    node,
    param,
    custom,
    children,
}: PropsWithChildren<{ node?: NodeType; param?: Parameter; custom?: ParamKeys }>) => {
    const value = useMemo(() => {
        return custom ?? determineParameterKey(node, param);
    }, [custom, node, param]);

    return (
        <ParamKey.Provider value={value}>
            {isDev ? <span data-debug={value} /> : null}
            {children}
        </ParamKey.Provider>
    );
};

export const useParamKey = () => {
    return useContext(ParamKey);
};
