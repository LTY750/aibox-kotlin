pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "aibox-kotlin"

include(":app-android")

include(":core:model")
include(":core:common")
include(":core:provider")
include(":core:conversation")
include(":core:network")
include(":core:storage")
include(":core:security")
include(":core:backup")

include(":data:database")
include(":data:secure-storage")

include(":feature:chat")
include(":feature:sessions")
include(":feature:settings")
include(":feature:settings-general")
include(":feature:providers")
include(":feature:model-selector")
include(":feature:attachments")
include(":feature:backup")
