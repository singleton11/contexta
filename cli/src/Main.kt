import com.github.ajalt.mordant.animation.animation
import com.github.ajalt.mordant.animation.coroutines.animateInCoroutine
import com.github.ajalt.mordant.markdown.Markdown
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.widgets.Panel
import com.github.ajalt.mordant.widgets.Text
import com.github.ajalt.mordant.widgets.progress.progressBar
import com.github.ajalt.mordant.widgets.progress.progressBarContextLayout
import com.github.ajalt.mordant.widgets.progress.text
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

suspend fun main(args: Array<String>) = coroutineScope {

    val t = Terminal()
    val model = ModelService()

    val text = """
          ,----..                           ___                             ___
         /   /   \                        ,--.'|_                         ,--.'|_
        |   :     :  ,---.        ,---,   |  | :,'                        |  | :,'
        .   |  ;. / '   ,'\   ,-+-. /  |  :  : ' :            ,--,  ,--,  :  : ' :
        .   ; /--` /   /   | ,--.'|'   |.;__,'  /     ,---.   |'. \/ .`|.;__,'  /    ,--.--.
        ;   | ;   .   ; ,. :|   |  ,"' ||  |   |     /     \  '  \/  / ;|  |   |    /       \
        |   : |   '   | |: :|   | /  | |:__,'| :    /    /  |  \  \.' / :__,'| :   .--.  .-. |
        .   | '___'   | .; :|   | |  | |  '  : |__ .    ' / |   \  ;  ;   '  : |__  \__\/: . .
        '   ; : .'|   :    ||   | |  |/   |  | '.'|'   ;   /|  / \  \  \  |  | '.'| ," .--.; |
        '   | '/  :\   \  / |   | |--'    ;  :    ;'   |  / |./__;   ;  \ ;  :    ;/  /  ,.  |
        |   :    /  `----'  |   |/        |  ,   / |   :    ||   :/\  \ ; |  ,   /;  :   .'   \
         \   \ .'           '---'          ---`-'   \   \  / `---'  `--`   ---`-' |  ,     .-./
          `---`                                      `----'                        `--`---'
    """.trimIndent()

    t.answer(text, "Welcome!", false)

    while (true) {
        t.print(TextColors.cyan(">"))
        val input = t.readLineOrNull(false)
        if (input == null || input == "/exit") {
            break
        }
        val progress = progressBarContextLayout<Unit> {
            text("API Request...")
            progressBar()
        }.animateInCoroutine(t, context = Unit, total = 1000, completed = 999)

        launch { progress.execute() }
        var panel: AiAssistantPanel? = null
        model.request(input).collect {
            if (!progress.finished) {
                progress.clear()
            }
            when (it) {
                is Message.Text -> {
                    if (panel == null || panel?.isClosed == true) {
                        panel = AiAssistantPanel(t)
                    }
                    panel.addTest(it.text)
                }

                is Message.Tool -> {
                    panel?.close()
                    t.answer(
                        """
                                    Tool name: ${it.name}
                                    Arguments:
                                    ${it.arguments}
                                """.trimIndent(), "Tool"
                    )
                    launch { progress.execute() }
                }
            }
        }
        panel?.close()
    }
}

private fun Terminal.answer(
    text: String,
    title: String = "AI Assistant",
    markdown: Boolean = true,
) {
    println(
        Panel(
            title = Text(TextColors.cyan(title)),
            content = when (markdown) {
                true -> Markdown(text)
                false -> Text(text)
            }
        )
    )
}

class AiAssistantPanel(private val terminal: Terminal) : AutoCloseable {

    var text = ""
    var isClosed = false

    private val animation = terminal.animation<String> {
        Panel(
            title = Text(TextColors.cyan("AI Assistant")),
            content = Markdown(text)
        )
    }

    fun addTest(value: String) {
        text += value
        animation.update(text)
    }

    override fun close() {
        isClosed = true
        animation.stop()
    }
}
