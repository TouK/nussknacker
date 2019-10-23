#Security

Nussnkacker has pluggable security architecture. You can either use default authentication provider, based
on Basic authentication and static user configuration or integrate with other authentication mechanisms such as corporate SSO.

##Users and permissions
Each user has id and set of permissions for every process category. There are following permissions:
* Read - user can view processes in category
* Write - user can modify/add new processes in category
* Deploy - user can deploy or cancel processes in given category

## Global permissions
In addition to permission system oriented around processes' categories we provide additional set of permissions.
This feature is designed to control access to components that have no category attached or it doesn't make sense for them to have one.

Currently supported permissions:
* AdminTab - shows Admin tab in the UI (right now there are some useful things kept there including search components functionality).

##Default security module

Default security provider uses users file in following format:
```
users: [
  {
    id: "user1"
    password: "pass"
    categoryPermissions: {
      "Category1": ["Read", "Deploy"]
      "Category2": ["Read", "Write"]
    }
    globalPermissions: ["AdminTab"]
  },
  {
    id: "user2"
    encrypedPassword: "$2a$12$oA3U7DXkT5eFkyB8GbtKzuVqxUCU0zDmcueBYV218zO/JFQ9/bzY6"
    categoryPermissions: {
      "Category1": ["Read", "Deploy"]
    }
  }
]

```
You can store passwords as plaintext or (preferably) encrypted using bcrypt. To compute encrypted passiowrd you can use following python script:
```python
import bcrypt
print(bcrypt.hashpw("password_to_encode".encode("utf8"), bcrypt.gensalt(rounds = 12, prefix = "2a")))

```

##Implementing own security provider

In order to implement authentication provider you have to implement trait: 
```java
trait AuthenticatorFactory {
  def createAuthenticator(config: Config): AuthenticationDirective[LoggedUser]
}
```

It is based on `AuthenticationDirective` of Akka Http. Implementation must be put on Nussknacker classpath (Note: **not** in jar with model)
You must also register your implementation using Java's ServiceLoader mechanism - that is, you have to provide
file `META-INF/services/pl.touk.nussknacker.ui.security.api.AuthenticatorFactory` containing fully qualified name of implementation of `AuthenticatorFactory`.
Please note that there can be **only** one implementation on the classpath. 
