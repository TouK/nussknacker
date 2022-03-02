---
sidebar_position: 4
---
# Designer configuration

## Web interface configuration

| Parameter name                              | Importance | Type     | Default value | Description                                                                                                                                                                    |
| --------------                              | ---------- | ----     | ------------- | -----------                                                                                                                                                                    |
| http.port                                   | High       | int      | 8080          | HTTP Port of Designer app                                                                                                                                                      |
| http.interface                              | High       | string   | "0.0.0.0"     | HTTP interface for Designer app                                                                                                                                                |
| http.publicPath                             | Medium     | string   | ""            | if Designer used with reverse proxy and custom path, use this configuration to generate links in Designer properly (Designer app is always served from root path)              |
| ssl.enabled                                 | Medium     | boolean  | false         | Should Designer app be served with SSL                                                                                                                                         |
| ssl.keyStore.location                       | Medium     | string   |               | Keystore file location (required if SSL enabled)                                                                                                                               |
| ssl.keyStore.password                       | Medium     | string   |               | Keystore file password (required if SSL enabled)                                                                                                                               |
| akka.*                                      | Medium     |          |               | [Akka HTTP](https://doc.akka.io/docs/akka-http/current/index.html) is used for HTTP serving, you can configure it in standard way. Below we give Nussknacker-specific defaults |
| akka.http.server.parsing.max-content-length | Low        | int      | 300000000     | Requests (e.g. with test data) can be quite large, so we increase the limit                                                                                                    |
| akka.http.server.request-timeout            | Low        | duration | 1 minute      | Consider increasing the value if you have large test data or long savepoint times during deploy                                                                                |

## Database configuration

Currently, Nussknacker supports following databases:

* HSQL (embedded), we use `syntax_ora` option
* PostgreSQL

Please
check [Slick documentation](https://scala-slick.org/doc/3.1.0/api/index.html#slick.jdbc.JdbcBackend$DatabaseFactoryDef@forConfig(String,Config,Driver,ClassLoader):Database)
for detailed list of configuration options.

The table below presents most important options, or the ones that have Nussknacker specific defaults.

| Parameter name       | Importance | Type   | Default value                                             | Description                                                                                 |
| --------------       | ---------- | ----   | -------------                                             | -----------                                                                                 |
| db.url               | High       | string | "jdbc:hsqldb:file:"${storageDir}"/db;sql.syntax_ora=true" | Default HSQL location                                                                       |
| db.driver            | High       | string | "org.hsqldb.jdbc.JDBCDriver"                              |                                                                                             |
| db.user              | High       | string | "SA"                                                      |                                                                                             |
| db.password          | High       | string | ""                                                        |                                                                                             |
| db.connectionTimeout | Low        | int    | 30000                                                     |                                                                                             |
| db.maximumPoolSize   | Low        | int    | 5                                                         | We have lower limits than default config, since then Designer is not heavy-load application |
| db.minimumIdle       | Low        | int    | 1                                                         | We have lower limits than default config, since then Designer is not heavy-load application |
| db.numThreads        | Low        | int    | 5                                                         | We have lower limits than default config, since then Designer is not heavy-load application |

## Metrics settings
                                                                     
### Metric dashboard

Each scenario can have a link to grafana dashboard. In [docker setup](https://github.com/TouK/nussknacker-quickstart/tree/main/docker/grafana) we
provide `nussknacker-scenario` dashboard. 
You can modify/configure own, the only assumption that we make is that [variable](https://grafana.com/docs/grafana/latest/variables/) `scenarioName` is used to display metrics for particular scenario.

Each scenario type can have different dashboard, this is configured by 
`metricsSettings.scenarioTypeToDashboard` settings. If no mapping is configured, `metricsSettings.defaultDashboard` is used.
Actual link for particular scenario is created by replacing 
- `$dashboard` with configured dashboard
- `$scenarioName` with scenario name
in `metricsSettings.url` setting.

| Parameter name                          | Importance | Type   | Default value                                                                                      | Description                                                                                                                    |
| --------------                          | ---------- | ----   | -------------                                                                                      | -----------                                                                                                                    |
| metricsSettings.url                     | High       | string | `/grafana/d/$dashboard?theme=dark&var-scenarioName=$scenarioName&var-env=local` (for docker setup) | URL (accessible from user browser, in docker setup its configured as relative URL) to Grafana dashboard, see above for details |
| metricsSettings.defaultDashboard        | Medium     | string | nussknacker-scenario (for docker setup)                                                            | Default dashboard                                                                                                              |
| metricsSettings.scenarioTypeToDashboard | Low        | map    |                                                                                                    | Mapping of scenario types to dashboard                                                                                         |


### Counts                                                 

Counts are based on InfluxDB metrics, stored in ```nodeCount``` measurement by default.
```countsSettings.queryMode``` setting can be used to choose metric computation algorithm:
- `OnlySingleDifference` - subtracts values between end and beginning of requested range. Fast method, but if restart
  in the requested time range is detected error is returned. We assume the job was restarted when event counter at the source 
  decreases.
- `OnlySumOfDifferences` - difference is computed by summing differences in measurements for requested time range. 
  This method works a bit better for restart situations, but can be slow for large diagrams and wide time ranges.
- `SumOfDifferencesForRestarts` - if restart is detected, the metrics are computed with `OnlySumDifferences`, otherwise - with `OnlySingleDifferences`
       
If you have custom metrics settings which result in different fields or tags (e.g. you have different telegraf configuration), you can configure required values
with the settings presented below:

| Parameter name                                     | Importance | Type                                                                      | Default value               | Description                                                                                                                                   |
|----------------------------------------------------| ---------- |---------------------------------------------------------------------------|-----------------------------| -----------                                                                                                                                   |
| countsSettings.influxUrl                           | Medium     | string                                                                    |                             | Main InfluxDB query endpoint (e.g. http://influx:8086/query). It should be accessible from Nussknacker Designer server, not from user browser |
| countsSettings.database                            | Medium     | string                                                                    |                             |                                                                                                                                               |
| countsSettings.user                                | Medium     | string                                                                    |                             |                                                                                                                                               |
| countsSettings.password                            | Medium     | string                                                                    |                             |                                                                                                                                               |
| countsSettings.queryMode                           | Low        | OnlySingleDifference / OnlySumOfDifferences / SumOfDifferencesForRestarts | SumOfDifferencesForRestarts |                                                                                                                                               |
| countsSettings.metricsConfig.sourceCountMetric     | Low        | string                                                                    | source_count                |                                                                                                                                               |
| countsSettings.metricsConfig.nodeCountMetric       | Low        | string                                                                    | nodeCount                   |                                                                                                                                               |
| countsSettings.metricsConfig.nodeIdTag             | Low        | string                                                                    | nodeId                      |                                                                                                                                               |
| countsSettings.metricsConfig.additionalGroupByTags | Low        | list of string                                                            | ["slot", "instanceId"]      |                                                                                                                                               |
| countsSettings.metricsConfig.scenarioTag           | Low        | string                                                                    | scenario                    |                                                                                                                                               |
| countsSettings.metricsConfig.countField            | Low        | string                                                                    | count                       |                                                                                                                                               |
| countsSettings.metricsConfig.envTag                | Low        | string                                                                    | env                         |                                                                                                                                               |

## Deployment settings

Nussknacker Designer can be configured to replace certain values in comments to links that can point e.g. to external issue tracker like
GitHub issues or Jira. For example, `MARKETING-555` will change to link `https://jira.organization.com/jira/browse/MARKETING-555`.
See [development configuration](https://github.com/TouK/nussknacker/blob/staging/nussknacker-dist/src/universal/conf/dev-application.conf#L104) for example configuration.                                 


| Parameter name                  | Importance | Type    | Default value | Description                                                                                                                           |
| --------------                  | ---------- | ----    | ------------- | -----------                                                                                                                           |
| commentSettings.matchExpression | Low        | regexp  |               | Regular expression to look for issue identifier (e.g. `(issues/[0-9]*)` - note use of regexp group)                                   |
| commentSettings.link            | Low        | string  |               | Link template (e.g. `https://github.com/TouK/nussknacker/$1` - `$1` will be replaced with matched group from `matchExpression` config |
| deploySettings.requireComment   | Low        | boolean | false         | If true, comment is required for deployment. Also, if `matchExpression` is defined, at least one match is required                    |

## Security

Nussknacker has pluggable security architecture - by default we support two type of authentication: BasicAuth and
OAuth2. You can either use default authentication provider, based on Basic authentication and static user configuration
or integrate with other authentication mechanisms such as custom SSO implementation.

### Users and permissions

Each user has id and set of permissions for every scenario category. There are following permissions:

* Read - user can view scenarios in category
* Write - user can modify/add new scenarios in category
* Deploy - user can deploy or cancel scenarios in given category

You can set a role assigned to an anonymous user with the `anonymousUserRole` setting in the `authentication` section in the configuration.
When no value is provided (default), no anonymous access will be granted.

### Global permissions

In addition to permission system oriented around scenarios categories we provide additional set of permissions. This
feature is designed to control access to components that have no category attached, or it doesn't make sense for them to
have one.

Currently supported permissions:

* AdminTab - shows Admin tab in the UI (right now there are some useful things kept there including search components
  functionality).

### Configuration parameters 

| Parameter name                   | Importance | Type        | Default value | Description                                                                                                                                                        |
| ---                              | ---        | ---         | ---           | ---                                                                                                                                                                |
| authentication.method            | required   | string      |               | `BasicAuth`, `Oidc` or `OAuth2`                                                                                                                                    |
| authentication.usersFile         | required   | url or path |               | URL or path to a file with a mapping of user identities to roles and roles to permissions                                                                          |
| authentication.anonymousUserRole | optional   | string      |               | Role assigned to an unauthenticated user if the selected authentication provider permits anonymous access. No anonymous access allowed unless a value is provided. |

#### Users' file format:

```hocon
users: [
  {
    identity: "admin"
    roles: ["Admin"]
  },
  {
    identity: "userWithAdminTab"
    roles: ["User", "UserWithAdminTab"]
  }
  {
    identity: "user"
    roles: ["User"]
  }
]

rules: [
  {
    role: "Admin"
    isAdmin: true,
    categories: ["RequestResponseCategory1"]
  },
  {
    role: "UserWithAdminTab"
    permissions: ["Read", "Write", "Deploy"]
    globalPermissions: ["AdminTab"]
    categories: ["Category2", "RequestResponseCategory1"]
  },
  {
    role: "User"
    permissions: ["Read", "Write"]
    categories: ["Category1", "Category2"]
  }
]
```

### BasicAuth security module

In order to use Basic authentication you just need to set  `authentication.method` to `BasicAuth` and 
provide either plain or encrypted passwords for users additionally to the `usersFile`'s content as follows:  

```hocon
users: [
  {
    ...
    password: "pass"
  },
  {
    ...
    encryptedPassword: "$2a$12$oA3U7DXkT5eFkyB8GbtKzuVqxUCU0zDmcueBYV218zO/JFQ9/bzY6"    
  }
]

...
```

#### Encrypted hashes

Encryption of passwords uses BCrypt algorithm. You can generate sample hash using command: 

```shell
python -c 'import bcrypt; print(bcrypt.hashpw("password".encode("utf8"), bcrypt.gensalt(rounds=12, prefix="2a")))'
```
If you don't have bcrypt installed, use `pip install bcrypt`. 

Be aware that usage of BCrypt hashes will cause significant CPU overhead for processing of each http request, because we
don't have sessions and all requests are authenticated. To avoid this overhead you can configure cashing of hashes using
configuration:

```hocon
authentication: {
  method: "BasicAuth"
  usersFile: "conf/users.conf"
  cachingHashes {
    enabled: true
  }
}
```

This workaround causes that passwords are kept in the memory, and it will introduce risk that someone with access to
content of heap will see cached passwords.

### OpenID Connect security module

When talking about OAuth2 in the context of authentication, most people probably mean OpenID Connect, an identity layer
built on top of it. Nussknacker provides a separate authentication provider for OIDC with simple configuration 
and provider discovery. The only supported flow is the authorization code flow with client secret.

You can select this authentication method by setting the `authentication.method` parameter to `Oidc`

| Parameter name                       | Importance  | Type        | Default value               | Description                                                                                                  |
| ---                                  | ---         | ---         | ---                         | ---                                                                                                          |
| authentication.issuer                | required    | url         |                             | OpenID Provider's location                                                                                   |
| authentication.clientId              | required    | string      |                             | Client identifier valid at the authorization server                                                          |
| authentication.clientSecret          | required    | string      |                             | Secret corresponding to the client identifier at the authorization server                                    |
| authentication.audience              | recommended | string      |                             | Required `aud` claim value of an access token that is assumed to be a JWT.                                   |
| authentication.rolesClaim            | recommended | string      |                             | ID Token claim used for mapping users to roles instead of or additionally to the ones defined in `usersFile` |
| authentication.redirectUri           | optional    | url         | inferred from UI's location | Callback URL to which a user is redirected after successful authentication                                   |
| authentication.scope                 | optional    | string      | `openid profile`            | Scope parameter's value sent to the authorization endpoint.                                                   |
| authentication.authorizationEndpoint | auxiliary   | url or path | discovered                  | Absolute URL or path relative to `Issuer` overriding the value retrieved from the OpenID Provider            |
| authentication.tokenEndpoint         | auxiliary   | url or path | discovered                  | as above                                                                                                     |
| authentication.userinfoEndpoint      | auxiliary   | url or path | discovered                  | as above                                                                                                     |
| authentication.jwksUri               | auxiliary   | url or path | discovered                  | as above                                                                                                     |

#### Auth0 sample configuration

Assuming `${nussknackerUrl}` is the location of your deployment, in your Auth0 tenant settings do the following:

- Create a "Regular Web Application" with the "Allowed Callback URL's" field set to `${nussknackerUrl}`
- Create an "API" with the "Identifier" field preferably set to `${nussknackerUrl}/api`
- Create an Auth Pipeline Rule with the content: 
```javascript
function (user, context, callback) {
  const assignedRoles = (context.authorization || {}).roles || ['User'];

  let idTokenClaims = context.idToken || {};

  idTokenClaims[`nussknacker:roles`] = assignedRoles;

  context.idToken = idTokenClaims;
  callback(null, user, context);}
```

You can find more configurations options at Auth0's documentation on
[applications](https://auth0.com/docs/get-started/dashboard/application-settings)
and [APIs](https://auth0.com/docs/configure/apis/api-settings)

In Nussknacker's configuration file add the following `authentication` section:
```hocon
authentication: {
  method: "Oidc"
  issuer: "https://<the value of Applications -> Application Name -> Settings -> Basic Information -> Domain>"
  clientSecret: "<the value of Applications -> Application Name -> Settings -> Basic Information -> Client Secret>"
  clientId: "<the value of Applications -> Application Name -> Settings -> Basic Information -> Client Identifier>"
  audience: "<the value of APIs -> API Name -> Settings -> General Settings -> Identifier>"
  rolesClaim: "nussknacker:roles"
  usersFile: "conf/users.conf"
}
```

The role names in the `usersFile` should match the roles defined in the Auth0 tenant.

### OAuth2 security module

#### Generic configuration

```
authentication: {
  method: ${?AUTHENTICATION_METHOD} (default: "BasicAuth")
  clientSecret: ${?OAUTH2_CLIENT_SECRET}
  clientId: ${?OAUTH2_CLIENT_ID}
  authorizeUri: ${?OAUTH2_AUTHORIZE_URI}
  redirectUri: ${?OAUTH2_REDIRECT_URI}
  accessTokenUri: ${?OAUTH2_ACCESS_TOKEN_URI}
  profileUri: ${?OAUTH2_PROFILE_URI}
  profileFormat: ${?OAUTH2_PROFILE_FORMAT}
  implicitGrantEnabled: ${?OAUTH2_IMPLICIT_GRANT_ENABLED}
  jwt {
    accessTokenIsJwt: ${?OAUTH2_ACCESS_TOKEN_IS_JWT} (default: false)
    userinfoFromIdToken: ${?OAUTH2_USERINFO_FROM_ID_TOKEN} (default: false)
    audience: ${?OAUTH2_JWT_AUDIENCE}
    publicKey: ${?OAUTH2_JWT_AUTH_SERVER_PUBLIC_KEY}
    publicKeyFile: ${?OAUTH2_JWT_AUTH_SERVER_PUBLIC_KEY_FILE}
    certificate: ${?OAUTH2_JWT_AUTH_SERVER_CERTIFICATE}
    certificateFile: ${?OAUTH2_JWT_AUTH_SERVER_CERTIFICATE_FILE}
    idTokenNonceVerificationRequired: ${?OAUTH2_JWT_ID_TOKEN_NONCE_VERIFICATION_REQUIRED}
  }
  accessTokenParams {
    grant_type: ${?OAUTH2_GRANT_TYPE}
  }
  accessTokenRequestContentType: "application/json" (default)
  authorizeParams {
    response_type: ${?OAUTH2_RESPONSE_TYPE}
    scope: ${?OAUTH2_SCOPE}
    audience: ${?OAUTH2_AUDIENCE}
  }
  headers {
    Accept: ${?AUTHENTICATION_HEADERS_ACCEPT}
  }
  usersFile: ${?AUTHENTICATION_USERS_FILE}
}
```

When `method` is set to `OAuth2`, the following fields are mandatory: `clientSecret`, `clientId`, `authorizeUri`
, `redirectUri`, `accessTokenUri`, `profileUri`, `profileFormat`, `implicitGrantEnabled`, `usersFile`.

For the `profileFormat` one of `oidc` or `github` is supported by default.

Subconfigs `accessTokenParams`, `accessTokenRequestContentType`, `authorizeParams`, `headers` are optional and every
field from any of the subconfigs is optional and could be provided separately.

By default, access token request is sent using `application/json` content type, to change it (e.g.
to `application/x-www-form-urlencoded`) use `accessTokenRequestContentType` config.

Subconfig `jwt` is also optional. However, if it is present and `enabled` is set to
true, the `audience` and one of the `publicKey`, `publicKeyFile`, `certificate`, `certificateFile`, 
fields have to be provided.

Access tokens are introspected only once and then stored in a cache for the expiration time.
When using JWT, an incoming access token is validated against its signature and `aud`(audience) claim.
Otherwise, a token is considered valid when it allows a `profileUri` (e.g. `/userinfo`)  query.
No token refreshing nor revoking is implemented.


#### Remarks:

- Setting `jwt.idTokenNonceVerificationRequired` to `true` has an effect only if `implicitGrantEnabled` is also set
  to `true`.
- For security reasons, if implicit flow used, `implicitGrantEnabled` and `jwt.idTokenNonceVerificationRequired` should
  be set to `true`. Furthermore `authorizeParams.response_type` should be set to `"access_token id_token"` (not
  only `"access_token"`). Then a received `access_token` format has to be JWT (like `id_token`).

  *Of course, this does not apply when `authorizeParams.response_type` is set to `code` (code flow used).*
- Provided `jwt` is enabled, the backend first checks whether a user profile can be obtained from the `access_token`,
  secondly it tries to obtain the profile from a request sent to `authorizeUri`.

### OAuth2 security module - GitHub example with code flow

#### Configuration in following format:

```
authentication: {
  method: "OAuth2"
  clientSecret: ""
  clientId: ""
  authorizeUri: "https://github.com/login/oauth/authorize"
  redirectUri: "http://localhost:3000"
  accessTokenUri: "https://github.com/login/oauth/access_token"
  profileUri: "https://api.github.com/user"
  profileFormat: "github"
  implicitGrantEnabled: false
  accessTokenParams {
    grant_type: "authorization_code"
  }
  headers {
    Accept: "application/json"
  }
  authorizeParams {
    response_type: "code"
  }
  usersFile: "./src/test/resources/oauth2-users.conf"
}
```

#### Users file in following format:

```
users: [ //Special settings by user email
  {
    identity: "some@email.com"
    roles: ["Admin"]
  }
]

rules: [
  {
    role: "Admin"
    isAdmin: true
  },
  {
    role: "User" //this is default role for all users
    permissions: ["Read", "Write", "Deploy"]
    categories: ["Defautl", "FraudDetection"]
  }
]
```

## UI customization options

### Process Toolbar Configuration

Toolbars and buttons at process window are configurable, you can configure params:

* uuid - optional uuid identifier which determines unique code for FE localstorage cache, default: null - we generate
  uuid from hashcode config
* topLeft - optional top left panel, default: empty list
* bottomLeft - optional bottom left panel, default: empty list
* topRight - optional top right panel, default: empty list
* bottomRight - optional bottom right panel, default: empty list

Example configuration:

```
processToolbarConfig {
  defaultConfig {
    topLeft: [
      { type: "tips-panel" }
    ]
    topRight: [
      {
        type: "process-info-panel"
        buttons: [
          { type: "process-save", disabled: { subprocess: false, archived: true, type: "oneof" } }
          { type: "custom-link", title: "Metrics for $processName", url: "/metrics/$processId" }
        ]
      }
    ]
  }
}
```

We can also create special configuration for each category by:

```
processToolbarConfig {
  categoryConfig {
   "CategoryName" {
        id: "58f1acff-d864-4d66-9f86-0fa7319f7043"
        topLeft: [
          { type: "creator-panel", hidden: {subprocess: true, archived: false, type: "allof"} }
        ]
    } 
  }
}
```

#### Toolbar Panel Conditioning

Configuration allow us to:

* hiding panels
* hiding or disabling buttons

Each of this function can be configured by condition expression where we can use three parameters:

* `subprocess: boolean` - if true then condition match only subprocess, by default ignored
* `archived: boolean` - if true then condition match only archived, by default ignored
* `type: allof / oneof` - information about that checked will be only one condition or all conditions

#### Toolbar Panel Templating

Configuration allows to templating params like:

* `name` - available only on Buttons
* `title`- available on Panels and Buttons
* `url` - available only on CustomLink and CustomAction buttons
* `icon`- available only on Buttons

Right now we allow to template two elements:

* scenario id -`$processId`
* scenario name - `$processName`

Example usage:

* `title: "Metrics for $processName"`
* `name: "deploy $processName"`
* `url: "/metrics/$processId" `
* `icon: "/assets/process-icon-$processId"`

#### Default Process Panel Configuration

```
processToolbarConfig {
  defaultConfig {
    topLeft: [
      { type: "tips-panel" }
      { type: "creator-panel", hidden: { archived: true } }
      { type: "versions-panel" }
      { type: "comments-panel" }
      { type: "attachments-panel" }
    ]
    topRight: [
      {
        type: "process-info-panel"
        buttons: [
          { type: "process-save", title: "Save changes", disabled: { archived: true } }
          { type: "process-deploy", disabled: { subprocess: true, archived: true, type: "oneof" } }
          { type: "process-cancel", disabled: { subprocess: true, archived: true, type: "oneof" } }
          { type: "custom-link", name: "metrics", icon: "/assets/buttons/metrics.svg", url: "/metrics/$processName", disabled: { subprocess: true } }
        ]
      }
      {
        id: "view-panel"
        type: "buttons-panel"
        title: "view"
        buttons: [
          { type: "view-zoom-in" }
          { type: "view-zoom-out" }
          { type: "view-reset" }
        ]
      }
      {
        id: "edit-panel"
        type: "buttons-panel"
        title: "edit"
        hidden: { archived: true }
        buttonsVariant: "small"
        buttons: [
          { type: "edit-undo" }
          { type: "edit-redo" }
          { type: "edit-copy" }
          { type: "edit-paste" }
          { type: "edit-delete" }
          { type: "edit-layout" }
        ]
      }
      {
        id: "process-panel"
        type: "buttons-panel"
        title: "process"
        buttons: [
          { type: "process-properties", hidden: { subprocess: true } }
          { type: "process-compare" }
          { type: "process-migrate", disabled: { archived: true } }
          { type: "process-import", disabled: { archived: true } }
          { type: "process-json" }
          { type: "process-pdf" }
          { type: "process-archive", hidden: { archived: true } }
          { type: "process-unarchive", hidden: { archived: false } }
        ]
      }
      {
        id: "test-panel"
        type: "buttons-panel"
        title: "test"
        hidden: { subprocess: true }
        buttons: [
          { type: "test-from-file", disabled: { archived: true } }
          { type: "test-generate", disabled: { archived: true } }
          { type: "test-counts" }
          { type: "test-hide" }
        ]
      }
      {
        id: "group-panel"
        type: "buttons-panel"
        title: "group"
        hidden: { archived: true }
        buttons: [
          { type: "group" }
          { type: "ungroup" }
        ]
      }
    ]
  }
}
```

### Main menu configuration
                      
Tabs (in main menu bar, such as Scenarios etc.) can be configured in the following way:
```
 tabs: ${tabs} [
  {
    id: "kibana",
    title: "Kibana",
    url: "https://myKibana.org/kibana",
    type: "IFrame",
    requiredPermission: "AdminTab"
  }
]
```
By default, only `Scenarios` tab is configured.

| Parameter name     | Type                | Description                                                |
| --------------     | ----                | -----------                                                |
| id                 | string              | Unique identifier                                          |
| title              | string              | Title appearing in UI                                      |
| type               | IFrame/Local/Remote | Type of tab (see below for explanation)                    |
| url                | string              | URL of the tab                                             |
| requiredPermission | string              | Optional parameter, name of [Global Permission](#security) |
                                       
The types of tabs can be as follows (see `dev-application.conf` for some examples):
- IFrame - contents of the url parameter will be embedded as IFrame
- Local - redirect to Designer page (`/admin`, `/signals`, `/processes` etc., see [code](https://github.com/TouK/nussknacker/blob/staging/ui/client/containers/NussknackerApp.tsx#L118)
  for other options)
- Remote - [module federation](https://webpack.js.org/concepts/module-federation/) can be used to embed external tabs, url should be in form: `{module}/{path}@{host}/{remoteEntry}.js`  

## Environment configuration

Nussknacker installation may consist of more than one environment. Typical example is a configuration, where you have:
- environment for testing scenarios, which mirrors production data (e.g. via Kafka mirror-maker), but contains
  only scenarios that are currently worked on
- production environment

You can configure `secondaryEnvironment` to allow for 
- easy migration of scenarios
- comparing scenarios between environments
- testing (currently only via REST API) if all scenarios from secondary environment are valid with model configuration from this environment (useful for testing configuration etc.)
Currently, you can only configure secondary environment if it uses BASIC authentication - technical user is needed to access REST API.

| Parameter name                              | Importance | Type                                                                | Default value | Description                                                                                                                                                                                             |
| --------------                              | ---------- | ----                                                                | ------------- | -----------                                                                                                                                                                                             |
| environment                                 | Medium     | string                                                              |               | Used mainly for metrics configuration. Please note: it **has** to be consistent with [tag configuration of metrics](https://github.com/TouK/nussknacker-quickstart/blob/main/telegraf/telegraf.conf#L6) |
| environmentAlert.content                    | Low        | string                                                              |               | Human readable name of environment, to display in UI                                                                                                                                                    |
| environmentAlert.cssClass                   | Low        | indicator-green / indicator-blue / indicator-yellow / indicator-red |               | Color of environment indicator                                                                                                                                                                          |
| secondaryEnvironment.remoteConfig.uri       | Medium     | string                                                              |               | URL of Nussknacker REST API e.g. `http://secondary.host:8080/api`                                                                                                                                       |
| secondaryEnvironment.remoteConfig.batchSize | Low        | int                                                                 | 10            | For testing compatibility we have to load all scenarios, we do it in batches to optimize                                                                                                                |
| secondaryEnvironment.user                   | Medium     | string                                                              |               | User that should be used for migration/comparison                                                                                                                                                       |
| secondaryEnvironment.password               | Medium     | string                                                              |               | Password of the user that should be used for migration/comparison                                                                                                                                       |
| secondaryEnvironment.targetEnvironmentId    | Low        | string                                                              |               | Name of the secondary environment (used mainly for messages for user)                                                                                                                                   |

## Testing 

| Parameter name                    | Importance | Type   | Default value | Description                                           |
| --------------                    | ---------- | ----   | ------------- | -----------                                           |
| testDataSettings.maxSampleCount   | Medium     | string | 20            | Limits number of samples for tests from file          |
| testDataSettings.testDataMaxBytes | Low        | string | 200000        | Limits size of test input for tests from file         |
| testDataSettings.resultsMaxBytes  | Low        | string | 50000000      | Limits size of returned test data for tests from file |


## Other configuration options

| Parameter name                   | Importance | Type    | Default value         | Description                                                                                   |
| --------------                   | ---------- | ----    | -------------         | -----------                                                                                   |
| attachmentsPath                  | Medium     | string  | ./storage/attachments | Place where scenario attachments are stored                                                   |
| analytics.engine                 | Low        | Matomo  |                       | Currently only available analytics engine is [Matomo](https://matomo.org/)                    |
| analytics.url                    | Low        | string  |                       | URL of Matomo server                                                                          |
| analytics.siteId                 | Low        | string  |                       | [Site id](https://matomo.org/faq/general/faq_19212/)                                          |
| intervalTimeSettings.processes   | Low        | int     | 20000                 | How often frontend reloads scenario list                                                      |
| intervalTimeSettings.healthCheck | Low        | int     | 30000                 | How often frontend reloads checks scenarios states                                            |
| developmentMode                  | Medium     | boolean | false                 | For development mode we disable some security features like CORS. **Don't** use in production |

## Scenario type, categories

Every scenario has to belong to a group called `category`. For example, in one Nussknacker installation you can have
scenarios detecting frauds, and those implementing marketing campaigns. Category configuration looks like this:

```
categoriesConfig: {
  "marketing": "streaming",
  "fraud": "streaming",
}
```

For each category you have to define its scenario type (`streaming` in examples above). Scenario type configuration consists of two parts:
- `deploymentConfig` - [deployment manager configuration](DeploymentManagerConfiguration)
- `modelConfig` - [model configuration](ModelConfiguration)

In Nussknacker distribution there are preconfigured scenario types:
- `streaming` - using Flink Deployment Manager providing both stateful and stateless streaming components
- `streaming-lite-embedded` - using embedded Streaming-Lite Deployment Manager providing only stateless streaming components
- `request-response-embedded` - use embedded Request-Response Deployment Manager, scenario logic is exposed as REST API, on additional HTTP port at Designer

And one `Default` category using `streaming` by default (can be configured via `DEFAULT_SCENARIO_TYPE` environment variable)

See [example](https://github.com/TouK/nussknacker/blob/staging/nussknacker-dist/src/universal/conf/dev-application.conf#L33) 
from development config for more complex examples.
