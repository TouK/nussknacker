import React from "react";
import LoaderSpinner from "./spinner/Spinner";
import * as LibLoader from "../ExternalLib";
import { NuThemeProvider } from "../containers/theme/nuThemeProvider";
import SystemUtils from "../common/SystemUtils";

export const loadExternalReactModule = LibLoader.getExternalReactModuleLoader(NuThemeProvider);
export const loadExternalReactModuleWithAuth = LibLoader.getExternalReactModuleLoader(NuThemeProvider, SystemUtils.asyncAuthorizationToken);

window["loadExternalReactModule"] = loadExternalReactModule;
window["loadExternalReactModuleWithAuth"] = loadExternalReactModuleWithAuth;

export const RemoteComponent = <P extends NonNullable<unknown>>(props: LibLoader.RemoteComponentProps<P>) => (
    <LibLoader.RemoteComponent<P> {...props} fallback={<LoaderSpinner show={true} />} buildHash={__BUILD_HASH__} />
);
