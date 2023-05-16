import React, { FunctionComponent, PropsWithChildren } from "react";
import { PendingPromise } from "../../../../common/PendingPromise";
import SystemUtils from "../../../../common/SystemUtils";
import ErrorBoundary from "../../../../components/common/ErrorBoundary";
import { RemoteAuthenticationSettings } from "../../../../reducers/settings";
import { splitUrl } from "../../../ExternalLib";
import { ModuleString, ModuleUrl } from "../../../ExternalLib/types";
import { AuthErrorCodes } from "../../AuthErrorCodes";
import { Strategy, StrategyConstructor } from "../../Strategy";
import { AuthClient } from "./externalAuthModule";
import { RemoteComponent } from "../../../ExternalLib/RemoteComponent";

type AuthLibCallback = (a: AuthClient) => void;

function createAuthWrapper(
    { url, scope }: { url: ModuleUrl; scope: ModuleString },
    onInit: AuthLibCallback
): FunctionComponent {
    return function Wrapper({ children }: PropsWithChildren<unknown>) {
        return (
            <ErrorBoundary>
                <RemoteComponent url={url} scope={scope} onInit={onInit}>
                    {children}
                </RemoteComponent>
            </ErrorBoundary>
        );
    };
}

export const RemoteAuthStrategy: StrategyConstructor = class RemoteAuthStrategy implements Strategy {
    private pendingClient = new PendingPromise<AuthClient>();
    Wrapper = createAuthWrapper(this.urlWithScope, (auth) => this.pendingClient.resolve(auth));

    async inteceptor(error?: { response?: { status?: AuthErrorCodes } }): Promise<unknown> {
        if (error?.response?.status === AuthErrorCodes.HTTP_UNAUTHORIZED_CODE) {
            await this.handleAuth();
        }
        return;
    }

    async handleAuth(): Promise<void> {
        const auth = await this.pendingClient.promise;
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

    private get urlWithScope(): { scope: ModuleString; url: ModuleUrl } {
        const [url, scope] = splitUrl(this.settings.moduleUrl as ModuleUrl);
        return { url, scope };
    }

    private onError?: (error: AuthErrorCodes) => void = () => {
        return;
    };
};
