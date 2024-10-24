import React, { useContext } from "react";
import { createContext, PropsWithChildren, useState } from "react";

interface Props {
    isActivityHovered: boolean;
    handleSetIsActivityHovered: (isHovered: boolean) => void;
}

const ActivityItemContext = createContext<Props>(null);

export const ActivityItemProvider = ({ children }: PropsWithChildren) => {
    const [isActivityHovered, setIsActivityHovered] = useState<boolean>();

    const handleSetIsActivityHovered = (isHovered: boolean) => {
        setIsActivityHovered(isHovered);
    };

    return (
        <ActivityItemContext.Provider value={{ isActivityHovered, handleSetIsActivityHovered }}>{children}</ActivityItemContext.Provider>
    );
};

export const useActivityItemInfo = () => {
    const context = useContext(ActivityItemContext);

    if (!context) {
        throw new Error(`${useActivityItemInfo.name} was used outside of its ${ActivityItemContext.displayName} provider`);
    }

    return context;
};
