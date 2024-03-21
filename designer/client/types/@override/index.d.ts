declare const __DEV__: boolean;
declare const __BUILD_VERSION__: string;
declare const __BUILD_HASH__: string;

declare let __webpack_init_sharing__: (name: string) => unknown;
declare let __webpack_share_scopes__: {
    [name: string]: unknown;
    default: unknown;
};

declare module "*.css" {
    const classes: { [key: string]: string };
    export default classes;
}

declare module "*.svg" {
    const ReactComponent: React.ComponentType<React.SVGProps<SVGSVGElement>>;
    export default ReactComponent;
}

declare module "!raw-loader!*" {
    const content: string;
    export default content;
}
