import React from "react";
import LoaderSpinner from "./spinner/Spinner";
import { FederatedComponent, FederatedComponentProps, getFederatedComponentLoader } from "@touk/federated-component";
import { NuThemeProvider } from "../containers/theme/nuThemeProvider";
import SystemUtils from "../common/SystemUtils";

export const loadExternalReactModule = getFederatedComponentLoader({ Wrapper: NuThemeProvider });
export const loadExternalReactModuleWithAuth = getFederatedComponentLoader({
    Wrapper: NuThemeProvider,
    getAuthToken: SystemUtils.asyncAuthorizationToken,
});

window["loadExternalReactModule"] = loadExternalReactModule;
window["loadExternalReactModuleWithAuth"] = loadExternalReactModuleWithAuth;

export const RemoteComponent = <P extends NonNullable<unknown>>(props: FederatedComponentProps<P>) => (
    <FederatedComponent<P> {...props} fallback={<LoaderSpinner show={true} />} buildHash={__BUILD_HASH__} />
);
