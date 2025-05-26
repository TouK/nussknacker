import type { PropsWithChildren } from "react";
import React from "react";
import { createContext, useContext } from "react";

import type { TestingContextState } from "../../../modals/Testing/TestingContext";
import { useTestingState } from "../../../modals/Testing/TestingContext";

export const TestingButtonContext = createContext<TestingContextState>(null);

export const TestingButtonProvider = ({ children }: PropsWithChildren<unknown>) => {
    const testingState = useTestingState();

    return <TestingButtonContext.Provider value={testingState}>{children}</TestingButtonContext.Provider>;
};

export const useTestingButtonContext = () => {
    const context = useContext(TestingButtonContext);

    if (!context) {
        throw new Error(`${useTestingButtonContext.name} was used outside of ${TestingButtonContext.displayName} provider`);
    }

    return context;
};
