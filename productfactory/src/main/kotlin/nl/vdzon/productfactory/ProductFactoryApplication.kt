package nl.vdzon.productfactory

import nl.vdzon.productfactory.common.config.EnvironmentFiles
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.modulith.Modulithic
import org.springframework.scheduling.annotation.EnableAsync

@Modulithic
@SpringBootApplication
@EnableAsync
class ProductFactoryApplication

fun main(args: Array<String>) {
    SpringApplication(ProductFactoryApplication::class.java).apply {
        setDefaultProperties(EnvironmentFiles.load(EnvironmentFiles.locate()))
        run(*args)
    }
}
