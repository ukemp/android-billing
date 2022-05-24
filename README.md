# Download

In `settings.gradle` of your project, add:

Groovy:
```groovy
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url "https://gitlab.com/api/v4/groups/13544180/-/packages/maven"
            credentials(HttpHeaderCredentials) {
                name = 'Deploy-Token'
                value = gitLabDeployToken
            }
            authentication {
                header(HttpHeaderAuthentication)
            }
        }
    }
}
```

Kotlin:
```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = URI("https://gitlab.com/api/v4/groups/13544180/-/packages/maven")
            credentials(HttpHeaderCredentials::class) {
                name = "Deploy-Token"
                value = gitLabDeployToken
            }
            authentication {
                create<HttpHeaderAuthentication>("header")
            }
        }
    }
}

````
and make sure that in the (global) `gradle.properties` the `gitLabDeployToken` is defined like this:
```groovy
gitLabDeployToken=<...>
```

Then in `./app/build.gradle` add:

```groovy
dependencies {
    implementation "de.kempmobil.android:billing:5.0.0"
}
```

# Publishing
See the README file in the [Setup project](https://gitlab.com/a4265/setup).



