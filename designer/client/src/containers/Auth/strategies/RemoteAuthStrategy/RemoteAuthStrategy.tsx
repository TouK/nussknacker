import type { ModuleUrl } from "@touk/federated-component";
import type { FunctionComponent, PropsWithChildren } from "react";
import React from "react";

import { PendingPromise } from "../../../../common/PendingPromise";
import SystemUtils from "../../../../common/SystemUtils";
import { ErrorBoundary } from "../../../../components/common/error-boundary/ErrorBoundary";
import { PlainRemoteComponent } from "../../../../components/RemoteComponent";
import type { RemoteAuthenticationSettings } from "../../../../reducers/settings";
import { AuthErrorCodes } from "../../AuthErrorCodes";
import type { Strategy, StrategyConstructor } from "../../Strategy";
import type { AuthClient } from "./externalAuthModule";

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
    private _wrapper?: FunctionComponent;

    get Wrapper(): FunctionComponent {
        if (!this._wrapper) {
            this._wrapper = createAuthWrapper(this.urlWithScope, (auth) => this.pendingClient.resolve(auth));
        }
        return this._wrapper;
    }

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
