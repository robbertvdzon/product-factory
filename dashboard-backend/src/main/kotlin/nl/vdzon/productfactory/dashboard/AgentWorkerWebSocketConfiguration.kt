package nl.vdzon.productfactory.dashboard

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean

@Configuration
@EnableWebSocket
class AgentWorkerWebSocketConfiguration(private val hub: AgentWorkerHub) : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(hub, "/agent-worker")
    }

    @Bean
    fun agentWorkerWebSocketContainer() = ServletServerContainerFactoryBean().apply {
        setMaxTextMessageBufferSize(MAX_MESSAGE_BYTES)
    }

    companion object {
        /**
         * Een resultaat kan maximaal zes gegenereerde beelden van 5 MiB bevatten. Base64 vergroot die bytes met
         * ongeveer een derde; de resterende marge is voor de omringende JSON. Zonder deze expliciete transportgrens
         * sluit Tomcat een verder geldig beeldresultaat met WebSocket-code 1009.
         */
        const val MAX_MESSAGE_BYTES = 48 * 1024 * 1024
    }
}
