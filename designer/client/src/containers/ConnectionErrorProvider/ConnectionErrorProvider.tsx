import React, { createContext, FC, PropsWithChildren, useContext, useState } from "react";
import { Dialog } from "@mui/material";

const ConnectionErrorContext = createContext<{ handleChangeConnectionError: (connectionError: ConnectionError) => void | null }>(null);

type ConnectionError = "NO_INTERNET_ACCESS" | "NO_BACKEND_ACCESS" | "SOFTWARE_UPDATE";

export const ConnectionErrorProvider: FC<PropsWithChildren<unknown>> = ({ children }) => {
    const [connectionError, setConnectionError] = useState<ConnectionError | null>(null);

    const handleChangeConnectionError = (newConnectionError: ConnectionError) => {
        setConnectionError(newConnectionError);
    };

    const connectionErrorComponent = () => {
        switch (connectionError) {
            case "NO_INTERNET_ACCESS": {
                return <div>NO_INTERNET_ACCESS</div>;
            }
            case "NO_BACKEND_ACCESS": {
                return <div>NO_BACKEND_ACCESS</div>;
            }
            case "SOFTWARE_UPDATE": {
                return <div>SOFTWARE_UPDATE</div>;
            }
            default:
                return null;
        }
    };
    return (
        <ConnectionErrorContext.Provider value={{ handleChangeConnectionError }}>
            {connectionErrorComponent && <Dialog open={true}>{connectionErrorComponent()}</Dialog>}
            {children}
        </ConnectionErrorContext.Provider>
    );
};

export const useChangeConnectionError = () => {
    const context = useContext(ConnectionErrorContext);

    if (!context) {
        throw new Error(`${useChangeConnectionError.name} was used outside of its ${ConnectionErrorContext.displayName} providerł`);
    }

    return { handleChangeConnectionError: context.handleChangeConnectionError };
};
