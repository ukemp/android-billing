# Download

In `settings.gradle` of your project, add:

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

and make sure that in the (global) `gradle.properties` the `gitLabDeployToken` is defined like this:
```groovy
gitLabDeployToken=<...>
```

Then in `./app/build.gradle` add:

```groovy
dependencies {
    implementation "de.kempmobil.android:billing:4.0.4"
}
```

# Publishing
Note that this library must be published to its Gitlab [project registry](https://gitlab.com/a4265/billing/-/packages), while it is downloaded from the [Android Library](https://gitlab.com/groups/a4265/-/packages) Gitab registry. Hence other library projects may be downloaded from the same location!

To publish a new version:

1. Increase the version number in `./app/build.gradle`
2. Reflect the change in this file
3. Upload the new version with `./gradlew publish`



