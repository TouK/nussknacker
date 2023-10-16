import { mapValues, omitBy, pickBy } from "lodash";
import path from "path";
import { Configuration, container, WatchIgnorePlugin } from "webpack";
import WebpackRemoteTypesPlugin from "webpack-remote-types-plugin";
import extractUrlAndGlobal from "webpack/lib/util/extractUrlAndGlobal";
import { SimpleScriptPlugin } from "./simpleScriptPlugin";
import { hash } from "../../../client/version";

// ModuleFederationPluginOptions is not exported, have to find another way
type MFPOptions = ConstructorParameters<typeof container.ModuleFederationPlugin>[0];

interface ModuleFederationParams extends MFPOptions {
    remotes: Record<string, string>;
}

// language=JavaScript
const getPromiseScript = ([url, module]) => `new Promise((resolve, reject) => {
  const url = "${url}";
  const rootPathSegment = url.split("/").find(s => s.length);
  const module = "${module}"
  let origin;
  
  const parentScriptSrc = Array.from(document.scripts).map(s => s.src).find(s => s.indexOf(rootPathSegment) >= 0);
  if (parentScriptSrc) {
      [origin] = parentScriptSrc.split("/"+rootPathSegment);
  }
  
  if (!origin) {
      // assume last script as current (initiator), "document.currentScript" won't work here.
      const currentScript = document.scripts[document.scripts.length - 1];
      origin = new URL(currentScript.src).origin;
  }
  
  const script = document.createElement("script");
  
  try {
      script.src = new URL(origin + url).href
  } catch (e) {
      throw new Error("Unable to resolve relative path: " + url);
  }

  const errorListener = (event) => {
      if (event.filename === script.src) {
          reject("Unable to parse remote module: " + url);
      }
  };
  
  window.addEventListener("error", errorListener);
  script.onload = () => {
      resolve({
          get: (request) => window[module].get(request),
          init: (arg) => {
              try {
                  return window[module].init(arg);
              } catch (e) {
                  console.log("Remote container already initialized!");
              }
          }
      });
      setTimeout(() => {
          document.head.removeChild(script);
          window.removeEventListener("error", errorListener);
      });
  };

  document.head.appendChild(script);
});`;

function resolveRemotes(
    remotes: ModuleFederationParams["remotes"],
): Record<`${"proxied" | "plain" | "promise"}Remotes`, ModuleFederationParams["remotes"]> {
    const PROXY_PATH_RE = new RegExp(`@${process.env.PROXY_PATH}/?`);
    const NO_HOST_RE = /@\/\w/;
    const withHash = mapValues(remotes, (value) => `${value}?${hash}`);

    // resolve proxy paths to full urls (used before proxy is available)
    const resolvedRemotes = mapValues(withHash, (value) => {
        if (value.match(PROXY_PATH_RE)) {
            return value.replace(PROXY_PATH_RE, `@${process.env.NU_FE_CORE_URL}/static/`);
        }
        return value;
    });

    const plainRemotes = pickBy(resolvedRemotes, (value) => !value.match(NO_HOST_RE));

    // handle remotes with relative paths.
    // it's tricky to resolve right root origin and load script in right way.
    const noHostRemotes = pickBy(resolvedRemotes, (value) => value.match(NO_HOST_RE)); // relative paths
    const promiseRemotes = mapValues(noHostRemotes, (value) => {
        return `promise ${getPromiseScript(extractUrlAndGlobal(value))}`;
    });

    // adding full proxy url to avoid requests relative to unknown root.
    const proxiedRemotes = mapValues(withHash, (value) => {
        if (value.match(PROXY_PATH_RE)) {
            const [url, module] = extractUrlAndGlobal(value);
            return `${module}@http://localhost:${process.env.PORT}${url}`;
        }
        return value;
    });

    return { proxiedRemotes, plainRemotes, promiseRemotes };
}

// TODO: consider creating webpack plugin
export function withModuleFederationPlugins(cfg?: ModuleFederationParams): (wCfg: Configuration) => [Configuration] {
    const { remotes, ...federationConfig }: ModuleFederationParams = {
        // eslint-disable-next-line @typescript-eslint/no-var-requires
        ...require(path.join(process.cwd(), "federation.config.json")),
        ...cfg,
    };
    const { promiseRemotes, proxiedRemotes, plainRemotes } = resolveRemotes(remotes);
    return (webpackConfig) => [
        {
            plugins: [
                new WatchIgnorePlugin({
                    // We ignore packages/(\w|-)+$ because on linux, after changes in .federated-types/* is also changed timestamp of this root directory
                    paths: [/-dts\.tgz$/, /\.federated-types/, /packages\/(\w|-)+$/],
                }),
                new SimpleScriptPlugin([
                    `rm -rf .federated-types/*`,
                    `npx --package=@touk/federated-types make-federated-types --outputDir .federated-types/${federationConfig.name}`,
                    // this .tgz with types for exposed modules lands in public root
                    // and could be downloaded by remote side (e.g. `webpack-remote-types-plugin`).
                    `mkdir -p "${webpackConfig.output.path}"`,
                    `tar -C .federated-types/${federationConfig.name} -czf "${path.join(
                        webpackConfig.output.path,
                        `${federationConfig.name}-dts.tgz`,
                    )}" .`,
                ]),
                new container.ModuleFederationPlugin({
                    filename: "remoteEntry.js",
                    remotes: { ...proxiedRemotes, ...promiseRemotes },
                    ...federationConfig,
                }),
                new WebpackRemoteTypesPlugin({
                    // ignore localhost on CI, it's easier to just copy files there
                    remotes: !process.env.CI ? plainRemotes : omitBy(plainRemotes, (value: string) => value.match(/@http:\/\/localhost/)),
                    outputDir: "../../types/@remote/[name]",
                    remoteFileName: "[name]-dts.tgz",
                }),
            ].filter(Boolean),
        },
    ];
}
