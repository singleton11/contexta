import dev.langchain4j.mcp.McpToolProvider
import dev.langchain4j.mcp.client.DefaultMcpClient
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport
import dev.langchain4j.memory.chat.TokenWindowChatMemory
import dev.langchain4j.model.openai.OpenAiChatModelName
import dev.langchain4j.model.openai.OpenAiStreamingChatModel
import dev.langchain4j.model.openai.OpenAiTokenizer
import dev.langchain4j.model.openai.internal.chat.ResponseFormatType
import dev.langchain4j.model.output.FinishReason
import dev.langchain4j.service.AiServices
import dev.langchain4j.service.TokenStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.io.File
import java.util.*

interface Assistant {
    fun chat(question: String): TokenStream
}

data class ModelConfig(
    val baseUrl: String,
    val apiKey: String,
    val modelName: String
)

sealed interface Message {
    data class Text(val text: String) : Message
    data class Tool(val name: String, val arguments: String) : Message
}

object ConfigLoader {
    private const val CONFIG_FOLDER = ".contexta"
    private const val CONFIG_FILE = "config.properties"

    fun loadModelConfig(): ModelConfig {
        val homeDir = System.getProperty("user.home")
        val configDir = File(homeDir, CONFIG_FOLDER)
        val configFile = File(configDir, CONFIG_FILE)

        if (!configFile.exists()) {
            error("Configuration file not found at ${configFile.absolutePath}")
        }

        val properties = Properties()
        configFile.inputStream().use { properties.load(it) }

        return ModelConfig(
            baseUrl = properties.getProperty("baseUrl")
                ?: error("baseUrl not found in configuration"),
            apiKey = properties.getProperty("apiKey")
                ?: error("apiKey not found in configuration"),
            modelName = properties.getProperty("modelName")
                ?: error("modelName not found in configuration")
        )
    }
}

class ModelService(private val config: ModelConfig = ConfigLoader.loadModelConfig()) {

    private val chatMemory: TokenWindowChatMemory = TokenWindowChatMemory.builder()
        .maxTokens(
            1_000_000,
            OpenAiTokenizer(OpenAiChatModelName.GPT_4_O_MINI)
        )
        .build()

    private val fileSystem = StdioMcpTransport.Builder()
        .command(
            listOf(
                "npx",
                "-y",
                "@modelcontextprotocol/server-filesystem",
                "/Users/singleton/projects",
                "/Users/singleton/Documents/Obsidian Vault"
            )
        )
        .build()

    private val fetch = StdioMcpTransport.Builder()
        .command(
            listOf(
                "/Users/singleton/.local/bin/uvx",
                "mcp-server-fetch",
            )
        ).build()

    private val fileSystemMcpClient = DefaultMcpClient.Builder()
        .transport(fileSystem)
        .build()

    private val fetchMcpClient = DefaultMcpClient.Builder()
        .transport(fetch)
        .build()

    private val toolProvider = McpToolProvider.builder()
        .mcpClients(listOf(fileSystemMcpClient, fetchMcpClient))
        .build()

    fun request(message: String): Flow<Message> {
        val model = OpenAiStreamingChatModel.builder()
            .baseUrl(config.baseUrl)
            .apiKey(config.apiKey)
            .modelName(config.modelName)
            .build()

        val assistant = AiServices.builder(Assistant::class.java)
            .streamingChatLanguageModel(model)
            .chatMemory(chatMemory)
            .toolProvider(toolProvider)
            .build()
        val tokenStream = assistant.chat(message)

        val channel = Channel<Message>()

        tokenStream
            .onPartialResponse {
                channel.trySend(Message.Text(it))
            }
            .onCompleteResponse {
                channel.close()
            }
            .onToolExecuted { toolExecutionResult ->
                channel.trySend(
                    Message.Tool(
                        toolExecutionResult.request().name(),
                        toolExecutionResult.request().arguments()
                    )
                )
            }
            .onError {
                channel.close(it)
            }
            .start()
        return channel.consumeAsFlow()
    }
}
