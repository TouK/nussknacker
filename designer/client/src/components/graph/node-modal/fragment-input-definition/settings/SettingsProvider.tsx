import React, { createContext, PropsWithChildren, useContext, useState } from "react";
import { FixedValuesOption, ValueCompileTimeValidation } from "../item";

const SettingsContext = createContext<{
    temporaryUserDefinedList: FixedValuesOption[];
    handleTemporaryUserDefinedList: (listItem: FixedValuesOption[]) => void;
    temporaryValueCompileTimeValidation: ValueCompileTimeValidation;
    handleTemporaryValueCompileTimeValidation: (valueCompileTimeValidation: ValueCompileTimeValidation) => void;
}>(null);

export const SettingsProvider = ({
    initialFixedValuesList,
    initialTemporaryValueCompileTimeValidation,
    children,
}: PropsWithChildren<{
    initialFixedValuesList: FixedValuesOption[];
    initialTemporaryValueCompileTimeValidation: ValueCompileTimeValidation;
}>) => {
    const [temporaryUserDefinedList, setTemporaryUserDefinedList] = useState<FixedValuesOption[]>(initialFixedValuesList || []);
    const [temporaryValueCompileTimeValidation, setTemporaryValueCompileTimeValidation] = useState<ValueCompileTimeValidation>(
        initialTemporaryValueCompileTimeValidation || null,
    );

    const handleTemporaryUserDefinedList = (listItem: FixedValuesOption[]) => {
        setTemporaryUserDefinedList(listItem);
    };

    const handleTemporaryValueCompileTimeValidation = (valueCompileTimeValidation: ValueCompileTimeValidation) => {
        setTemporaryValueCompileTimeValidation(valueCompileTimeValidation);
    };

    return (
        <SettingsContext.Provider
            value={{
                temporaryUserDefinedList,
                handleTemporaryUserDefinedList,
                temporaryValueCompileTimeValidation,
                handleTemporaryValueCompileTimeValidation,
            }}
        >
            {children}
        </SettingsContext.Provider>
    );
};

export const useSettings = () => {
    const settingsContext = useContext(SettingsContext);

    if (!settingsContext) {
        throw new Error(`used outside ${SettingsProvider.name}`);
    }

    return settingsContext;
};
