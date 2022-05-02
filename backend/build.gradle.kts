plugins {
	kotlin("jvm")
	application
}


application {
	mainClass.set("net.malkowscy.application.ServerKt")
}
val ktorVersion = "1.6.7"
dependencies {
    implementation(Deps.Ktor.Server.core)
    implementation(Deps.Ktor.Server.hostCommon)
    implementation(Deps.Ktor.Server.statusPages)
    implementation(Deps.Ktor.Server.netty)
    implementation(Deps.Ktor.Server.contentNegotiation)
    implementation(Deps.Ktor.Server.websockets)
    implementation(Deps.Ktor.serializationJson)
    implementation(Deps.Logback.classic)
    implementation("io.lettuce:lettuce-core:6.1.8.RELEASE")

	implementation(project(":shared"))
	testImplementation(Deps.Ktor.Server.tests)
	testImplementation(Deps.JetBrains.Kotlin.testJunit)
}


tasks.named<ProcessResources>("processResources") {
	dependsOn(":web:assemble")
	from(File(rootProject.project("web").buildDir, "distributions/")) {
		into("app")
	}
}
