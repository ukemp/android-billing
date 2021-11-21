# Download

In `settings.gradle` use

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

and make sure that in the global `gradle.properties` the `gitLabDeployToken` is defined like this:
```groovy
gitLabDeployToken=<...>
```

Then in `app/gradle.build` add:

```groovy
dependencies {
    implementation "de.kempmobil.android:billing:4.0.4"
}
```

