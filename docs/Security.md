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
    encrypedPassword: "$2a$12$oA3U7DXkT5eFkyB8GbtKzuVqxUCU0zDmcueBYV218zO/JFQ9/bzY6"
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

## OAuth2 security module

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
  accessTokenParams: {
    grant_type: "authorization_code"
  }
  headers: {
    Accept: "application/json"
  }
  authorizeParams: {
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
