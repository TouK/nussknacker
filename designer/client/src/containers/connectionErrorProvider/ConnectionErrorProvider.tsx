import React, { createContext, FC, PropsWithChildren, useContext, useMemo, useState } from "react";
import { Dialog } from "@mui/material";
import { ConnectionErrorContent } from "./ConnectionErrorContent";
import { CloudOff, WifiOff } from "@mui/icons-material";
import i18next from "i18next";
import { useCancelRequestsIfConnectionProblem } from "./useCancelRequestsIfConnectionProblem";

const ConnectionErrorContext = createContext<{ handleChangeConnectionError: (connectionError: ConnectionError) => void | null }>(null);

export type ConnectionError = "NO_NETWORK_ACCESS" | "NO_BACKEND_ACCESS";

export const ConnectionErrorProvider: FC<PropsWithChildren<unknown>> = ({ children }) => {
    const [connectionError, setConnectionError] = useState<ConnectionError | null>(null);

    const handleChangeConnectionError = (newConnectionError: ConnectionError) => {
        setConnectionError(newConnectionError);
    };

    useCancelRequestsIfConnectionProblem(connectionError);

    const connectionErrorComponent = useMemo(() => {
        switch (connectionError) {
            case "NO_NETWORK_ACCESS": {
                return (
                    <ConnectionErrorContent
                        headerText={i18next.t("connectionError.noNetworkAccess.header", "No network access")}
                        contentText={i18next.t(
                            "connectionError.noNetworkAccess.content",
                            "We're sorry, but it appears that you don't have a network connection at the moment. Please check your network settings.",
                        )}
                        Icon={WifiOff}
                    />
                );
            }
            case "NO_BACKEND_ACCESS": {
                return (
                    <ConnectionErrorContent
                        headerText={i18next.t("connectionError.noBackendAccess.header", "Backend connection issue")}
                        contentText={i18next.t(
                            "connectionError.noBackendAccess.content",
                            "We're experiencing difficulties connecting to the backend server at the moment. We kindly ask you to wait just a few moments while we resolve the issue.",
                        )}
                        Icon={CloudOff}
                    />
                );
            }
            default:
                return null;
        }
    }, [connectionError]);

    return (
        <ConnectionErrorContext.Provider value={{ handleChangeConnectionError }}>
            {connectionErrorComponent && (
                <Dialog sx={(theme) => ({ zIndex: theme.custom.ConnectionErrorModal.zIndex })} maxWidth={"xs"} open={true}>
                    {connectionErrorComponent}
                </Dialog>
            )}
            {children}
        </ConnectionErrorContext.Provider>
    );
};

export const useChangeConnectionError = () => {
    const context = useContext(ConnectionErrorContext);

    if (!context) {
        throw new Error(`${useChangeConnectionError.name} was used outside of its ${ConnectionErrorContext.displayName} provider`);
    }

    return { handleChangeConnectionError: context.handleChangeConnectionError };
};
