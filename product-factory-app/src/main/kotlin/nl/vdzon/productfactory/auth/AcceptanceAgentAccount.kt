package nl.vdzon.productfactory.auth

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("acceptance")
class AcceptanceAgentAccount(private val users: UserIdentityRepository,
    @Value("\${PF_ENVIRONMENT:local}") private val environment: String,
    @Value("\${AI_ACCESS_EMAILS:}") private val emails: String) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        require(environment == "acceptance") { "Acceptatieaccounts mogen niet in productie worden aangemaakt" }
        emails.split(',').map(String::trim).filter(String::isNotBlank).forEach {
            require(it.endsWith("@product-factory.invalid")) { "Acceptatie vereist synthetische identiteiten" }
            users.resolveOrCreate(it, factoryOwner = true)
        }
    }
}
