import React, { createContext, FC, PropsWithChildren, useContext, useState } from "react";
import { Dialog } from "@mui/material";
import { ConnectionErrorContent } from "./ConnectionErrorContent";
import { CloudOff, Update, WifiOff } from "@mui/icons-material";

const ConnectionErrorContext = createContext<{ handleChangeConnectionError: (connectionError: ConnectionError) => void | null }>(null);

type ConnectionError = "NO_NETWORK_ACCESS" | "NO_BACKEND_ACCESS" | "SOFTWARE_UPDATE";

export const ConnectionErrorProvider: FC<PropsWithChildren<unknown>> = ({ children }) => {
    const [connectionError, setConnectionError] = useState<ConnectionError | null>(null);

    const handleChangeConnectionError = (newConnectionError: ConnectionError) => {
        setConnectionError(newConnectionError);
    };

    const connectionErrorComponent = () => {
        switch (connectionError) {
            case "NO_NETWORK_ACCESS": {
                return (
                    <ConnectionErrorContent
                        headerText={"No internet access"}
                        contentText={
                            "We're sorry, but it appears that you don't have a network connection at the moment. Please check your network settings."
                        }
                        Icon={WifiOff}
                    />
                );
            }
            case "NO_BACKEND_ACCESS": {
                return (
                    <ConnectionErrorContent
                        headerText={"Backend connection issue"}
                        contentText={
                            "We're experiencing difficulties connecting to the backend server at the moment. We kindly ask you to wait just a few moments while. we resolve the issue."
                        }
                        Icon={CloudOff}
                    />
                );
            }
            case "SOFTWARE_UPDATE": {
                return (
                    <ConnectionErrorContent
                        headerText={"Software update"}
                        contentText={"Automatic software update is processing. The application should be available soon."}
                        Icon={Update}
                    />
                );
            }
            default:
                return null;
        }
    };
    return (
        <ConnectionErrorContext.Provider value={{ handleChangeConnectionError }}>
            {connectionErrorComponent && (
                <Dialog maxWidth={"xs"} open={true}>
                    {connectionErrorComponent()}
                </Dialog>
            )}
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
