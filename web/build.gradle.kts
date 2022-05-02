import org.jetbrains.compose.compose

plugins {
	id("multiplatform-setup")
	id("org.jetbrains.compose")
}

val ktorVersion = "1.6.7"

kotlin {

	sourceSets {
		val jsMain by getting {
			dependencies {
				implementation(compose.web.core)
				implementation(compose.runtime)
				implementation(project(":shared"))

                implementation("io.ktor:ktor-client-js:$ktorVersion")
                implementation("io.ktor:ktor-client-json:$ktorVersion")
                implementation("io.ktor:ktor-client-serialization:$ktorVersion")
                implementation("io.ktor:ktor-client-websockets-js:$ktorVersion")

//				implementation(Deps.Ktor.Client.core)
//				implementation(Deps.Ktor.Client.js)
//				implementation(Deps.Ktor.Client.contentNegotiation)
//				implementation(Deps.Ktor.serializationJson)
			}
		}
		val jsTest by getting {
			dependencies {
				implementation(kotlin("test-js"))
			}
		}
	}
}
