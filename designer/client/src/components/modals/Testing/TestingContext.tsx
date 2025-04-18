import type { PropsWithChildren } from "react";
import React, { createContext, useCallback, useContext, useMemo, useState } from "react";

export interface TestingContextState {
    isValid: boolean;
    handleIsValid: (isValid: boolean) => void;
    action: () => void;
    handleSetAction: (action: TestingContextState["action"]) => void;
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

    return useMemo(
        () => ({
            isValid,
            handleIsValid,
            action,
            handleSetAction,
        }),
        [action, handleIsValid, handleSetAction, isValid],
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
