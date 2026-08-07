package nl.vdzon.productfactory

import nl.vdzon.productfactory.common.config.EnvironmentFiles
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.modulith.Modulithic
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

@Modulithic
@SpringBootApplication
@EnableAsync
@EnableScheduling
class ProductFactoryApplication

fun main(args: Array<String>) {
    SpringApplication(ProductFactoryApplication::class.java).apply {
        setDefaultProperties(EnvironmentFiles.load(EnvironmentFiles.locate()))
        run(*args)
    }
}
