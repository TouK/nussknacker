import React, { createContext, PropsWithChildren, useContext, useState } from "react";
import { FixedValuesOption } from "../item";

const SettingsContext = createContext<{
    temporaryUserDefinedList: FixedValuesOption[];
    handleTemporaryUserDefinedList: (listItem: FixedValuesOption[]) => void;
}>(null);

export const SettingsProvider = ({
    initialFixedValuesList,
    children,
}: PropsWithChildren<{
    initialFixedValuesList: FixedValuesOption[];
}>) => {
    const [temporaryUserDefinedList, setTemporaryUserDefinedList] = useState<FixedValuesOption[]>(initialFixedValuesList || []);

    const handleTemporaryUserDefinedList = (listItem: FixedValuesOption[]) => {
        setTemporaryUserDefinedList(listItem);
    };

    return (
        <SettingsContext.Provider
            value={{
                temporaryUserDefinedList,
                handleTemporaryUserDefinedList,
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
