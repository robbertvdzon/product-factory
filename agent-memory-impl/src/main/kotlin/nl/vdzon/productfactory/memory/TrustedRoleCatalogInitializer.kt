package nl.vdzon.productfactory.memory

import org.springframework.beans.factory.InitializingBean
import org.springframework.stereotype.Component

@Component
class TrustedRoleCatalogInitializer(
    private val memory: AgentMemoryApplicationService,
) : InitializingBean {
    override fun afterPropertiesSet() = memory.registerTrustedRoles()
}
