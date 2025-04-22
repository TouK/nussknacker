import type { PropsWithChildren } from "react";
import React, { createContext, useCallback, useContext, useMemo, useState } from "react";

import type { TestingOption, TestType } from "./useTestOptions";
import { useTestOptions } from "./useTestOptions";

export interface TestingContextState {
    isValid: boolean;
    handleIsValid: (isValid: boolean) => void;
    action: () => void;
    handleSetAction: (action: TestingContextState["action"]) => void;
    options: TestingOption[];
    testType: TestType;
}

/**
 * Since we need to pass buttons to WindowContent and specific testing type component is rendered as a children to isolate logic sake
 * The child component sets the button action and state, which are then passed to the WindowContent buttons
 */

export const TestingContext = createContext<TestingContextState>(null);

export function useTestingState(): TestingContextState {
    const [isValid, setIsValid] = useState<boolean>(false);
    const [action, setAction] = useState<TestingContextState["action"]>();

    const handleIsValid = useCallback((isValid: boolean) => {
        setIsValid(isValid);
    }, []);

    const handleSetAction = useCallback((action: TestingContextState["action"]) => {
        setAction(() => action);
    }, []);

    const { options, testType } = useTestOptions();

    return useMemo(
        () => ({
            isValid,
            handleIsValid,
            action,
            handleSetAction,
            options,
            testType,
        }),
        [action, handleIsValid, handleSetAction, isValid, options, testType],
    );
}

export const TestingProvider = ({ children }: PropsWithChildren) => {
    const context = useTestingState();
    return <TestingContext.Provider value={context}>{children}</TestingContext.Provider>;
};

export const useTestingContext = () => {
    const context = useContext(TestingContext);

    if (!context) {
        throw new Error(`${useTestingContext.name} was used outside of its ${TestingContext.displayName} provider`);
    }

    return context;
};
