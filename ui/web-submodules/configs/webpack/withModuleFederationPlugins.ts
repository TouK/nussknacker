import { mapValues, omitBy, pickBy } from "lodash";
import path from "path";
import { Configuration, container, WatchIgnorePlugin } from "webpack";
import WebpackRemoteTypesPlugin from "webpack-remote-types-plugin";
import extractUrlAndGlobal from "webpack/lib/util/extractUrlAndGlobal";
import { SimpleScriptPlugin } from "./simpleScriptPlugin";

// ModuleFederationPluginOptions is not exported, have to find another way
type MFPOptions = ConstructorParameters<typeof container.ModuleFederationPlugin>[0];

interface ModuleFederationParams extends MFPOptions {
    remotes: Record<string, string>;
}

// language=JavaScript
const getPromiseScript = ([url, module]) => `new Promise(resolve => {
    // assume last script as current (initiator), "document.currentScript" won't work here.
    const currentScript = document.scripts[document.scripts.length - 1];
    const remoteUrl = new URL(currentScript.src).origin + "${url}";
    const script = document.createElement("script");
    script.src = remoteUrl;
    script.onload = () => {
        resolve({
            get: (request) => window.${module}.get(request),
            init: (arg) => {
                try {
                    return window.${module}.init(arg);
                } catch (e) {
                    console.log("remote container already initialized");
                }
            }
        });
        setTimeout(() => document.head.removeChild(script))
    };
    document.head.appendChild(script);
})`;

const NO_HOST_RE = /@\/\w/;
const hasFullUrl = (value: string) => !value.match(NO_HOST_RE);
const getPromise = (value: string) => `promise ${getPromiseScript(extractUrlAndGlobal(value))}`;

// TODO: consider creating webpack plugin
export function withModuleFederationPlugins(cfg?: ModuleFederationParams): (wCfg: Configuration) => [Configuration] {
    const { remotes, ...federationConfig }: ModuleFederationParams = {
        // eslint-disable-next-line @typescript-eslint/no-var-requires
        ...require(path.join(process.cwd(), "federation.config.json")),
        ...cfg,
    };
    const plainRemotes = pickBy(remotes, hasFullUrl);
    const noHostRemotes = omitBy(remotes, hasFullUrl);
    const promiseRemotes = mapValues(noHostRemotes, getPromise);
    return (webpackConfig) => [
        {
            plugins: [
                new WatchIgnorePlugin({
                    paths: [/\*-dts\.tgz$/, /\.federated-types/],
                }),
                new SimpleScriptPlugin([
                    `npx make-federated-types --outputDir .federated-types`,
                    // this .tgz with types for exposed modules lands in public root
                    // and could be downloaded by remote side (e.g. `webpack-remote-types-plugin`).
                    `mkdir -p "${webpackConfig.output.path}"`,
                    `tar -C .federated-types -czf "${path.join(webpackConfig.output.path, `${federationConfig.name}-dts.tgz`)}" .`,
                    `rm -rf .federated-types/*`,
                ]),
                new container.ModuleFederationPlugin({
                    filename: "remoteEntry.js",
                    remotes: { ...plainRemotes, ...promiseRemotes },
                    ...federationConfig,
                }),
                new WebpackRemoteTypesPlugin({
                    remotes: plainRemotes,
                    outputDir: "../../types/@remote/[name]",
                    remoteFileName: "[name]-dts.tgz",
                }),
            ].filter(Boolean),
        },
    ];
}
