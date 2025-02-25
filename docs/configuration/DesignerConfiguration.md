---
title: Designer
sidebar_position: 3
---
# Designer configuration

Designer configuration area contains configs for web application interface, security, various UI settings, database and more. Check [configuration areas](./index.mdx#configuration-areas) to understand where Designer configuration should be
placed in the Nussknacker configuration.

The default Designer configuration can be found in [defaultDesignerConfig.conf](https://github.com/TouK/nussknacker/blob/staging/designer/server/src/main/resources/defaultDesignerConfig.conf).


## Web interface configuration

| Parameter name                              | Importance | Type     | Default value | Description                                                                                                                                                                    |
|---------------------------------------------|------------|----------|---------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
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

Database is used to store scenario definitions; data processed by Nussknacker are not stored. Currently, Nussknacker supports following databases:

* HSQL (embedded), we use `syntax_ora` option
* PostgreSQL

Please
check [Slick documentation](https://scala-slick.org/doc/3.1.0/api/index.html#slick.jdbc.JdbcBackend$DatabaseFactoryDef@forConfig(String,Config,Driver,ClassLoader):Database)
for detailed list of configuration options.

The table below presents most important options, or the ones that have Nussknacker specific defaults.

| Parameter name       | Importance | Type   | Default value                                             | Description                                                                                 |
|----------------------|------------|--------|-----------------------------------------------------------|---------------------------------------------------------------------------------------------|
| db.url               | High       | string | "jdbc:hsqldb:file:"${storageDir}"/db;sql.syntax_ora=true" | Default HSQL location                                                                       |
| db.driver            | High       | string | "org.hsqldb.jdbc.JDBCDriver"                              | "org.postgresql.Driver" in case of PostgreSQL                                               |
| db.user              | High       | string | "SA"                                                      |                                                                                             |
| db.password          | High       | string | ""                                                        |                                                                                             |
| db.connectionTimeout | Low        | int    | 30000                                                     |                                                                                             |
| db.maximumPoolSize   | Low        | int    | 5                                                         | We have lower limits than default config, since then Designer is not heavy-load application |
| db.minimumIdle       | Low        | int    | 1                                                         | We have lower limits than default config, since then Designer is not heavy-load application |
| db.numThreads        | Low        | int    | 5                                                         | We have lower limits than default config, since then Designer is not heavy-load application |

## Metrics settings

### Metric dashboard

Each scenario can have a link to Grafana dashboard. 
In [Docker setup](https://github.com/TouK/nussknacker-quickstart/tree/main/grafana/dashboards) 
we provide a sample `nussknacker-scenario` dashboard. You can modify/configure your own, the only assumption that 
we make is that [variable](https://grafana.com/docs/grafana/latest/variables/) `scenarioName` is used to display 
metrics for particular scenario.

Each scenario type can have different dashboard, this is configured by
`metricsSettings.scenarioTypeToDashboard` settings. If no mapping is configured, `metricsSettings.defaultDashboard` is used.
Actual link for particular scenario is created by replacing
- `$dashboard` with configured dashboard
- `$scenarioName` with scenario name
  in `metricsSettings.url` setting.

| Parameter name                          | Importance | Type   | Default value                                                                                      | Description                                                                                                                    |
|-----------------------------------------|------------|--------|----------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------|
| metricsSettings.url                     | High       | string | `/grafana/d/$dashboard?theme=dark&var-scenarioName=$scenarioName&var-env=local` (for Docker setup) | URL (accessible from user browser, in docker setup its configured as relative URL) to Grafana dashboard, see above for details |
| metricsSettings.defaultDashboard        | Medium     | string | nussknacker-scenario (for Docker setup)                                                            | Default dashboard                                                                                                              |
| metricsSettings.scenarioTypeToDashboard | Low        | map    |                                                                                                    | Mapping of scenario types to dashboard                                                                                         |


### Counts

Counts are based on InfluxDB metrics, stored in `nodeCount` measurement by default.
`countsSettings.queryMode` setting can be used to choose metric computation algorithm:
- `OnlySingleDifference` - subtracts values between end and beginning of requested range. Fast method, but if restart
  in the requested time range is detected error is returned. We assume the job was restarted when event counter at the source
  decreases.
- `OnlySumOfDifferences` - difference is computed by summing differences in measurements for requested time range.
  This method works a bit better for restart situations, but can be slow for large diagrams and wide time ranges.
- `SumOfDifferencesForRestarts` - if restart is detected, the metrics are computed with `OnlySumDifferences`, otherwise - with `OnlySingleDifferences`

If you have custom metrics settings which result in different fields or tags (e.g. you have different telegraf configuration), you can configure required values
with the settings presented below:

| Parameter name                                     | Importance | Type                                                                      | Default value          | Description                                                                                                                                   |
|----------------------------------------------------|------------|---------------------------------------------------------------------------|------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------|
| countsSettings.influxUrl                           | Medium     | string                                                                    |                        | Main InfluxDB query endpoint (e.g. http://influx:8086/query). It should be accessible from Nussknacker Designer server, not from user browser |
| countsSettings.database                            | Medium     | string                                                                    |                        | InfluxDB database name                                                                                                                        |
| countsSettings.user                                | Medium     | string                                                                    |                        | User name (optional)                                                                                                                          |
| countsSettings.password                            | Medium     | string                                                                    |                        | Password (optional)                                                                                                                           |
| countsSettings.additionalHeaders                   | Low        | map of strings                                                            | empty map              | Additional headers sent to InfluxDB                                                                                                           |
| countsSettings.queryMode                           | Low        | OnlySingleDifference / OnlySumOfDifferences / SumOfDifferencesForRestarts | OnlySumOfDifferences   |                                                                                                                                               |
| countsSettings.metricsConfig.sourceCountMetric     | Low        | string                                                                    | source_count           |                                                                                                                                               |
| countsSettings.metricsConfig.nodeCountMetric       | Low        | string                                                                    | nodeCount              |                                                                                                                                               |
| countsSettings.metricsConfig.nodeIdTag             | Low        | string                                                                    | nodeId                 |                                                                                                                                               |
| countsSettings.metricsConfig.additionalGroupByTags | Low        | list of string                                                            | ["slot", "instanceId"] |                                                                                                                                               |
| countsSettings.metricsConfig.scenarioTag           | Low        | string                                                                    | scenario               |                                                                                                                                               |
| countsSettings.metricsConfig.countField            | Low        | string                                                                    | count                  |                                                                                                                                               |
| countsSettings.metricsConfig.envTag                | Low        | string                                                                    | env                    |                                                                                                                                               |

## Deployment settings

Nussknacker Designer can be configured to replace certain values in comments to links that can point e.g. to external issue tracker like
GitHub issues or Jira. For example, `MARKETING-555` will change to link `https://jira.organization.com/jira/browse/MARKETING-555`.
See [development configuration](https://github.com/TouK/nussknacker/blob/staging/nussknacker-dist/src/universal/conf/dev-application.conf#L329) for example configuration.


| Parameter name                              | Importance | Type     | Default value | Description                                                                                                                                                                          |
|---------------------------------------------|------------|----------|---------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| commentSettings.substitutionPattern         | Low        | regexp   |               | Regular expression to look for issue identifier (e.g. `(issues/[0-9]*)` - note use of regexp group)                                                                                  |
| commentSettings.substitutionLink            | Low        | string   |               | Link template (e.g. `https://github.com/TouK/nussknacker/$1` - `$1` will be replaced with matched group from `substitutionPattern` config                                            |
| deploymentCommentSettings.validationPattern | Low        | regexp   |               | If deploymentCommentSettings is specified, comment matching validation pattern is required for deployment. Also, if `substitutionPattern` is defined, at least one match is required |
| deploymentCommentSettings.exampleComment    | Low        | string   |               | Example of comment which passes validation. Unlike validationPattern field is not mandatory.                                                                                         |

## Security


### Overview
Nussknacker has pluggable security architecture - we support three types of authentication: BasicAuth, OAuth2 and OpenID Connect (OIDC). Configuration specific to each of these three types of authentication mechanism is described in the dedicated sections.

Nussknacker supports roles; the roles permissions are defined in the users configuration file.

### Users, roles and permissions

Each user has id and set of permissions for every scenario category. The following permissions are supported:

* Read - user can view scenarios in category
* Write - user can modify/add new scenarios in category
* Deploy - user can deploy or cancel scenarios in given category

You can set `isAdmin` flag to a certain role in the [users configuration file](#users-file-format).
Users who have this role will be considered an Admin user by the system and will have all the permissions to every
scenario category as well as all the [global permissions](#global-permissions).

You can set a role assigned to an anonymous user with the `anonymousUserRole` setting in the `authentication` section in the configuration.
When no value is provided (default), no anonymous access will be granted.

### Global permissions

In addition to permission system oriented around scenarios categories we provide additional set of permissions. This
feature is designed to control access to components that have no category attached, or it doesn't make sense for them to
have one.

Currently supported permissions:

* Impersonate - Enables technical user to impersonate a business user and act on their behalf with their permissions.

### Impersonation

Nussknacker supports an impersonation mechanism on the API level, allowing system's technical users to perform actions on behalf of
business users. A technical user has to have the `Impersonate` global permission configured in order to be able to
impersonate.

Currently only BasicAuth security mechanism supports this feature.

You can configure whether Admin users can be impersonated by such technical users with `isAdminImpersonationPossible`
setting in the `authentication` section. By default, it's set to `false`.

### Common configuration parameters

The table below contains parameters common to all the supported authentication methods.

| Parameter name                              | Importance | Type        | Default value | Description                                                                                                                                                        |
|---------------------------------------------|------------|-------------|---------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| authentication.method                       | required   | string      |               | `BasicAuth`, `Oidc` or `OAuth2`                                                                                                                                    |
| authentication.usersFile                    | required   | url or path |               | URL or path to a file with a mapping of user identities to roles and roles to permissions                                                                          |
| authentication.anonymousUserRole            | optional   | string      |               | Role assigned to an unauthenticated user if the selected authentication provider permits anonymous access. No anonymous access allowed unless a value is provided. |
| authentication.isAdminImpersonationPossible | required   | boolean     | false         | `true` or `false`. Flag describing whether users with `Impersonate` global permission can impersonate Admin users.                                                 |

#### Users' file format:

The association of the users to the roles is in the users' configuration file; in the case of OIDC it can additionally be supplemented by the list of roles provided by the OpenId provider.

If OpenID Connect (OIDC) authentication is used, the information about the user identity is stored in the field `sub` (subject) of the OIDC token - make sure that these values match.

```hocon
users: [
  {
    identity: "admin"
    roles: ["Admin"]
  },
  {
    identity: "userId",
    username: "overrided username",
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
    isAdmin: true
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

### OpenID Connect (OIDC) security module

When talking about OAuth2 in the context of authentication, most people probably mean OpenID Connect, an identity layer
built on top of it. Nussknacker provides a separate authentication provider for OIDC with simple configuration
and provider discovery. The only supported flow is the authorization code flow with client secret.

You can select this authentication method by setting the `authentication.method` parameter to `Oidc`

| Parameter name                       | Importance  | Type           | Default value               | Description                                                                                                                                                                                                                                             |
|--------------------------------------|-------------|----------------|-----------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| authentication.issuer                | required    | url            |                             | OpenID Provider's location                                                                                                                                                                                                                              |
| authentication.clientId              | required    | string         |                             | Client identifier valid at the authorization server                                                                                                                                                                                                     |
| authentication.clientSecret          | required    | string         |                             | Secret corresponding to the client identifier at the authorization server                                                                                                                                                                               |
| authentication.audience              | recommended | string         |                             | Required `aud` claim value of an access token that is assumed to be a JWT.                                                                                                                                                                              |
| authentication.rolesClaims           | recommended | list of string |                             | Name of the field in the ID token which contains list of user roles. These roles must correspond to those defined in the `usersFile`.                                                                                                                   |
| authentication.redirectUri           | optional    | url            | inferred from UI's location | Callback URL to which a user is redirected after successful authentication                                                                                                                                                                              |
| authentication.scope                 | optional    | string         | `openid profile`            | Scope parameter's value sent to the authorization endpoint.                                                                                                                                                                                             |
| authentication.authorizationEndpoint | auxiliary   | url or path    | discovered                  | Absolute URL or path relative to `Issuer` overriding the value retrieved from the OpenID Provider                                                                                                                                                       |
| authentication.tokenEndpoint         | auxiliary   | url or path    | discovered                  | as above                                                                                                                                                                                                                                                |
| authentication.userinfoEndpoint      | auxiliary   | url or path    | discovered                  | as above                                                                                                                                                                                                                                                |
| authentication.jwksUri               | auxiliary   | url or path    | discovered                  | as above                                                                                                                                                                                                                                                |
| authentication.tokenCookie.name      | auxiliary   | string         |                             | name of cookie to store access token                                                                                                                                                                                                                    |
| authentication.tokenCookie.path      | auxiliary   | string         |                             | path of access token cookie                                                                                                                                                                                                                             |
| authentication.tokenCookie.domain    | auxiliary   | string         |                             | domain of access token cookie                                                                                                                                                                                                                           |
| authentication.usernameClaim         | optional    | string         |                             | The OIDC claim from JWT which be mapped to the username at Nussknacker authorized user object. Available options: `preferred_username`, `given_name`, `nickname`, `name`. By default, username is represented by the `sub` (identifier) claim from JWT. |

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
  rolesClaims: ["nussknacker:roles"]
  usersFile: "conf/users.conf"
}
```

The role names in the `usersFile` should match the roles defined in the Auth0 tenant.

#### MS Azure Active Directory sample configuration

- Open MS Azure Portal: https://portal.azure.com/
- Go to Azure Active Directory Service
- Register new app: AAD Service -> App registrations -> New registration
- Add auth platform: AAD Service -> App registrations -> Your App -> Authentication -> Add a platform
- Register app roles: AAD Service -> App registrations -> Your App -> App roles -> Create app role
- Add client secret: AAD Service -> App registrations -> Your App -> Certificates & secrets -> New client secret
- Configure users roles: Enterprise applications -> Your App -> Users and groups -> Add user/group

In Nussknacker's configuration file add the following `authentication` section:
```hocon
authentication: {
  method: "Oidc"
  issuer: "https://login.microsoftonline.com/YOUR_TENANT_ID/v2.0"
  clientSecret: <the value of App registrations -> Your App -> Certificates & secrets -> Your created secret value>
  clientId: <the value of App registrations -> Your App -> Overview -> Application (client) ID>
  usernameClaim: "name" # Here MS AAD returns at JWT full user name 
  rolesClaims: ["roles"] # Here MS AAD returns at JWT information about assigned roles
  usersFile: "conf/users.conf"
}
```

The value of YOUR_TENANT_ID you can find at App registrations -> Your App -> Directory (tenant) ID. More information about
the API you can find at https://login.microsoftonline.com/YOUR_TENANT_ID/v2.0/.well-known/openid-configuration.

The role names in the `usersFile` should match the roles defined in MS AAD App registrations -> Your App -> App roles.

### OAuth2 security module

#### Generic configuration

```
authentication: {
  method: ${?AUTHENTICATION_METHOD} (default: "BasicAuth")
  usersFile: ${?AUTHENTICATION_USERS_FILE}
  authorizeUri: ${?OAUTH2_AUTHORIZE_URI}
  clientSecret: ${?OAUTH2_CLIENT_SECRET}
  clientId: ${?OAUTH2_CLIENT_ID}
  issuer: ${?OAUTH2_ISSUER}
  profileUri: ${?OAUTH2_PROFILE_URI}
  profileFormat: ${?OAUTH2_PROFILE_FORMAT}
  accessTokenUri: ${?OAUTH2_ACCESS_TOKEN_URI}
  redirectUri: ${?OAUTH2_REDIRECT_URI}
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
  authorizeParams {
    response_type: ${?OAUTH2_RESPONSE_TYPE}
    scope: ${?OAUTH2_SCOPE}
    audience: ${?OAUTH2_AUDIENCE}
  }
  usernameClaim: ${?OAUTH2_USERNAME_CLAIM}
  headers {
    Accept: ${?AUTHENTICATION_HEADERS_ACCEPT}
  }
  authorizationHeader: "Authorization" (default)
  accessTokenRequestContentType: "application/json" (default)
  defaultTokenExpirationDuration: "1 hour" (default)
  anonymousUserRole: None (default)
  tokenCookie: None (default)
  overrideFrontendAuthenticationStrategy: None (default)
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
- Access token can be configured to be set in http-only cookie (e.g. for enabling proxy grafana authentication). This is disabled by default,
  it can be enabled with following config:

```
  authentication {
  ...
    tokenCookie {
      name: "cookieName"
      path: "/grafana"  //optional
      domain: "mydomain.com"  //optional
    }
  }
```
### OAuth2 security module - GitHub example with code flow

#### Configuration example:

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

#### Users file example:

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
    categories: ["Default", "FraudDetection"]
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
        type: "process-actions-panel"
        buttons: [
          { type: "process-save", disabled: { fragment: false, archived: true, type: "oneof" } }
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
          { type: "creator-panel", hidden: {fragment: true, archived: false, type: "allof"} }
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

* `fragment: boolean` - if true then condition match only fragment, by default ignored
* `archived: boolean` - if true then condition match only archived, by default ignored
* `type: allof / oneof` - information about that checked will be only one condition or all conditions

#### Toolbar Panel Templating

Configuration allows to templating params like:

* `name` - available only on Buttons
* `title`- available on Panels and Buttons
* `url` - available only on CustomLink buttons
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
      { type: "search-panel" }
      { type: "tips-panel" }
      { type: "creator-panel", hidden: { archived: true } }
      { type: "versions-panel" }
      { type: "comments-panel" }
      { type: "attachments-panel" }
    ]
    topRight: [
      { type: "process-info-panel" }
      {
        type: "process-actions-panel"
        buttons: [
          { type: "process-save", title: "Save changes", disabled: { archived: true } }
          { type: "process-deploy", disabled: { fragment: true, archived: true, type: "oneof" } }
          { type: "process-cancel", disabled: { fragment: true, archived: true, type: "oneof" } }
          { type: "custom-link", name: "metrics", icon: "/assets/buttons/metrics.svg", url: "/metrics/$processName", disabled: { fragment: true } }
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
          { type: "process-properties", hidden: { fragment: true } }
          { type: "process-compare" }
          { type: "process-migrate", disabled: { archived: true } }
          { type: "process-import", disabled: { archived: true } }
          { type: "process-export" }
          { type: "process-pdf" }
          { type: "process-archive", hidden: { archived: true } }
          { type: "process-unarchive", hidden: { archived: false } }
        ]
      }
      {
        id: "test-panel"
        type: "buttons-panel"
        title: "test"
        hidden: { fragment: true }
        buttons: [
          { type: "test-from-file", disabled: { archived: true } }
          { type: "test-generate", disabled: { archived: true } }
          { type: "test-counts" }
          { type: "test-hide" }
          { type: "generate-and-test", disabled: { archived: true } }
          { type: "adhoc-testing", disabled: { archived: true } }
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

| Parameter name                   | Type                    | Default value | Description                                                                                                              |
|----------------------------------|-------------------------|---------------|--------------------------------------------------------------------------------------------------------------------------|
| id                               | string                  |               | Unique identifier                                                                                                        |
| title                            | string                  |               | Title appearing in UI                                                                                                    |
| type                             | IFrame/Local/Remote/Url |               | Type of tab (see below for explanation)                                                                                  |
| url                              | string                  |               | URL of the tab                                                                                                           |
| requiredPermission               | string                  |               | Optional parameter, name of [Global Permission](#security)                                                               |
| accessTokenInQuery.enabled       | boolean                 | false         | When true the parameter holding access token (if OAuth2 authentication is used) will be added to iframe query parameters |
| accessTokenInQuery.parameterName | string                  | auth_token    | Optional name of query parameter that holds the access token                                                             |

The types of tabs can be as follows (see `dev-application.conf` for some examples):
- IFrame - contents of the url parameter will be embedded as IFrame
- Local - redirect to Designer page (`/admin`, `/processes` etc., see [code](https://github.com/TouK/nussknacker/blob/staging/designer/client/src/containers/RootRoutes.tsx#L23)
  for other options)
- Remote - [module federation](https://webpack.js.org/concepts/module-federation/) can be used to embed external tabs, url should be in form: `{module}/{path}@{host}/{remoteEntry}.js`
- Url - redirect to external page/url

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
|---------------------------------------------|------------|---------------------------------------------------------------------|---------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| environment                                 | Medium     | string                                                              |               | Used mainly for metrics configuration. Please note: it **has** to be consistent with [tag configuration of metrics](https://github.com/TouK/nussknacker-quickstart/blob/main/telegraf/telegraf.conf#L6) |
| environmentAlert.content                    | Low        | string                                                              |               | Human readable name of environment, to display in UI                                                                                                                                                    |
| environmentAlert.color                      | Low        | indicator-green / indicator-blue / indicator-yellow / indicator-red |               | Color of environment indicator                                                                                                                                                                          |
| secondaryEnvironment.remoteConfig.uri       | Medium     | string                                                              |               | URL of Nussknacker REST API e.g. `http://secondary.host:8080/api`                                                                                                                                       |
| secondaryEnvironment.remoteConfig.batchSize | Low        | int                                                                 | 10            | For testing compatibility we have to load all scenarios, we do it in batches to optimize                                                                                                                |
| secondaryEnvironment.user                   | Medium     | string                                                              |               | User that should be used for migration/comparison                                                                                                                                                       |
| secondaryEnvironment.password               | Medium     | string                                                              |               | Password of the user that should be used for migration/comparison                                                                                                                                       |
| secondaryEnvironment.targetEnvironmentId    | Low        | string                                                              |               | Name of the secondary environment (used mainly for messages for user)                                                                                                                                   |

## Testing

| Parameter name                     | Importance | Type | Default value | Description                                                                                                    |
|------------------------------------|------------|------|---------------|----------------------------------------------------------------------------------------------------------------|
| testDataSettings.maxSamplesCount   | Medium     | int  | 20            | Limits the number of samples to be generated and received from a file                                          |
| testDataSettings.testDataMaxLength | Low        | int  | 200000        | Limits the size (in characters) of the test data generated and received in tests from a file                   |
| testDataSettings.resultsMaxBytes   | Low        | long | 50000000      | Limits the size (in bytes) of returned test data (i.e. variables for each node, invocation results and errors) |


## Other configuration options

| Parameter name                                             | Importance | Type     | Default value | Description                                                                                                                                                                                                                 |
|------------------------------------------------------------|------------|----------|---------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| attachments.maxSizeInBytes                                 | Medium     | long     | 10485760      | Limits max size of scenario attachment, by default to 10mb                                                                                                                                                                  |
| analytics.engine                                           | Low        | Matomo   |               | Currently only available analytics engine is [Matomo](https://matomo.org/)                                                                                                                                                  |
| analytics.url                                              | Low        | string   |               | URL of Matomo server                                                                                                                                                                                                        |
| analytics.siteId                                           | Low        | string   |               | [Site id](https://matomo.org/faq/general/faq_19212/)                                                                                                                                                                        |
| intervalTimeSettings.processes                             | Low        | int      | 20000         | How often frontend reloads scenario list                                                                                                                                                                                    |
| intervalTimeSettings.healthCheck                           | Low        | int      | 30000         | How often frontend reloads checks scenarios states                                                                                                                                                                          |
| developmentMode                                            | Medium     | boolean  | false         | For development mode we disable some security features like CORS. **Don't** use in production                                                                                                                               |
| enableConfigEndpoint                                       | Medium     | boolean  | false         | Expose config over http (GET /api/app/config/) - requires admin permission. Please mind, that config often contains password or other confidential data - this feature is meant to be used only on 'non-prod' envrionments. |
| redirectAfterArchive                                       | Low        | boolean  | true          | Redirect to scenarios list after archive operation.                                                                                                                                                                         |
| scenarioLabelSettings.validationRules                      | Low        | array    | []            | Allows to configure validation rules for scenario labels                                                                                                                                                                    |
| scenarioLabelSettings.validationRules[0].validationPattern | Low        | string   |               | [Regular expression](https://docs.oracle.com/javase/8/docs/api/java/util/regex/Pattern.html) for scenario label validation                                                                                                  |
| scenarioLabelSettings.validationRules[0].validationMessage | Low        | string   |               | Hint shown to the user when the scenario label does not match the given validation regex                                                                                                                                    |
| scenarioStateTimeout                                       | Low        | duration | 5 seconds     | Timeout for fetching scenario state operation                                                                                                                                                                               |
| usageStatisticsReports.enabled                             | Low        | boolean  | true          | When enabled browser will send anonymous usage statistics reports to `stats.nussknacker.io`                                                                                                                                 |
| usageStatisticsReports.errorReportsEnabled                 | Low        | boolean  | true          | When enabled browser will send anonymous errors reports to `stats.nussknacker.io`                                                                                                                                           |

## Scenario type, categories

Every scenario has to belong to a group called `category`. Category defines the business area around which you can organize 
[users permissions](#users-roles-and-permissions).

For example, in one Nussknacker installation you can have scenarios detecting frauds, and those implementing marketing campaigns. Then, the configuration will look like:

```
scenarioTypes {
  streaming-marketing {
    deploymentConfig { 
      (...) 
    }
    modelConfig {
      (...)
    }
    category: "Marketing"
  }
  streaming-fraud-detection {
    deploymentConfig { 
      (...) 
    }
    modelConfig {
      (...)
    }
    category: "Fraud Detection"
  }
}
```

Scenario type configuration consists of parts:
- `deploymentConfig` - [scenario deployment configuration](./ScenarioDeploymentConfiguration.md)
- `modelConfig` - [model configuration](./model/ModelConfiguration.md)
- `category` - category handled by given scenario type

In Nussknacker distribution there are preconfigured scenario types:
- `streaming` - using Flink Deployment Manager providing both stateful and stateless streaming components
- `streaming-lite-embedded` - using embedded Lite Deployment Manager in Streaming processing mode providing only stateless streaming components
- `request-response-embedded` - use embedded Request-Response Deployment Manager, scenario logic is exposed as REST API, on additional HTTP port at Designer

Each of these scenario types has `Default` category assigned.

See [example](https://github.com/TouK/nussknacker/blob/staging/nussknacker-dist/src/universal/conf/dev-application.conf#L33)
from development config for more complex examples.
