# Security

Nussknacker has pluggable security architecture - by default we support two type of authentication: BasicAuth and OAuth2.
You can either use default authentication provider, based on Basic authentication and static user configuration or integrate 
with other authentication mechanisms such as custom SSO implementation.

## Users and permissions
Each user has id and set of permissions for every process category. There are following permissions:
* Read - user can view processes in category
* Write - user can modify/add new processes in category
* Deploy - user can deploy or cancel processes in given category

## Global permissions
In addition to permission system oriented around processes' categories we provide additional set of permissions.
This feature is designed to control access to components that have no category attached or it doesn't make sense for them to have one.

Currently supported permissions:
* AdminTab - shows Admin tab in the UI (right now there are some useful things kept there including search components functionality).

## BasicAuth security module

#### Configuration in following format:
```
authentication: {
  method: "BasicAuth"
  usersFile: "conf/users.conf"
}
```

#### Users file in following format:
```
users: [
  {
    identity: "user1"
    password: "pass",
    roles: ["Writer"]
  },
  {
    identity: "user2"
    encryptedPassword: "$2a$12$oA3U7DXkT5eFkyB8GbtKzuVqxUCU0zDmcueBYV218zO/JFQ9/bzY6"
    permissions: ["Deployer"]
  }
]

rules: [
  {
    role: "Reader"
    permissions: ["Read"]
    categories: ["Category1", "Category2"]
  },
  {
    role: "Writer"
    permissions: ["Read", "Write"]
    categories: ["Category1", "Category2"]
  },
  {
    role: "Deployer"
    permissions: ["Read", "Write", "Deploy"]
    globalPermissions: ["AdminTab"]
    categories: ["Category1", "Category2"]
  }
]
```

#### Encrypted hashes

Encryption of passwords uses BCrypt algorithm. You can generate sample hash using command:
```
python -c 'import bcrypt; print(bcrypt.hashpw("password".encode("utf8"), bcrypt.gensalt(rounds=12, prefix="2a")))'
```

Be aware that usage of BCrypt hashes will cause significant CPU overhead for processing of each http request, because we don't have sessions and all requests are authenticated.
To avoid this overhead you can configure cashing of hashes using configuration:
```
authentication: {
  method: "BasicAuth"
  usersFile: "conf/users.conf"
  cachingHashes {
    enabled: true
  }
}
```
This workaround causes that passwords are kept in the memory and it will introduce risk that someone with access to content of heap will see cached passwords.

## OAuth2 security module

### Generic configuration

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
  headers {
    Accept: ${?AUTHENTICATION_HEADERS_ACCEPT}
  }
  usersFile: ${?AUTHENTICATION_USERS_FILE}
}
```

When `method` is set to `OAuth2`, the following fields are mandatory: `clientSecret`, `clientId`, `authorizeUri`, `redirectUri`, `accessTokenUri`, `profileUri`, `profileFormat`, `implicitGrantEnabled`, `usersFile`.

Subconfigs `accessTokenParams`, `authorizeParams`, `headers` are optional and every field from any of the subconfigs is optional and could be provided separately.

Subconfig `jwt` is also optional. However, if it is present, `idTokenNonceVerificationRequired` and one of the `publicKey`, `publicKeyFile`, `certificate`, `certificateFile` fields have to be provided.

#### Remarks:
 - Setting `jwt.idTokenNonceVerificationRequired` to `true` has an effect only if `implicitGrantEnabled` is also set to `true`.
 - For security reasons, if implicit flow used, `implicitGrantEnabled` and `jwt.idTokenNonceVerificationRequired` should be set to `true`.
   Furthermore `authorizeParams.response_type` should be set to `"access_token id_token"` (not only `"access_token"`).
   Then a received `access_token` format has to be JWT (like `id_token`).
   
   *Of course, this does not apply when `authorizeParams.response_type` is set to `code` (code flow used).*
 - Provided `jwt` is enabled, the backend first checks whether a user profile can be obtained from the `access_token`, secondly it tries to obtain the profile from a request sent to `authorizeUri`.

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

### OAuth2 security module - Auth0 example with implicit flow

#### Auth0 application configuration

 - "Application Type" should be set to "Regular Web Application"
 - "Use Auth0 instead of the IdP to do Single Sign On" should be enabled.
 - "Allowed Callback URLs" should contain `redirectUri`.
 - "Implicit" should be added to "Grant Types" in "Advanced Settings".
 
More at https://auth0.com/docs/get-started/dashboard/application-settings.

#### Configuration in following format:
```
authentication: {
  method: "OAuth2"
  clientSecret: ""
  clientId: ""
  authorizeUri: "https://<your-domain>.auth0.com/authorize"
  redirectUri: "http://localhost:3000"
  accessTokenUri: "https://<your-auth0-domain>.auth0.com/oauth/token"
  profileUri: "https://<your-auth0-domain>.auth0.com/userinfo"
  profileFormat: "auth0"
  implicitGrantEnabled: true
  jwt {
    certificateFile: "./etc/cert.pem"
    idTokenNonceVerificationRequired: true
  }
  authorizeParams {
    response_type: "access_token id_token"
    scope: "openid profile email"
    audience: "<your-domain>"
  }
  usersFile: "./src/test/resources/oauth2-users.conf"
}
```

##### *Users file in the same format as in GitHub example.*

#### Implementation own OAuth2ServiceFactory 
OAuth2 backend allows to exchange engine to fetching / parsing data from your OAuth2 Authentication Server and Profile Resource. 
By default we support Github data format. To do this, simply replace the OAuth2ServiceFactory by your own implementation. 
After that you have to register your implementation using Java's ServiceLoader mechanism by prepare `META-INFO/service` 
resource for `pl.touk.nussknacker.ui.security.oauth2.OAuth2ServiceFactory`. You can find an example at tests in 
`ExampleOAuth2ServiceFactory` file.

```
You can store passwords as plaintext or (preferably) encrypted using bcrypt. To compute encrypted passiowrd you can use following python script:
```python
import bcrypt
print(bcrypt.hashpw("password_to_encode".encode("utf8"), bcrypt.gensalt(rounds = 12, prefix = "2a")))

```

## Implementing own security provider

In order to implement authentication provider you have to implement trait: 
```java
trait AuthenticatorFactory {
  def createAuthenticator(config: Config, classLoader: ClassLoader): AuthenticationDirective[LoggedUser]
}
```

It is based on `AuthenticationDirective` of Akka Http. Implementation must be put on Nussknacker classpath (Note: **not** in jar with model)
You must also register your implementation using Java's ServiceLoader mechanism - that is, you have to provide
file `META-INF/services/pl.touk.nussknacker.ui.security.api.AuthenticatorFactory` containing fully qualified name of implementation of `AuthenticatorFactory`.
Please note that there can be **only** one implementation on the classpath. 
