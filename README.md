# Android Billing

A wrapper around the Android Billing Client SDK to allow in app, one time purchases of a single
product (basically ad version of an app vs. purchased version). I use it in private projects, so its
simplest to clone it.

The version of this library reflects the version of the billing client used. 

# Usage

Note to myself: to use this library, in `settings.gradle.kts` of the project, add:

```kotlin
val githubDeployToken: String by settings

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = URI("https://maven.pkg.github.com/ukemp/android-billing")
            credentials {
                username = "ukemp"
                password = githubDeployToken
            }
        }
    }
}
````

and make sure that in the (global) `gradle.properties` the `gitLabDeployToken` is defined like this:

```groovy
githubDeployToken=<...>
```

Then in `./app/build.gradle.kts` add:

```kotlin
dependencies {
    implementation("de.kempmobil.android:billing:7.1.1")
}
```


