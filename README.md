# Contexta

A command-line AI assistant that provides contextual awareness by accessing your local filesystem and web content. 
Built using Kotlin, LangChain4j, and MCP.

## Features

- Terminal-based AI chat CLI with markdown support
- MCP
- Persistent chat memory

## Configuration

Create a config file at `~/.contexta/config.properties` with the following:

```properties
baseUrl=<openai compatible url>
apiKey=<your api key>
modelName=<model name>
```

## Building

The project uses Amper build system. To build:

```bash
./amper build
```

## Usage

Run the application:

```bash
./amper run
```

Or directly with the generated JAR:

```bash
./amper package && java -jar build/tasks/_cli_executableJarJvm/cli-jvm-executable.jar
```

### Commands

- Type your query and press Enter to send it to the AI
- Type `/exit` to quit the application

## Project Structure

- **cli**: Command-line interface implementation with terminal UI
- **integration**: Core logic for model interaction, tool integration, and configuration

## License

MIT
