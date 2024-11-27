import React, { useMemo } from "react";
import LoaderSpinner from "./spinner/Spinner";
import { FederatedComponent, FederatedComponentProps, getFederatedComponentLoader } from "@touk/federated-component";
import { NuThemeProvider } from "../containers/theme/nuThemeProvider";
import SystemUtils from "../common/SystemUtils";
import { useWindows, WindowKind } from "../windowManager";

export const loadExternalReactModule = getFederatedComponentLoader({ Wrapper: NuThemeProvider });
export const loadExternalReactModuleWithAuth = getFederatedComponentLoader({
    Wrapper: NuThemeProvider,
    getAuthToken: SystemUtils.asyncAuthorizationToken,
});

window["loadExternalReactModule"] = loadExternalReactModule;
window["loadExternalReactModuleWithAuth"] = loadExternalReactModuleWithAuth;

export type RemoteToolbarContentProps = {
    openRemoteModuleWindow: <P extends NonNullable<unknown>>(props: P & { url?: string; title?: string }) => void;
};

function RemoteComponentRender<P extends NonNullable<unknown>, T = unknown>(props: FederatedComponentProps<P>, ref: React.ForwardedRef<T>) {
    const { open } = useWindows();
    const sharedContext = useMemo<RemoteToolbarContentProps>(
        () => ({
            openRemoteModuleWindow: ({ title, ...props }) =>
                open({
                    kind: WindowKind.remote,
                    title,
                    meta: props,
                }),
        }),
        [open],
    );

    return (
        <FederatedComponent<P, T>
            ref={ref}
            {...sharedContext}
            {...props}
            fallback={<LoaderSpinner show={true} />}
            buildHash={__BUILD_HASH__}
        />
    );
}

export const RemoteComponent = React.forwardRef(RemoteComponentRender) as <P extends NonNullable<unknown>, T = unknown>(
    props: FederatedComponentProps<P> & React.RefAttributes<T>,
) => React.ReactElement;
