package nl.vdzon.productfactory.dashboard

import nl.vdzon.productfactory.common.config.EnvironmentFiles
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
class DashboardBackendApplication

fun main(args: Array<String>) {
    SpringApplication(DashboardBackendApplication::class.java).apply {
        setDefaultProperties(EnvironmentFiles.load(EnvironmentFiles.locate()))
        run(*args)
    }
}
