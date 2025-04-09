import type { PropsWithChildren } from "react";
import { useCallback } from "react";
import React, { createContext, useContext, useState } from "react";

interface TestingContextProps {
    isValid: boolean;
    handleIsValid: (isValid: boolean) => void;
    action: () => void;
    handleSetAction: (action: TestingContextProps["action"]) => void;
}

const TestingContext = createContext<TestingContextProps>(null);

export const TestingProvider = ({ children }: PropsWithChildren) => {
    const [isValid, setIsValid] = useState<boolean>(false);
    const [action, setAction] = useState<TestingContextProps["action"]>();

    const handleIsValid = useCallback((isValid: boolean) => {
        setIsValid(isValid);
    }, []);

    const handleSetAction = useCallback((action: TestingContextProps["action"]) => {
        setAction(() => action);
    }, []);

    return <TestingContext.Provider value={{ isValid, handleIsValid, action, handleSetAction }}>{children}</TestingContext.Provider>;
};

export const useTesting = () => {
    const context = useContext(TestingContext);

    if (!context) {
        throw new Error(`${useTesting.name} was used outside of its ${TestingContext.displayName} provider`);
    }

    return context;
};
