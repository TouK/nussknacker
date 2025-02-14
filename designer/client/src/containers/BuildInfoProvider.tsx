import React, { createContext, PropsWithChildren, useContext, useEffect, useState } from "react";
import HttpService, { AppBuildInfo } from "../http/HttpService";
import LoaderSpinner from "../components/spinner/Spinner";

const BuildInfoContext = createContext<AppBuildInfo>(null);

export const BuildInfoProvider = ({ children }: PropsWithChildren) => {
    const [buildInfo, setBuildInfo] = useState<AppBuildInfo>();

    useEffect(() => {
        HttpService.fetchAppBuildInfo().then((res) => setBuildInfo(res.data));
    }, []);

    return buildInfo ? <BuildInfoContext.Provider value={buildInfo}>{children}</BuildInfoContext.Provider> : <LoaderSpinner show />;
};

export const useBuildInfo = () => {
    const context = useContext(BuildInfoContext);

    if (!context) {
        throw new Error(`${useBuildInfo.name} was used outside of its ${BuildInfoContext.displayName} provider`);
    }

    return context;
};
