package nl.vdzon.productfactory.operations

import com.fasterxml.jackson.databind.ObjectMapper
import nl.vdzon.productfactory.ProductFactoryApplication
import nl.vdzon.productfactory.api.quality.QualityService
import nl.vdzon.productfactory.api.quality.RepairQualityPublicationCommand
import org.springframework.boot.builder.SpringApplicationBuilder
import java.nio.file.Files
import java.nio.file.Path

/** Explicit operator entry point; never registered as an application startup task or HTTP route. */
object QualityPublicationRepairMain {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 1) { "Usage: QualityPublicationRepairMain <reviewed-command.json>" }
        val commandFile = Path.of(args.single())
        require(Files.isRegularFile(commandFile) && Files.size(commandFile) in 1..1_048_576) {
            "A non-empty command file of at most 1 MiB is required"
        }
        SpringApplicationBuilder(ProductFactoryApplication::class.java).run(
            "--PF_SCHEDULES_ENABLED=false",
            "--PF_AI_RUNTIME_SCHEDULING_ENABLED=false",
            "--PF_HISTORY_RETENTION_ENABLED=false",
            "--spring.flyway.enabled=false",
            "--spring.datasource.hikari.maximum-pool-size=2",
            "--spring.datasource.hikari.minimum-idle=0",
            "--server.address=127.0.0.1",
            "--server.port=0",
        ).use { context ->
            val command = context.getBean(ObjectMapper::class.java).readValue(
                Files.readString(commandFile), RepairQualityPublicationCommand::class.java,
            )
            val session = context.getBean(QualityService::class.java).repairQualityPublication(command)
            println("QUALITY_PUBLICATION_REPAIR_COMPLETED sessionId=${session.value}")
        }
    }
}
