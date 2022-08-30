export const RootPath = `/`
export const ProcessesTabDataPath = `/processes`
export const ArchiveTabDataPath = `/processes/archived`
export const SubProcessesTabDataPath = `/processes/subprocesses`
export const ProcessesLegacyPaths = [ProcessesTabDataPath, SubProcessesTabDataPath, ArchiveTabDataPath]
export const VisualizationBasePath = `/visualization`
export const VisualizationPath = `${VisualizationBasePath}/:processId`
export const MetricsBasePath = `/metrics`
export const MetricsPath = `${MetricsBasePath}/:processId?`
export const SignalsPath = `/signals`
export const ServicesPath = `/services`
export const CustomTabBasePath = `/customtabs`
export const ScenariosBasePath = `/scenarios`
export const ArchivedPath = `${ScenariosBasePath}/?SHOW_ARCHIVED=true&HIDE_ACTIVE=true`

