pluginManagement {
    repositories {
        google { setUrl("https://maven.aliyun.com/repository/google/")}
        mavenCentral { setUrl("https://maven.aliyun.com/repository/public/")}
        maven {  setUrl("https://maven.aliyun.com/repository/gradle-plugin/")}
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        
        google { setUrl("https://maven.aliyun.com/repository/google/")}
        mavenCentral { setUrl("https://maven.aliyun.com/repository/public/")}
    }
}

rootProject.name = "Clock"
include(":app")
