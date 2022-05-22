export function getUserSetting(key: string): boolean {
    const settings = JSON.parse(
        localStorage.getItem("persist:settings"),
        (key, value) => value === "true" || (value === "false" ? false : value),
    );
    return settings[key];
}
