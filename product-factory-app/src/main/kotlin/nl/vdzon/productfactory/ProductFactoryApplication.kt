package nl.vdzon.productfactory

import nl.vdzon.productfactory.config.EnvironmentFiles
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import java.nio.file.Path

@SpringBootApplication
class ProductFactoryApplication

fun main(args: Array<String>) {
    val localConfiguration = EnvironmentFiles.load(Path.of("."))
    SpringApplicationBuilder(ProductFactoryApplication::class.java)
        .properties(localConfiguration)
        .run(*args)
}
