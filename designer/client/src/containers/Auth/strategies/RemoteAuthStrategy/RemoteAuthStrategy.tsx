import { ModuleUrl } from "@touk/federated-component";
import React, { FunctionComponent, PropsWithChildren } from "react";
import { PendingPromise } from "../../../../common/PendingPromise";
import SystemUtils from "../../../../common/SystemUtils";
import { ErrorBoundary } from "../../../../components/common/error-boundary";
import { PlainRemoteComponent } from "../../../../components/RemoteComponent";
import { RemoteAuthenticationSettings } from "../../../../reducers/settings";
import { AuthErrorCodes } from "../../AuthErrorCodes";
import { Strategy, StrategyConstructor } from "../../Strategy";
import { AuthClient } from "./externalAuthModule";

type AuthLibCallback = (a: AuthClient) => void;
type RemoteAuthProviderProps = PropsWithChildren<{
    onInit: AuthLibCallback;
}>;

function createAuthWrapper(url: ModuleUrl, onInit: AuthLibCallback): FunctionComponent {
    return function Wrapper({ children }: PropsWithChildren<unknown>) {
        return (
            <ErrorBoundary>
                <PlainRemoteComponent<RemoteAuthProviderProps> url={url} onInit={onInit}>
                    {children}
                </PlainRemoteComponent>
            </ErrorBoundary>
        );
    };
}

export const RemoteAuthStrategy: StrategyConstructor = class RemoteAuthStrategy implements Strategy {
    private pendingClient = PendingPromise.withTimeout<AuthClient>();
    Wrapper = createAuthWrapper(this.urlWithScope, (auth) => this.pendingClient.resolve(auth));

    async inteceptor(error?: {
        response?: {
            status?: AuthErrorCodes;
        };
    }): Promise<unknown> {
        if (error?.response?.status === AuthErrorCodes.HTTP_UNAUTHORIZED_CODE) {
            await this.handleAuth();
        }
        return;
    }

    async handleAuth(): Promise<void> {
        const auth = await this.pendingClient;
        if (!auth.isAuthenticated) {
            auth.login();
        }
        const token = await auth.getToken();
        SystemUtils.setAuthorizationToken(token);
    }

    setOnErrorCallback(callback: (error: AuthErrorCodes) => void): void {
        this.onError = callback;
    }

    constructor(private settings: RemoteAuthenticationSettings) {}

    private get urlWithScope(): ModuleUrl {
        return this.settings.moduleUrl as ModuleUrl;
    }

    private onError?: (error: AuthErrorCodes) => void = () => {
        return;
    };
};
