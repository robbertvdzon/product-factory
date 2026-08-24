package nl.vdzon.productfactory

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ProductFactoryApplication

fun main(args: Array<String>) {
    runApplication<ProductFactoryApplication>(*args)
}
