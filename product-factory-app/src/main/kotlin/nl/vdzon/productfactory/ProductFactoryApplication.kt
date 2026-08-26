package nl.vdzon.productfactory

import nl.vdzon.productfactory.config.EnvironmentFiles
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.scheduling.annotation.EnableScheduling
import java.nio.file.Path

@SpringBootApplication(exclude = [UserDetailsServiceAutoConfiguration::class])
@EnableScheduling
class ProductFactoryApplication

fun main(args: Array<String>) {
    val localConfiguration = EnvironmentFiles.load(Path.of("."))
    SpringApplicationBuilder(ProductFactoryApplication::class.java)
        .properties(localConfiguration)
        .run(*args)
}
